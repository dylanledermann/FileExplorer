package com.dylanledermann.app;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import com.dylanledermann.app.FileExplorers.CachedFileExplorer;
import com.dylanledermann.app.FileExplorers.DirectoryListing;
import com.dylanledermann.app.FileExplorers.FileExplorerNoCache;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class CachedFileExplorerTests {
    @BeforeAll
    public static void initiateTests() {
        System.out.println("============ Generic Cache Tests ============");
    }

    @Test
    public void testReadFileCacheHits() throws IOException {
        Path tempDir = Files.createTempDirectory("cached-explorer-read");
        Path file = tempDir.resolve("read.txt");
        Files.writeString(file, "cached content");

        try (CachedFileExplorer explorer = new CachedFileExplorer(new FileExplorerNoCache())) {
            String first = explorer.readFile(file);
            String second = explorer.readFile(file);
            assertEquals(first, second, "Cached read file results should equal");
            assertTrue(explorer.fileCacheStats().hits() >= 1,
                    "Expected at least one cache hit for repeated read");
        } finally {
            Files.deleteIfExists(file);
            Files.deleteIfExists(tempDir);
        }
    }

    @Test
    public void testDirectoryListingCacheHits() throws IOException {
        Path tempDir = Files.createTempDirectory("cached-explorer-list");
        Files.createFile(tempDir.resolve("a.txt"));

        try (CachedFileExplorer explorer = new CachedFileExplorer(new FileExplorerNoCache())) {
            DirectoryListing first = explorer.listFolder(tempDir);
            DirectoryListing second = explorer.listFolder(tempDir);
            assertEquals(first.entries().size(), second.entries().size(), "Expected same listing size");
            assertTrue(explorer.listingCacheStats().hits() >= 1,
                    "Expected at least one cache hit for repeated listing");
        } finally {
            Files.deleteIfExists(tempDir.resolve("a.txt"));
            Files.deleteIfExists(tempDir);
        }
    }

    @Test
    public void testCacheInvalidationAfterDelete() throws IOException {
        Path tempDir = Files.createTempDirectory("cached-explorer-delete");
        Path file = tempDir.resolve("b.txt");
        Files.writeString(file, "delete me");

        try (CachedFileExplorer explorer = new CachedFileExplorer(new FileExplorerNoCache())) {
            explorer.readFile(file);
            assertTrue(explorer.fileCacheStats().misses() >= 1, "Expected initial read miss");
            explorer.deleteTarget(file);
            assertFalse(Files.exists(file), "File should be deleted");
            explorer.listFolder(tempDir);
            assertTrue(explorer.listingCacheStats().misses() >= 1,
                    "After delete, listing should refresh from cache or miss");
        } finally {
            Files.deleteIfExists(file);
            Files.deleteIfExists(tempDir);
        }
    }

    @Test
    public void testPerformanceFolderListingAndSize() throws IOException {
        Path tempDir = Files.createTempDirectory("explorer-test-perf");
        try (CachedFileExplorer explorer = new CachedFileExplorer(new FileExplorerNoCache())) {
            createTestStructure(tempDir, 10, 20, 512);

            long start = System.nanoTime();
            DirectoryListing listing = explorer.listFolder(tempDir);
            long listDuration = System.nanoTime() - start;

            start = System.nanoTime();
            long size = explorer.getFolderSize(tempDir);
            long sizeDuration = System.nanoTime() - start;

            long actualBytes = computeActualStorage(tempDir);
            assertEquals(actualBytes, size, "Folder size should equal actual bytes on disk");
            assertEquals(actualBytes, listing.totalSize(), "Listing total size should equal actual bytes on disk");

            System.out.println("Performance: listFolder time = " + (listDuration / 1_000_000.0) + " ms");
            System.out.println("Performance: getFolderSize time = " + (sizeDuration / 1_000_000.0) + " ms");
            System.out.println("Performance: total storage = " + actualBytes + " bytes");
        } finally {
            deleteRecursively(tempDir);
        }
    }

    @Test
    public void testPerformanceFolderListingAndSizeRepeated() throws IOException {
        Path tempDir = Files.createTempDirectory("explorer-test-perf");
        try (CachedFileExplorer explorer = new CachedFileExplorer(new FileExplorerNoCache())) {
            createTestStructure(tempDir, 10, 20, 512);
            long totListDuration = 0;
            long totSizeDuration = 0;
            for (int i = 0; i < 5; i++) {
                long start = System.nanoTime();
                DirectoryListing listing = explorer.listFolder(tempDir);
                long listDuration = System.nanoTime() - start;
                totListDuration += listDuration;

                start = System.nanoTime();
                long size = explorer.getFolderSize(tempDir);
                long sizeDuration = System.nanoTime() - start;
                totSizeDuration += sizeDuration;

                long actualBytes = computeActualStorage(tempDir);
                assertEquals(actualBytes, size, "Folder size should equal actual bytes on disk");
                assertEquals(actualBytes, listing.totalSize(), "Listing total size should equal actual bytes on disk");
            }
            System.out.println("Performance: listFolder 5 times = " + (totListDuration / 1_000_000.0) + " ms");
            System.out.println("Performance: getFolderSize 5 times = " + (totSizeDuration / 1_000_000.0) + " ms");
        } finally {
            deleteRecursively(tempDir);
        }
    }

    @Test
    public void testPerformanceRepeatedRead() throws IOException {
        Path tempDir = Files.createTempDirectory("explorer-test-perf-read");
        Path file = tempDir.resolve("large.txt");
        String content = "0123456789".repeat(1000);
        Files.writeString(file, content);

        try (CachedFileExplorer explorer = new CachedFileExplorer(new FileExplorerNoCache())) {
            int iterations = 20;
            long totalBytes = 0;
            long start = System.nanoTime();
            for (int i = 0; i < iterations; i++) {
                String read = explorer.readFile(file);
                assertEquals(content.length(), read.length(), "Unexpected read size");
                totalBytes += read.length();
            }
            long duration = System.nanoTime() - start;
            System.out.println(
                    "Performance: repeated read " + iterations + " times = " + (duration / 1_000_000.0) + " ms");
            System.out.println("Performance: bytes read total = " + totalBytes + " bytes");
        } finally {
            Files.deleteIfExists(file);
            Files.deleteIfExists(tempDir);
        }
    }

    @Test
    public void testLargeDirectoryCacheStress() throws IOException {
        Path tempDir = Files.createTempDirectory("cached-explorer-stress");
        try (CachedFileExplorer explorer = new CachedFileExplorer(new FileExplorerNoCache())) {
            int subdirs = 40;
            int filesPerDir = 25;
            createTestStructure(tempDir, subdirs, filesPerDir, 256);

            long start = System.nanoTime();
            for (int i = 0; i < subdirs; i++) {
                DirectoryListing listing = explorer.listFolder(tempDir.resolve("dir" + i));
                assertEquals(filesPerDir, listing.entries().size(), "Expected " + filesPerDir + " entries");
            }
            long firstPass = System.nanoTime() - start;

            long cacheHitsBefore = explorer.listingCacheStats().hits();
            start = System.nanoTime();
            for (int i = 0; i < subdirs; i++) {
                DirectoryListing listing = explorer.listFolder(tempDir.resolve("dir" + i));
                assertEquals(filesPerDir, listing.entries().size(), "Expected " + filesPerDir + " entries");
            }
            long secondPass = System.nanoTime() - start;

            assertTrue(explorer.listingCacheStats().hits() >= cacheHitsBefore + subdirs,
                    "Expected repeated listings to hit cache");

            Path sampleFile = tempDir.resolve("dir0").resolve("file0.txt");
            explorer.readFile(sampleFile);
            long fileHitsBefore = explorer.fileCacheStats().hits();
            explorer.readFile(sampleFile);
            assertTrue(explorer.fileCacheStats().hits() >= fileHitsBefore + 1,
                    "Expected repeated read to hit cache");

            System.out.println("Stress: first pass directory listings = " + (firstPass / 1_000_000.0) + " ms");
            System.out.println("Stress: second pass directory listings = " + (secondPass / 1_000_000.0) + " ms");
        } finally {
            deleteRecursively(tempDir);
        }
    }

    @Test
    public void testDeepNestedFolderSizePerformance() throws IOException {
        Path tempDir = Files.createTempDirectory("explorer-test-deep");
        try (CachedFileExplorer explorer = new CachedFileExplorer(new FileExplorerNoCache())) {
            Path current = tempDir;
            int depth = 40;
            for (int i = 0; i < depth; i++) {
                current = Files.createDirectory(current.resolve("level" + i));
                Files.writeString(current.resolve("file" + i + ".txt"), "nested-data".repeat(128));
            }

            long start = System.nanoTime();
            long size = explorer.getFolderSize(tempDir);
            long duration = System.nanoTime() - start;
            long expected = computeActualStorage(tempDir);
            assertEquals(expected, size, "Deep folder size mismatch");
            System.out.println(
                    "Stress: deep nested getFolderSize time = " + (duration / 1_000_000.0) + " ms for depth " + depth);
        } finally {
            deleteRecursively(tempDir);
        }
    }

    private void createTestStructure(Path root, int subdirs, int filesPerDir, int bytesPerFile) throws IOException {
        for (int i = 0; i < subdirs; i++) {
            Path dir = Files.createDirectory(root.resolve("dir" + i));
            for (int j = 0; j < filesPerDir; j++) {
                byte[] data = new byte[bytesPerFile];
                Files.write(dir.resolve("file" + j + ".txt"), data);
            }
        }
    }

    private long computeActualStorage(Path folder) throws IOException {
        try (var stream = Files.walk(folder)) {
            return stream.filter(Files::isRegularFile)
                    .mapToLong(path -> {
                        try {
                            return Files.size(path);
                        } catch (IOException e) {
                            throw new RuntimeException(e);
                        }
                    })
                    .sum();
        }
    }

    private void deleteRecursively(Path folder) throws IOException {
        if (Files.notExists(folder)) {
            return;
        }
        try (var stream = Files.walk(folder)) {
            stream.sorted((a, b) -> b.compareTo(a))
                    .forEach(path -> {
                        try {
                            Files.deleteIfExists(path);
                        } catch (IOException ignored) {
                        }
                    });
        }
    }
}
