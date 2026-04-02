package com.dylanledermann.app.FileExplorers;

import com.dylanledermann.app.Cache.Cache;
import com.dylanledermann.app.Cache.CacheLoader.CacheLoadException;
import com.dylanledermann.app.Cache.CacheStats;
import com.dylanledermann.app.Cache.GenericCache;

import java.io.Closeable;
import java.io.IOException;
import java.nio.file.Path;
import java.time.Duration;

public class CachedFileExplorer implements FileExplorer, Closeable {
    private final FileExplorer delegate;
    private final Cache<String, DirectoryListing> folderListingCache;
    private final Cache<String, Long> folderSizeCache;
    private final Cache<String, String> fileCache;

    public CachedFileExplorer(FileExplorer delegate) {
        this.delegate = delegate;
        this.folderListingCache = GenericCache.<String, DirectoryListing>builder()
                .maxSize(256)
                .ttl(Duration.ofMinutes(10))
                .sweepInterval(Duration.ofSeconds(30))
                .loader(this::loadDirectoryListing)
                .build();

        this.folderSizeCache = GenericCache.<String, Long>builder()
                .maxSize(256)
                .ttl(Duration.ofMinutes(10))
                .sweepInterval(Duration.ofSeconds(30))
                .loader(this::loadFolderSize)
                .build();

        this.fileCache = GenericCache.<String, String>builder()
                .maxSize(512)
                .ttl(Duration.ofMinutes(10))
                .sweepInterval(Duration.ofSeconds(30))
                .loader(this::loadFileContents)
                .build();
    }

    @Override
    public DirectoryListing listFolder(Path directory) throws IOException {
        String key = keyFor(directory);
        try {
            return folderListingCache.get(key)
                    .orElseThrow(() -> new IOException("Failed to load directory listing for " + directory));
        } catch (CacheLoadException e) {
            throw new IOException("Cache loading failed for directory: " + directory, e);
        }
    }

    @Override
    public String readFile(Path file) throws IOException {
        String key = keyFor(file);
        try {
            return fileCache.get(key)
                    .orElseThrow(() -> new IOException("Failed to load file contents for " + file));
        } catch (CacheLoadException e) {
            throw new IOException("Cache loading failed for file: " + file, e);
        }
    }

    @Override
    public boolean deleteTarget(Path target) throws IOException {
        boolean deleted = delegate.deleteTarget(target);
        if (deleted) {
            invalidateCaches(target);
        }
        return deleted;
    }

    @Override
    public long getFolderSize(Path folder) throws IOException {
        String key = keyFor(folder);
        try {
            return folderSizeCache.get(key)
                    .orElseThrow(() -> new IOException("Failed to load folder size for " + folder));
        } catch (CacheLoadException e) {
            throw new IOException("Cache loading failed for folder size: " + folder, e);
        }
    }

    @Override
    public boolean isTextFile(Path file) throws IOException {
        return delegate.isTextFile(file);
    }

    @Override
    public boolean verifyTarget(String option, String target, Path current) throws IOException {
        return delegate.verifyTarget(option, target, current);
    }

    @Override
    public void close() throws IOException {
        folderListingCache.close();
        folderSizeCache.close();
        fileCache.close();
    }

    public CacheStats listingCacheStats() {
        return folderListingCache.stats();
    }

    public CacheStats fileCacheStats() {
        return fileCache.stats();
    }

    public CacheStats folderSizeCacheStats() {
        return folderSizeCache.stats();
    }

    public CacheStats totalCacheStats() {
        CacheStats listing = folderListingCache.stats();
        CacheStats sizes = folderSizeCache.stats();
        CacheStats files = fileCache.stats();
        return new CacheStats(
                listing.hits() + sizes.hits() + files.hits(),
                listing.misses() + sizes.misses() + files.misses(),
                listing.evictions() + sizes.evictions() + files.evictions());
    }

    private DirectoryListing loadDirectoryListing(String key) throws Exception {
        return delegate.listFolder(Path.of(key));
    }

    private Long loadFolderSize(String key) throws Exception {
        return delegate.getFolderSize(Path.of(key));
    }

    private String loadFileContents(String key) throws Exception {
        return delegate.readFile(Path.of(key));
    }

    private String keyFor(Path path) {
        return path.toAbsolutePath().normalize().toString();
    }

    private void invalidateCaches(Path target) {
        String targetKey = keyFor(target);
        fileCache.invalidate(targetKey);
        folderSizeCache.invalidate(targetKey);
        folderListingCache.invalidate(targetKey);
        Path parent = target.getParent();
        if (parent != null) {
            folderListingCache.invalidate(keyFor(parent));
        }
    }
}
