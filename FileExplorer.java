import java.io.File;
import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.text.DecimalFormat;
import java.util.Scanner;

public class FileExplorer {

    /**
     * main function that starts the file reading service
     */
    public static void main(String[] args) {
        System.out.println("Starting FileReader...");
        loop();
        System.out.println("Closing FileReader");
    }

    /**
     * Runs loop that waits for user input, then runs user input
     * User input: option target
     * options:
     * 'b' - back a level (up)
     * 'f' - forward a level (down) (followed by a directory)
     * 'r' - read a file (followed by a text file)
     * 'd' - delete a file/folder (followed by file or folder)
     * 'l' - shows the current directory
     * 'e' - closes the file reader
     * 'h or help' - gives help menu
     */
    public static void loop() {
        Scanner scanner = new Scanner(System.in);
        // Set current directory to home
        File currentDirectory = new File(System.getProperty("user.home"));
        listFolder(currentDirectory);
        System.out.print("$ ");
        String[] userInput = parseInput(scanner.nextLine());
        String targetFile = userInput.length > 1 ? userInput[1] : "";
        String currentOption = userInput[0].substring(0, 1).toUpperCase();
        while (!currentOption.equals("E")) {
            if (verifyTarget(currentOption, targetFile, currentDirectory)) {
                currentDirectory = execute(currentOption, targetFile, currentDirectory);
            } else {
                System.out.println("Invalid Option, input 'H' for help");
            }
            System.out.print("$ ");
            userInput = parseInput(scanner.nextLine());
            targetFile = userInput.length > 1 ? userInput[1] : "";
            currentOption = userInput[0].substring(0, 1).toUpperCase();
        }
        scanner.close();
    }

    private static String[] parseInput(String line) {
        String trimmed = line.trim();
        return trimmed.isEmpty() ? new String[] { "H" } : trimmed.split("\\s+", 2);
    }

    public static void listFolder(File directory) {
        if (!directory.exists() || !directory.isDirectory()) {
            System.out.println("Invalid directory" + directory.getPath());
            return;
        }

        System.out.println("\n Directory: " + directory.getAbsolutePath());
        System.out.println("-".repeat(60));
        System.out.printf("%-40s %-10s %-10s%n", "Name", "Type", "Size");
        System.out.println("-".repeat(60));

        File[] files = directory.listFiles();
        if (files == null || files.length == 0) {
            System.out.println("  (empty directory)");
            return;
        }

        // Sort: folders first, then files
        java.util.Arrays.sort(files, (a, b) -> {
            if (a.isDirectory() && !b.isDirectory())
                return -1;
            if (!a.isDirectory() && b.isDirectory())
                return 1;
            return a.getName().compareToIgnoreCase(b.getName());
        });

        long totalSize = 0;
        for (File file : files) {
            String icon = file.isDirectory() ? "📁" : "📄";
            String type = file.isDirectory() ? "Folder" : "File";
            long size = file.isDirectory() ? getFolderSize(file) : file.length();
            totalSize += size;

            System.out.printf("%-40s %-10s %-10s%n",
                    icon + " " + truncate(file.getName(), 37),
                    type,
                    formatSize(size));
        }
        System.out.println("-".repeat(60));
        System.out.printf("%-40s %-10s %-10s%n",
                "Total (" + files.length + " items)",
                "",
                formatSize(totalSize));
    }

    static long getFolderSize(File folder) {
        if (!folder.isDirectory()) {
            System.out.println("Error: getFolderSize() input is not a directory: " + folder.getAbsolutePath());
            return 0;
        }
        long size = 0;
        File[] contents = folder.listFiles();
        if (contents != null) {
            for (File file : contents) {
                size += file.isDirectory() ? getFolderSize(file) : file.length();
            }
        }

        return size;
    }

    static void listHelpMenu() {
        System.out.println("""
                Usage:
                    'B' - Go to parent directory
                    'F' <directory> - Open child directory
                    'R' <file> - Read a file
                    'D' <file/directory> - Delete a file/folder
                    'L' - Lists the current location
                    'E' - Close the file reader
                    'H' - Display the help menu
                """);
    }

    static String truncate(String text, int len) {
        if (text.length() <= len) {
            return text;
        }

        return text.substring(0, len - 3) + "...";
    }

    static String formatSize(long bytes) {
        if (bytes <= 0)
            return "0 B";
        String[] units = { "B", "KB", "MB", "GB", "TB" };
        int unitIndex = (int) (Math.log10(bytes) / Math.log10(1024));
        unitIndex = Math.min(unitIndex, units.length - 1);
        double value = bytes / Math.pow(1024, unitIndex);
        return new DecimalFormat("#,##0.#").format(value) + " " + units[unitIndex];
    }

    public static boolean isTextFile(File file) {
        try {
            String mimeType = Files.probeContentType(file.toPath());
            System.out.println(mimeType);
            return mimeType != null && mimeType.startsWith("text/");
        } catch (Exception e) {
            System.out.print("Error Occurred Checking File Type: ");
            e.printStackTrace();
            return false;
        }
    }

    public static boolean verifyTarget(String option, String target, File current) {
        File newTarget = current.toPath().resolve(target).toFile();
        switch (option) {
            case "F":
                return newTarget.exists() && newTarget.isDirectory();
            case "R":
                return newTarget.exists() && newTarget.isFile() && isTextFile(newTarget);
            case "D":
                return newTarget.exists();
            default:
                return true;
        }
    }

    public static File execute(String option, String target, File current) {
        switch (option) {
            case "B":
                // Go Back
                if (current.getParentFile() != null) {
                    listFolder(current.getParentFile());
                    return current.getParentFile();
                }
                listFolder(current);
                return current;
            case "F":
                // Go Foward(this.target)
                listFolder(current.toPath().resolve(target).toFile());
                break;
            case "R":
                // Print file(this.target)
                try {
                    System.out.println(Files.readString(current.toPath().resolve(target)));
                } catch (Exception e) {
                    System.out.print("Error Occurred Reading File: ");
                    e.printStackTrace();
                }
                return current;
            case "D":
                // Delete target(this.target)
                // {files, directories, total space (file space)}
                Scanner scanner = new Scanner(System.in);
                System.out.printf("Confirm delete %s (y/N): ", target);
                String confirm = scanner.nextLine().trim().toLowerCase();
                if (!confirm.equals("y")) {
                    System.out.println("Cancelled deletion");
                    return current;
                }
                long[] values = { 0, 0, 0 };
                try {
                    // Recursively walk file tree deleting files and storing their information.
                    Files.walkFileTree(current.toPath().resolve(target), new SimpleFileVisitor<Path>() {
                        @Override
                        public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                            values[0]++;
                            values[2] += file.toFile().length();
                            Files.delete(file); // Delete the file
                            return FileVisitResult.CONTINUE;
                        }

                        @Override
                        public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
                            values[1]++;
                            Files.delete(dir); // Delete the directory after its contents are gone
                            return FileVisitResult.CONTINUE;
                        }
                    });
                } catch (Exception e) {
                    System.out.print("Error Occurred Deleting File " + target + ": ");
                    e.printStackTrace();
                }
                System.out.printf("Files deleted: %s, Directories deleted: %s, Total Space Freed: %s %n", values[0],
                        values[1], formatSize(values[2]));
                return current;
            case "L":
                // Open current directory
                listFolder(current);
                return current;
            default:
                listHelpMenu();
        }
        return current.toPath().resolve(target).toFile();
    }
}