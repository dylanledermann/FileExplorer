package com.dylanledermann.app.FileExplorers;

import java.io.IOException;
import java.nio.file.Path;

public interface FileExplorer {
    DirectoryListing listFolder(Path directory) throws IOException;

    String readFile(Path file) throws IOException;

    boolean deleteTarget(Path target) throws IOException;

    long getFolderSize(Path folder) throws IOException;

    boolean isTextFile(Path file) throws IOException;

    boolean verifyTarget(String option, String target, Path current) throws IOException;
}
