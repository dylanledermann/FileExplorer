package com.dylanledermann.app;

import java.io.IOException;

import com.dylanledermann.app.FileExplorers.FileExplorerNoCache;
import org.junit.jupiter.api.BeforeAll;

public class FileExplorerNoCacheTests extends AbstractFileExplorerTests {

    @BeforeAll
    public static void initiateTests() {
        System.out.println("============ No Cache Tests ============");
    }

    @Override
    protected com.dylanledermann.app.FileExplorers.FileExplorer createExplorer() throws IOException {
        return new FileExplorerNoCache();
    }
}
