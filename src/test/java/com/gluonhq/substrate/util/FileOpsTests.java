package com.gluonhq.substrate.util;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class FileOpsTests {


    private Path getTempDir() throws IOException {
        return Files.createTempDirectory("substrate-tests");
    }

    //--- resourceAsStream ----------------

    @Test
    void nullResourceAsStream() {
        assertThrows( NullPointerException.class, () -> FileOps.resourceAsStream(null));
    }

    @Test
    void existingResourceAsStream() throws IOException {
        assertNotNull( FileOps.resourceAsStream("/test-resource.txt"));
    }

    @Test
    void nonExistingResourceAsStream() {
        assertThrows( IOException.class, () -> FileOps.resourceAsStream("/xxx/xxx.txt"));
    }


    //--- copyResource ----------------

    @Test
    void copyExistingResource() throws IOException {
        Path resourcePath = FileOps.copyResource("/test-resource.txt", getTempDir());
        assertTrue(Files.exists(resourcePath));
        Files.deleteIfExists(resourcePath);
    }

    @Test
    void copyNonExistingResource() {
        assertThrows( IOException.class, () -> FileOps.copyResource("/xxx/xxx.txt", getTempDir()));
    }


    @Test
    void copyNullResource() {
        assertThrows( NullPointerException.class, () -> FileOps.copyResource(null, getTempDir()));
    }

    @Test
    void copyNullDestination() {
        assertThrows( NullPointerException.class, () -> FileOps.copyResource("/test-resource.txt", null));
    }


    //--- copyStream ----------------

    @Test
    void copyValidStream() throws IOException {
        InputStream stream = FileOps.resourceAsStream("/test-resource.txt");
        Path dest = FileOps.copyStream( stream, getTempDir().resolve("stream.txt"));
        assertTrue(Files.exists(dest));
        Files.deleteIfExists(dest);
    }

    @Test
    void copyNullStream() {
        assertThrows( NullPointerException.class, () -> FileOps.copyStream(null, getTempDir().resolve("stream.txt")));
    }

    @Test
    void copyNullStreamDestination() throws IOException {
        InputStream stream = FileOps.resourceAsStream("/test-resource.txt");
        assertThrows( NullPointerException.class, () -> FileOps.copyStream(stream,null));
    }

}
