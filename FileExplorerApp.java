package FileExplorers;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Scanner;

public class FileExplorerApp {
    public static void main(String[] args) {
        boolean useCache = args.length > 0 && "--cache".equals(args[0]);
        FileExplorer explorer = useCache ? new CachedFileExplorer(new FileExplorerNoCache())
                : new FileExplorerNoCache();
        try {
            runLoop(explorer);
        } catch (IOException e) {
            System.err.println("Error: " + e.getMessage());
            e.printStackTrace();
        } finally {
            if (explorer instanceof AutoCloseable closeable) {
                try {
                    closeable.close();
                } catch (Exception ignored) {
                }
            }
        }
    }

    private static void runLoop(FileExplorer explorer) throws IOException {
        try (Scanner scanner = new Scanner(System.in)) {
            Path currentDirectory = Path.of(System.getProperty("user.home"));
            printListing(explorer.listFolder(currentDirectory));
            while (true) {
                System.out.print("$ ");
                String[] input = parseInput(scanner.nextLine());
                String option = input[0].substring(0, 1).toUpperCase();
                String target = input.length > 1 ? input[1].trim() : "";

                if (option.equals("E")) {
                    break;
                }

                switch (option) {
                    case "B" -> {
                        if (currentDirectory.getParent() != null) {
                            currentDirectory = currentDirectory.getParent();
                            printListing(explorer.listFolder(currentDirectory));
                        } else {
                            System.out.println("Already at root directory.");
                        }
                    }
                    case "F" -> {
                        if (explorer.verifyTarget("F", target, currentDirectory)) {
                            currentDirectory = currentDirectory.resolve(target).normalize();
                            printListing(explorer.listFolder(currentDirectory));
                        } else {
                            System.out.println("Invalid folder target: " + target);
                        }
                    }
                    case "R" -> {
                        if (explorer.verifyTarget("R", target, currentDirectory)) {
                            System.out.println(explorer.readFile(currentDirectory.resolve(target)));
                        } else {
                            System.out.println("Invalid text file target: " + target);
                        }
                    }
                    case "D" -> {
                        if (explorer.verifyTarget("D", target, currentDirectory)) {
                            System.out.printf("Confirm delete %s (y/N): ", target);
                            String confirm = scanner.nextLine().trim().toLowerCase();
                            if (confirm.equals("y")) {
                                explorer.deleteTarget(currentDirectory.resolve(target));
                                System.out.println("Deleted " + target);
                            } else {
                                System.out.println("Cancelled deletion");
                            }
                        } else {
                            System.out.println("Invalid delete target: " + target);
                        }
                    }
                    case "L" -> printListing(explorer.listFolder(currentDirectory));
                    case "H" -> printHelp();
                    default -> System.out.println("Unknown command. Input 'H' for help.");
                }
            }
        }
    }

    private static void printListing(DirectoryListing listing) {
        System.out.println("\n Directory: " + listing.directory());
        System.out.println("-".repeat(60));
        System.out.printf("%-40s %-10s %-10s%n", "Name", "Type", "Size");
        System.out.println("-".repeat(60));
        for (DirectoryEntry entry : listing.entries()) {
            System.out.printf("%-40s %-10s %-10s%n",
                    entry.icon() + " " + entry.name(),
                    entry.type(),
                    formatSize(entry.size()));
        }
        System.out.println("-".repeat(60));
        System.out.printf("%-40s %-10s %-10s%n",
                "Total (" + listing.entries().size() + " items)",
                "",
                formatSize(listing.totalSize()));
    }

    private static void printHelp() {
        System.out.println("Usage:");
        System.out.println("  B             - Go to parent directory");
        System.out.println("  F <directory> - Open child directory");
        System.out.println("  R <file>      - Read a file");
        System.out.println("  D <target>    - Delete a file or folder");
        System.out.println("  L             - List current directory");
        System.out.println("  E             - Exit");
        System.out.println("  H             - Help");
    }

    private static String[] parseInput(String line) {
        String trimmed = line == null ? "" : line.trim();
        return trimmed.isEmpty() ? new String[] { "H" } : trimmed.split("\\s+", 2);
    }

    private static String formatSize(long bytes) {
        if (bytes <= 0) {
            return "0 B";
        }
        String[] units = { "B", "KB", "MB", "GB", "TB" };
        int unitIndex = (int) (Math.log10(bytes) / Math.log10(1024));
        unitIndex = Math.min(unitIndex, units.length - 1);
        double value = bytes / Math.pow(1024, unitIndex);
        return String.format("%.1f %s", value, units[unitIndex]);
    }
}
