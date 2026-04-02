package com.dylanledermann.app;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import com.dylanledermann.app.FileExplorers.FileExplorer;
import com.dylanledermann.app.FileExplorers.DirectoryListing;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public abstract class AbstractFileExplorerTests {

    protected abstract FileExplorer createExplorer() throws IOException;

    protected void closeExplorer(FileExplorer explorer) throws Exception {
        if (explorer instanceof AutoCloseable closeable) {
            closeable.close();
        }
    }

    @Test
    public void testListFolderEmptyDirectory() throws Exception {
        Path tempDir = Files.createTempDirectory("explorer-test-empty");
        try {
            FileExplorer explorer = createExplorer();
            DirectoryListing listing = explorer.listFolder(tempDir);
            assertTrue(listing.entries().isEmpty(), "Expected empty directory listing");
            assertEquals(0, listing.totalSize(), "Expected total size 0 for empty directory");
            closeExplorer(explorer);
        } finally {
            Files.deleteIfExists(tempDir);
        }
    }

    @Test
    public void testReadFileAndVerifyTarget() throws Exception {
        Path tempDir = Files.createTempDirectory("explorer-test-read");
        Path file = tempDir.resolve("sample.txt");
        Files.writeString(file, "hello world");
        try {
            FileExplorer explorer = createExplorer();
            assertTrue(explorer.verifyTarget("R", "sample.txt", tempDir), "Expected text file to verify");
            String contents = explorer.readFile(file);
            assertEquals("hello world", contents, "Unexpected file content");
            closeExplorer(explorer);
        } finally {
            Files.deleteIfExists(file);
            Files.deleteIfExists(tempDir);
        }
    }

    @Test
    public void testGetFolderSize() throws Exception {
        Path tempDir = Files.createTempDirectory("explorer-test-size");
        Path fileA = tempDir.resolve("a.txt");
        Path nested = Files.createDirectory(tempDir.resolve("nested"));
        Path fileB = nested.resolve("b.txt");
        Files.writeString(fileA, "12345");
        Files.writeString(fileB, "abcd");

        try {
            FileExplorer explorer = createExplorer();
            long expected = Files.size(fileA) + Files.size(fileB);
            long actual = explorer.getFolderSize(tempDir);
            assertEquals(expected, actual, "Expected folder size to match actual file size");
            closeExplorer(explorer);
        } finally {
            Files.deleteIfExists(fileB);
            Files.deleteIfExists(nested);
            Files.deleteIfExists(fileA);
            Files.deleteIfExists(tempDir);
        }
    }

    @Test
    public void testDeleteTarget() throws Exception {
        Path tempDir = Files.createTempDirectory("explorer-test-delete");
        Path target = Files.createDirectory(tempDir.resolve("child"));
        Path nested = Files.createFile(target.resolve("child.txt"));
        try {
            FileExplorer explorer = createExplorer();
            assertTrue(Files.exists(target), "Target should exist before deletion");
            boolean deleted = explorer.deleteTarget(target);
            assertTrue(deleted, "deleteTarget should return true");
            assertFalse(Files.exists(target), "Target should be removed after deletion");
            assertTrue(Files.exists(tempDir), "Parent directory should remain");
            closeExplorer(explorer);
        } finally {
            Files.deleteIfExists(nested);
            Files.deleteIfExists(target);
            Files.deleteIfExists(tempDir);
        }
    }

    @Test
    public void testPerformanceFolderListingAndSize() throws Exception {
        Path tempDir = Files.createTempDirectory("explorer-test-perf");
        try {
            createTestStructure(tempDir, 10, 20, 512);
            FileExplorer explorer = createExplorer();

            long start = System.nanoTime();
            DirectoryListing listing = explorer.listFolder(tempDir);
            long listDuration = System.nanoTime() - start;

            start = System.nanoTime();
            long size = explorer.getFolderSize(tempDir);
            long sizeDuration = System.nanoTime() - start;

            long actualBytes = computeActualStorage(tempDir);
            assertEquals(actualBytes, size, "Folder size should equal actual bytes on disk");
            assertEquals(actualBytes, listing.totalSize(), "Listing total size should equal actual bytes on disk");
            closeExplorer(explorer);

            System.out.println("Performance: listFolder time = " + (listDuration / 1_000_000.0) + " ms");
            System.out.println("Performance: getFolderSize time = " + (sizeDuration / 1_000_000.0) + " ms");
            System.out.println("Performance: total storage = " + actualBytes + " bytes");
        } finally {
            deleteRecursively(tempDir);
        }
    }

    @Test
    public void testPerformanceFolderListingAndSizeRepeated() throws Exception {
        Path tempDir = Files.createTempDirectory("explorer-test-perf");
        try {
            createTestStructure(tempDir, 10, 20, 512);
            FileExplorer explorer = createExplorer();
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
            closeExplorer(explorer);
            System.out.println("Performance: listFolder 5 times = " + (totListDuration / 1_000_000.0) + " ms");
            System.out.println("Performance: getFolderSize 5 times = " + (totSizeDuration / 1_000_000.0) + " ms");
        } finally {
            deleteRecursively(tempDir);
        }
    }

    @Test
    public void testPerformanceRepeatedRead() throws Exception {
        Path tempDir = Files.createTempDirectory("explorer-test-perf-read");
        Path file = tempDir.resolve("large.txt");
        String content = "0123456789".repeat(1000);
        Files.writeString(file, content);

        try {
            FileExplorer explorer = createExplorer();
            int iterations = 20;
            long totalBytes = 0;
            long start = System.nanoTime();
            for (int i = 0; i < iterations; i++) {
                String read = explorer.readFile(file);
                assertEquals(content.length(), read.length(), "Unexpected read size");
                totalBytes += read.length();
            }
            long duration = System.nanoTime() - start;
            closeExplorer(explorer);
            System.out.println(
                    "Performance: repeated read " + iterations + " times = " + (duration / 1_000_000.0) + " ms");
            System.out.println("Performance: bytes read total = " + totalBytes + " bytes");
        } finally {
            Files.deleteIfExists(file);
            Files.deleteIfExists(tempDir);
        }
    }

    @Test
    public void testLargeDirectoryCacheStress() throws Exception {
        Path tempDir = Files.createTempDirectory("explorer-stress");
        try {
            createTestStructure(tempDir, 40, 25, 256);
            FileExplorer explorer = createExplorer();

            long start = System.nanoTime();
            for (int i = 0; i < 40; i++) {
                DirectoryListing listing = explorer.listFolder(tempDir.resolve("dir" + i));
                assertEquals(25, listing.entries().size(), "Expected 25 entries");
            }
            long firstPass = System.nanoTime() - start;

            start = System.nanoTime();
            for (int i = 0; i < 40; i++) {
                DirectoryListing listing = explorer.listFolder(tempDir.resolve("dir" + i));
                assertEquals(25, listing.entries().size(), "Expected 25 entries");
            }
            long secondPass = System.nanoTime() - start;
            closeExplorer(explorer);

            System.out.println("Stress: first pass directory listings = " + (firstPass / 1_000_000.0) + " ms");
            System.out.println("Stress: second pass directory listings = " + (secondPass / 1_000_000.0) + " ms");
        } finally {
            deleteRecursively(tempDir);
        }
    }

    @Test
    public void testDeepNestedFolderSizePerformance() throws Exception {
        Path tempDir = Files.createTempDirectory("explorer-test-deep");
        try {
            Path current = tempDir;
            int depth = 40;
            for (int i = 0; i < depth; i++) {
                current = Files.createDirectory(current.resolve("level" + i));
                Files.writeString(current.resolve("file" + i + ".txt"), "nested-data".repeat(128));
            }

            FileExplorer explorer = createExplorer();
            long start = System.nanoTime();
            long size = explorer.getFolderSize(tempDir);
            long duration = System.nanoTime() - start;
            long expected = computeActualStorage(tempDir);
            assertEquals(expected, size, "Deep folder size mismatch");
            closeExplorer(explorer);
            System.out.println(
                    "Stress: deep nested getFolderSize time = " + (duration / 1_000_000.0) + " ms for depth " + depth);
        } finally {
            deleteRecursively(tempDir);
        }
    }

    @Test
    public void testRepeatedCacheHitsAndSpeed() throws Exception {
        Path tempDir = Files.createTempDirectory("caffeine-cache-hits");
        try {
            createTestStructure(tempDir, 20, 50, 256);
            FileExplorer explorer = createExplorer();

            int subdirs = 20;
            long firstStart = System.nanoTime();
            for (int i = 0; i < subdirs; i++) {
                DirectoryListing listing = explorer.listFolder(tempDir.resolve("dir" + i));
                assertEquals(50, listing.entries().size(), "Expected 50 entries");
            }
            long firstDuration = System.nanoTime() - firstStart;

            long secondStart = System.nanoTime();
            for (int i = 0; i < subdirs; i++) {
                DirectoryListing listing = explorer.listFolder(tempDir.resolve("dir" + i));
                assertEquals(50, listing.entries().size(), "Expected 50 entries");
            }
            long secondDuration = System.nanoTime() - secondStart;

            Path sampleFile = tempDir.resolve("dir0").resolve("file0.txt");
            long readFirstStart = System.nanoTime();
            for (int i = 0; i < 200; i++) {
                explorer.readFile(sampleFile);
            }
            long readFirstDuration = System.nanoTime() - readFirstStart;

            long readSecondStart = System.nanoTime();
            for (int i = 0; i < 200; i++) {
                explorer.readFile(sampleFile);
            }
            long readSecondDuration = System.nanoTime() - readSecondStart;

            closeExplorer(explorer);

            System.out.println("Performance: listing first pass = " + (firstDuration / 1_000_000.0) + " ms");
            System.out.println("Performance: listing second pass = " + (secondDuration / 1_000_000.0) + " ms");
            System.out.println("Performance: file read first series = " + (readFirstDuration / 1_000_000.0) + " ms");
            System.out.println("Performance: file read second series = " + (readSecondDuration / 1_000_000.0) + " ms");
        } finally {
            deleteRecursively(tempDir);
        }
    }

    @Test
    public void testThreadedConcurrentFileReads() throws Exception {
        Path tempDir = Files.createTempDirectory("explorer-threaded-perf");
        try {
            createTestStructure(tempDir, 10, 20, 1024);
            List<Path> filePaths = Files.walk(tempDir)
                    .filter(Files::isRegularFile)
                    .toList();
            assertEquals(200, filePaths.size(), "Expected 200 files in the test structure");

            FileExplorer explorer = createExplorer();
            int threadCount = 8;
            int readsPerThread = 250;
            ExecutorService executor = Executors.newFixedThreadPool(threadCount);
            CountDownLatch startLatch = new CountDownLatch(1);
            CountDownLatch doneLatch = new CountDownLatch(threadCount);
            AtomicReference<Exception> executionException = new AtomicReference<>();

            for (int threadIndex = 0; threadIndex < threadCount; threadIndex++) {
                int index = threadIndex;
                executor.submit(() -> {
                    try {
                        startLatch.await();
                        for (int i = 0; i < readsPerThread; i++) {
                            Path file = filePaths.get((index * readsPerThread + i) % filePaths.size());
                            String contents = explorer.readFile(file);
                            assertEquals(1024, contents.length(), "Unexpected concurrent read size");
                        }
                    } catch (Exception e) {
                        executionException.compareAndSet(null, e);
                    } finally {
                        doneLatch.countDown();
                    }
                });
            }

            startLatch.countDown();
            long start = System.nanoTime();
            assertTrue(doneLatch.await(30, TimeUnit.SECONDS), "Concurrent reads did not finish in time");
            long duration = System.nanoTime() - start;
            executor.shutdownNow();

            if (executionException.get() != null) {
                throw executionException.get();
            }

            closeExplorer(explorer);
            System.out.println("Performance: threaded concurrent reads with " + threadCount + " threads = "
                    + (duration / 1_000_000.0) + " ms");
        } finally {
            deleteRecursively(tempDir);
        }
    }

    protected void createTestStructure(Path root, int subdirs, int filesPerDir, int bytesPerFile) throws IOException {
        for (int i = 0; i < subdirs; i++) {
            Path dir = Files.createDirectory(root.resolve("dir" + i));
            for (int j = 0; j < filesPerDir; j++) {
                byte[] data = new byte[bytesPerFile];
                Files.write(dir.resolve("file" + j + ".txt"), data);
            }
        }
    }

    protected long computeActualStorage(Path folder) throws IOException {
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

    protected void deleteRecursively(Path folder) throws IOException {
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
