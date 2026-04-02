package com.dylanledermann.app.FileExplorers;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;

import com.dylanledermann.app.Cache.CacheStats;

public class CaffeineFileExplorer implements FileExplorer {
    private final FileExplorer delegate;
    private final Cache<String, DirectoryListing> folderListingCache;
    private final Cache<String, Long> folderSizeCache;
    private final Cache<String, String> fileCache;

    public CaffeineFileExplorer(FileExplorer delegate) {
        this.delegate = delegate;
        this.folderListingCache = Caffeine.newBuilder()
                .maximumSize(256)
                .expireAfterAccess(10, TimeUnit.MINUTES)
                .recordStats()
                .build();
        this.folderSizeCache = Caffeine.newBuilder()
                .maximumSize(256)
                .expireAfterAccess(10, TimeUnit.MINUTES)
                .recordStats()
                .build();
        this.fileCache = Caffeine.newBuilder()
                .maximumSize(512)
                .expireAfterAccess(10, TimeUnit.MINUTES)
                .recordStats()
                .build();
    }

    @Override
    public DirectoryListing listFolder(Path directory) throws IOException {
        String key = keyFor(directory);
        try {
            return this.folderListingCache.get(key, k -> {
                try {
                    return loadDirectoryListing(k);
                } catch (Exception e) {
                    throw new RuntimeException("Failed to load directory listing for " + directory);
                }
            });
        } catch (Exception e) {
            throw new IOException("Error listing folder: ", e);
        }
    }

    @Override
    public String readFile(Path file) throws IOException {
        String key = keyFor(file);
        try {
            return this.fileCache.get(key, k -> {
                try {
                    return loadFileContents(k);
                } catch (Exception e) {
                    throw new RuntimeException("Failed to load file contents for " + file);
                }
            });
        } catch (Exception e) {
            throw new IOException("Error reading file: ", e);
        }
    }

    @Override
    public boolean deleteTarget(Path target) throws IOException {
        boolean targetIsDirectory = Files.isDirectory(target);
        boolean deleted = delegate.deleteTarget(target);
        if (deleted) {
            invalidateCaches(target, targetIsDirectory);
        }
        return deleted;
    }

    @Override
    public long getFolderSize(Path folder) throws IOException {
        String key = keyFor(folder);
        try {
            return this.folderSizeCache.get(key, k -> {
                try {
                    return loadFolderSize(k);
                } catch (Exception e) {
                    throw new RuntimeException("Failed to load folder size for " + folder);
                }
            });
        } catch (Exception e) {
            throw new IOException("Error getting folder size: ", e);
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

    public CacheStats listingCacheStats() {
        com.github.benmanes.caffeine.cache.stats.CacheStats stats = folderListingCache.stats();
        return new CacheStats(
                stats.hitCount(),
                stats.missCount(),
                stats.evictionCount());
    }

    public CacheStats fileCacheStats() {
        com.github.benmanes.caffeine.cache.stats.CacheStats stats = fileCache.stats();
        return new CacheStats(
                stats.hitCount(),
                stats.missCount(),
                stats.evictionCount());
    }

    public CacheStats folderSizeCacheStats() {
        com.github.benmanes.caffeine.cache.stats.CacheStats stats = folderSizeCache.stats();
        return new CacheStats(
                stats.hitCount(),
                stats.missCount(),
                stats.evictionCount());
    }

    public com.dylanledermann.app.Cache.CacheStats totalCacheStats() {
        CacheStats listing = listingCacheStats();
        CacheStats files = fileCacheStats();
        CacheStats sizes = folderSizeCacheStats();
        return new CacheStats(
                listing.hits() + sizes.hits() + files.hits(),
                listing.misses() + sizes.misses() + files.misses(),
                listing.evictions() + sizes.evictions() + files.evictions());
    }

    private DirectoryListing loadDirectoryListing(String key) throws IOException {
        return delegate.listFolder(Path.of(key));
    }

    private long loadFolderSize(String key) throws IOException {
        return delegate.getFolderSize(Path.of(key));
    }

    private String loadFileContents(String key) throws IOException {
        return delegate.readFile(Path.of(key));
    }

    private String keyFor(Path path) {
        return path.toAbsolutePath().normalize().toString();
    }

    private void invalidateCaches(Path target, boolean targetIsDirectory) {
        String key = keyFor(target);
        fileCache.invalidate(key);
        folderSizeCache.invalidate(key);
        folderListingCache.invalidate(key);

        if (targetIsDirectory) {
            invalidateDescendants(key);
        }

        Path parent = target.getParent();
        while (parent != null) {
            String parentKey = keyFor(parent);
            folderListingCache.invalidate(parentKey);
            folderSizeCache.invalidate(parentKey);
            parent = parent.getParent();
        }
    }

    private void invalidateDescendants(String targetKey) {
        String prefix = targetKey.equals(File.separator) ? targetKey : targetKey + File.separator;
        folderListingCache.asMap().keySet().removeIf(k -> k.startsWith(prefix));
        folderSizeCache.asMap().keySet().removeIf(k -> k.startsWith(prefix));
        fileCache.asMap().keySet().removeIf(k -> k.startsWith(prefix));
    }
}
