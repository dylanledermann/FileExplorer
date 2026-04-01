# File Explorer In Java
This application is a simple file explorer built using Java.
The explorer has a cached explorer and no cache explorer to compare performances.
The use case for this application is generally finding the space of files/folders and deleting files/folders. The input options are the following:
```bash
'B' - Go to parent directory
'F' <directory> - Open child directory
'R' <file> - Read a file
'D' <file/directory> - Delete a file/folder
'L' - Lists the current location
'E' - Close the file reader
'H' - Display the help menu
```

## Build and run

Compile the project:
```bash
javac -d out FileExplorerApp.java FileExplorers/*.java Cache/*.java Cache/CacheLoader/*.java
```

Run the explorer:
```bash
# Run the file explorer with no cache
java -cp out FileExplorerApp
# Run the file explorer with the generic cache
java -cp out FileExplorerApp --cache
```

## Tests

Run the manual test harnesses:
```bash
java -cp out FileExplorers.FileExplorerNoCacheTests
java -cp out FileExplorers.CachedFileExplorerTests
java -cp out Cache.GenericCacheTests
```