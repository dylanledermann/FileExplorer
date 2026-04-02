# File Explorer In Java
This application tests file explorers with different cache implementations.
[Maven](https://maven.apache.org/index.html) is used to bundle and test the application.
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
mvn clean package
```

Run the explorer:
```bash
# Run the file explorer with no cache
java -jar target/file-explorer-1.0-SNAPSHOT.jar
# Run the file explorer with the generic cache
java -jar target/file-explorer-1.0-SNAPSHOT.jar --cache=generic
# Run the file explorer with the caffeine cache
java -jar target/file-explorer-1.0-SNAPSHOT.jar --cache=caffeine
```

## Tests

Run the tests:
```bash
mvn -q test
# Run specific test
mvn -q -Dtest={ClassName,ClassName2,ClassName#MethodName+MethodName2}
```