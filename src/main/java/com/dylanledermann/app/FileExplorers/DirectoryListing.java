package com.dylanledermann.app.FileExplorers;

import java.nio.file.Path;
import java.util.List;

public record DirectoryListing(Path directory, List<DirectoryEntry> entries, long totalSize) {
}
