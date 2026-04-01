package FileExplorers;

import java.nio.file.Path;

public record DirectoryEntry(
        Path path,
        String name,
        boolean directory,
        long size) {
    public String type() {
        return directory ? "Folder" : "File";
    }

    public String icon() {
        return directory ? "📁" : "📄";
    }
}
