package com.dylanledermann.app;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import com.dylanledermann.app.FileExplorers.DirectoryListing;
import com.dylanledermann.app.FileExplorers.FileExplorer;
import com.dylanledermann.app.FileExplorers.FileExplorerNoCache;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class FileExplorerNoCacheTests {
    @BeforeAll
    public static void initiateTests() {
        System.out.println("============ No Cache Tests ============");
    }

    @Test
    public void testListFolderEmptyDirectory() throws IOException {
        Path tempDir = Files.createTempDirectory("explorer-test-empty");
        try {
            FileExplorer explorer = new FileExplorerNoCache();
            DirectoryListing listing = explorer.listFolder(tempDir);
            assertTrue(listing.entries().isEmpty(), "Expected empty directory listing");
            assertEquals(0, listing.totalSize(), "Expected total size 0 for empty directory");
        } finally {
            Files.deleteIfExists(tempDir);
        }
    }

    @Test
    public void testReadFileAndVerifyTarget() throws IOException {
        Path tempDir = Files.createTempDirectory("explorer-test-read");
        Path file = tempDir.resolve("sample.txt");
        Files.writeString(file, "hello world");
        try {
            FileExplorer explorer = new FileExplorerNoCache();
            assertTrue(explorer.verifyTarget("R", "sample.txt", tempDir), "Expected text file to verify");
            String contents = explorer.readFile(file);
            assertEquals("hello world", contents, "Unexpected file content");
        } finally {
            Files.deleteIfExists(file);
            Files.deleteIfExists(tempDir);
        }
    }

    @Test
    public void testGetFolderSize() throws IOException {
        Path tempDir = Files.createTempDirectory("explorer-test-size");
        Path fileA = tempDir.resolve("a.txt");
        Path nested = Files.createDirectory(tempDir.resolve("nested"));
        Path fileB = nested.resolve("b.txt");
        Files.writeString(fileA, "12345");
        Files.writeString(fileB, "abcd");

        try {
            FileExplorer explorer = new FileExplorerNoCache();
            long expected = Files.size(fileA) + Files.size(fileB);
            long actual = explorer.getFolderSize(tempDir);
            assertEquals(expected, actual, "Expected folder size to match actual file size");
        } finally {
            Files.deleteIfExists(fileB);
            Files.deleteIfExists(nested);
            Files.deleteIfExists(fileA);
            Files.deleteIfExists(tempDir);
        }
    }

    @Test
    public void testDeleteTarget() throws IOException {
        Path tempDir = Files.createTempDirectory("explorer-test-delete");
        Path target = Files.createDirectory(tempDir.resolve("child"));
        Path nested = Files.createFile(target.resolve("child.txt"));
        FileExplorer explorer = new FileExplorerNoCache();

        try {
            assertTrue(Files.exists(target), "Target should exist before deletion");
            boolean deleted = explorer.deleteTarget(target);
            assertTrue(deleted, "deleteTarget should return true");
            assertFalse(Files.exists(target), "Target should be removed after deletion");
            assertTrue(Files.exists(tempDir), "Parent directory should remain");
        } finally {
            Files.deleteIfExists(nested);
            Files.deleteIfExists(target);
            Files.deleteIfExists(tempDir);
        }
    }

    @Test
    public void testPerformanceFolderListingAndSize() throws IOException {
        Path tempDir = Files.createTempDirectory("explorer-test-perf");
        try {
            createTestStructure(tempDir, 10, 20, 512);
            FileExplorer explorer = new FileExplorerNoCache();

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
        try {
            createTestStructure(tempDir, 10, 20, 512);
            FileExplorer explorer = new FileExplorerNoCache();
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

        try {
            FileExplorer explorer = new FileExplorerNoCache();
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
        Path tempDir = Files.createTempDirectory("explorer-stress");
        try {
            FileExplorerNoCache explorer = new FileExplorerNoCache();
            int subdirs = 40;
            int filesPerDir = 25;
            createTestStructure(tempDir, subdirs, filesPerDir, 256);

            long start = System.nanoTime();
            for (int i = 0; i < subdirs; i++) {
                DirectoryListing listing = explorer.listFolder(tempDir.resolve("dir" + i));
                assertEquals(filesPerDir, listing.entries().size(), "Expected " + filesPerDir + " entries");
            }
            long firstPass = System.nanoTime() - start;

            start = System.nanoTime();
            for (int i = 0; i < subdirs; i++) {
                DirectoryListing listing = explorer.listFolder(tempDir.resolve("dir" + i));
                assertEquals(filesPerDir, listing.entries().size(), "Expected " + filesPerDir + " entries");
            }
            long secondPass = System.nanoTime() - start;

            Path sampleFile = tempDir.resolve("dir0").resolve("file0.txt");
            explorer.readFile(sampleFile);
            explorer.readFile(sampleFile);

            System.out.println("Stress: first pass directory listings = " + (firstPass / 1_000_000.0) + " ms");
            System.out.println("Stress: second pass directory listings = " + (secondPass / 1_000_000.0) + " ms");
        } finally {
            deleteRecursively(tempDir);
        }
    }

    @Test
    public void testDeepNestedFolderSizePerformance() throws IOException {
        Path tempDir = Files.createTempDirectory("explorer-test-deep");
        try {
            Path current = tempDir;
            int depth = 40;
            for (int i = 0; i < depth; i++) {
                current = Files.createDirectory(current.resolve("level" + i));
                Files.writeString(current.resolve("file" + i + ".txt"), "nested-data".repeat(128));
            }

            FileExplorer explorer = new FileExplorerNoCache();
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
