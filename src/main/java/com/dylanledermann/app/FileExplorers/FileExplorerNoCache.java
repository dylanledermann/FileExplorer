package com.dylanledermann.app.FileExplorers;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class FileExplorerNoCache implements FileExplorer {

    @Override
    public DirectoryListing listFolder(Path directory) throws IOException {
        if (directory == null || !Files.exists(directory) || !Files.isDirectory(directory)) {
            throw new IOException("Invalid directory: " + directory);
        }

        List<Path> paths = new ArrayList<>();
        try (var stream = Files.list(directory)) {
            stream.forEach(paths::add);
        }

        paths.sort(Comparator.comparing((Path path) -> !Files.isDirectory(path))
                .thenComparing(path -> path.getFileName().toString().toLowerCase()));

        List<DirectoryEntry> entries = new ArrayList<>();
        long totalSize = 0;
        for (Path path : paths) {
            boolean isDirectory = Files.isDirectory(path);
            long size = isDirectory ? getFolderSize(path) : Files.size(path);
            entries.add(new DirectoryEntry(path, path.getFileName().toString(), isDirectory, size));
            totalSize += size;
        }

        return new DirectoryListing(directory.toAbsolutePath().normalize(), entries, totalSize);
    }

    @Override
    public String readFile(Path file) throws IOException {
        if (file == null || !Files.exists(file) || !Files.isRegularFile(file)) {
            throw new IOException("Invalid file: " + file);
        }
        return Files.readString(file);
    }

    @Override
    public boolean deleteTarget(Path target) throws IOException {
        if (target == null || !Files.exists(target)) {
            throw new IOException("Delete target does not exist: " + target);
        }

        Files.walkFileTree(target, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                Files.delete(file);
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
                if (exc != null) {
                    throw exc;
                }
                Files.delete(dir);
                return FileVisitResult.CONTINUE;
            }
        });

        return true;
    }

    @Override
    public long getFolderSize(Path folder) throws IOException {
        if (folder == null || !Files.exists(folder) || !Files.isDirectory(folder)) {
            throw new IOException("Invalid directory: " + folder);
        }

        try (var stream = Files.walk(folder)) {
            return stream.filter(Files::isRegularFile)
                    .mapToLong(path -> {
                        try {
                            return Files.size(path);
                        } catch (IOException e) {
                            throw new UncheckedIOException(e);
                        }
                    })
                    .sum();
        } catch (UncheckedIOException e) {
            throw e.getCause();
        }
    }

    @Override
    public boolean isTextFile(Path file) throws IOException {
        if (file == null || !Files.exists(file) || !Files.isRegularFile(file)) {
            return false;
        }
        String mimeType = Files.probeContentType(file);
        return mimeType != null && mimeType.startsWith("text/");
    }

    @Override
    public boolean verifyTarget(String option, String target, Path current) throws IOException {
        if (option == null) {
            return false;
        }
        Path newTarget = current.resolve(target).normalize();
        switch (option.toUpperCase()) {
            case "F":
                return Files.exists(newTarget) && Files.isDirectory(newTarget);
            case "R":
                return Files.exists(newTarget) && Files.isRegularFile(newTarget) && isTextFile(newTarget);
            case "D":
                return Files.exists(newTarget);
            default:
                return true;
        }
    }
}
