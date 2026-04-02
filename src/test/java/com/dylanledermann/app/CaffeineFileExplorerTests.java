package com.dylanledermann.app;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import com.dylanledermann.app.FileExplorers.CaffeineFileExplorer;
import com.dylanledermann.app.FileExplorers.FileExplorer;
import com.dylanledermann.app.FileExplorers.FileExplorerNoCache;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class CaffeineFileExplorerTests extends AbstractFileExplorerTests {

    @BeforeAll
    public static void initiateTests() {
        System.out.println("============ Caffeine Cache Tests ============");
    }

    @Override
    protected FileExplorer createExplorer() throws IOException {
        return new CaffeineFileExplorer(new FileExplorerNoCache());
    }

    @Test
    public void testRepeatedHitsShowCaffeineImprovement() throws IOException {
        Path tempDir = Files.createTempDirectory("caffeine-cache-repeated");
        try {
            createTestStructure(tempDir, 1, 100, 1024);
            CaffeineFileExplorer explorer = new CaffeineFileExplorer(new FileExplorerNoCache());
            Path file = tempDir.resolve("dir0").resolve("file0.txt");

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
            System.out.println("Caffeine repeated hot reads = " + (duration / 1_000_000.0) + " ms");
        } finally {
            deleteRecursively(tempDir);
        }
    }

    @Test
    public void testEvictionHeavyCacheWorkload() throws IOException {
        Path tempDir = Files.createTempDirectory("caffeine-eviction-heavy");
        try {
            int totalFiles = 1500;
            for (int i = 0; i < totalFiles; i++) {
                Path file = tempDir.resolve("file" + i + ".txt");
                Files.writeString(file, "x".repeat(2048));
            }

            CaffeineFileExplorer explorer = new CaffeineFileExplorer(new FileExplorerNoCache());
            for (int i = 0; i < totalFiles; i++) {
                explorer.readFile(tempDir.resolve("file" + i + ".txt"));
            }

            long start = System.nanoTime();
            for (int i = totalFiles - 500; i < totalFiles; i++) {
                String contents = explorer.readFile(tempDir.resolve("file" + i + ".txt"));
                assertEquals(2048, contents.length(), "Unexpected cached file length");
            }
            long duration = System.nanoTime() - start;

            System.out.println("Caffeine eviction-heavy hot reads = " + (duration / 1_000_000.0) + " ms");
        } finally {
            deleteRecursively(tempDir);
        }
    }
}
