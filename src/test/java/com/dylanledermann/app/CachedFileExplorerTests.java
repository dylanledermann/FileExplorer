package com.dylanledermann.app;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import com.dylanledermann.app.FileExplorers.CachedFileExplorer;
import com.dylanledermann.app.FileExplorers.DirectoryListing;
import com.dylanledermann.app.FileExplorers.FileExplorer;
import com.dylanledermann.app.FileExplorers.FileExplorerNoCache;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class CachedFileExplorerTests extends AbstractFileExplorerTests {
    @BeforeAll
    public static void initiateTests() {
        System.out.println("============ Generic Cache Tests ============");
    }

    @Override
    protected FileExplorer createExplorer() throws IOException {
        return new CachedFileExplorer(new FileExplorerNoCache());
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
    public void testRepeatedHitsShowGenericCacheImprovement() throws IOException {
        Path tempDir = Files.createTempDirectory("generic-cache-repeated");
        try {
            createTestStructure(tempDir, 1, 100, 1024);
            CachedFileExplorer explorer = new CachedFileExplorer(new FileExplorerNoCache());
            Path file = tempDir.resolve("dir0").resolve("file0.txt");

            // Warm the cache
            explorer.readFile(file);

            int iterations = 500;
            long start = System.nanoTime();
            for (int i = 0; i < iterations; i++) {
                String contents = explorer.readFile(file);
                assertEquals(1024, contents.length(), "Unexpected cached file length");
            }
            long duration = System.nanoTime() - start;

            assertTrue(explorer.fileCacheStats().hits() >= iterations,
                    "Expected repeated file reads to generate cache hits");
            explorer.close();
            System.out.println("Generic cache repeated hot reads = " + (duration / 1_000_000.0) + " ms");
        } finally {
            deleteRecursively(tempDir);
        }
    }

    @Test
    public void testEvictionHeavyCacheWorkload() throws IOException {
        Path tempDir = Files.createTempDirectory("generic-eviction-heavy");
        try {
            int totalFiles = 1500;
            for (int i = 0; i < totalFiles; i++) {
                Path file = tempDir.resolve("file" + i + ".txt");
                Files.writeString(file, "x".repeat(2048));
            }

            try (CachedFileExplorer explorer = new CachedFileExplorer(new FileExplorerNoCache())) {
                for (int i = 0; i < totalFiles; i++) {
                    explorer.readFile(tempDir.resolve("file" + i + ".txt"));
                }

                long start = System.nanoTime();
                for (int i = totalFiles - 500; i < totalFiles; i++) {
                    String contents = explorer.readFile(tempDir.resolve("file" + i + ".txt"));
                    assertEquals(2048, contents.length(), "Unexpected cached file length");
                }
                long duration = System.nanoTime() - start;

                System.out.println("Generic cache eviction-heavy hot reads = " + (duration / 1_000_000.0) + " ms");
            }
        } finally {
            deleteRecursively(tempDir);
        }
    }
}
