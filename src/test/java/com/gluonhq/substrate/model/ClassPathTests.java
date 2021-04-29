package com.gluonhq.substrate.model;

import com.gluonhq.substrate.util.FileOps;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertIterableEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class ClassPathTests {

    @Test
    public void testContains() {
        var cp = new ClassPath("aaa:bbb:ccc");
        assertTrue( cp.contains("bbb"::equals));
        assertFalse( cp.contains("xxx"::equals));
    }

    @Test
    public void testFilter() {
        var cp = new ClassPath("aaa:bbb:ccc");
        assertIterableEquals(Collections.singletonList("bbb"), cp.filter("bbb"::equals));
    }

    @Test
    public void testMapToList() {
        var cp = new ClassPath("aaa:bbb:ccc");
        assertIterableEquals(Arrays.asList("aaa","xxx","ccc"), cp.mapToList( s -> "bbb".equals(s)? "xxx": s ));
    }

    @Test
    public void testMapToString() {
        var cp = new ClassPath("aaa:bbb:ccc");
        assertEquals("aaa:xxx:ccc", cp.mapToString( s -> "bbb".equals(s)? "xxx": s ));
    }

    @Test
    public void testMapToStringWithDuplicates() {
        var cp = new ClassPath("aaa:bbb:ccc:bbb");
        assertEquals("aaa:xxx:ccc", cp.mapToString( s -> "bbb".equals(s)? "xxx": s ));
    }

    @Test
    public void mapWithLibs() {
        var cp = new ClassPath("javafx-base:javafx-graphics");
        String newCp = cp.mapWithLibs(Path.of("/aa/bb/"), "javafx-base", "javafx-graphics");
        assertEquals("/aa/bb/javafx-base.jar:/aa/bb/javafx-graphics.jar", newCp);
    }

    @Test
    public void mapWithLibsAndMap() {
        var cp = new ClassPath("javafx-base:javafx-graphics");
        String newCp = cp.mapWithLibs(Path.of("/aa/bb/"),
                s -> s.replace("-", "."),
                null,
                "javafx-base", "javafx-graphics");
        assertEquals("/aa/bb/javafx.base.jar:/aa/bb/javafx.graphics.jar", newCp);
    }

    @Test
    public void mapWithLibsAndTest() {
        var cp = new ClassPath("javafx-base:javafx-graphics");
        String newCp = cp.mapWithLibs(Path.of("/aa/bb/"),
                null,
                p -> Files.exists(p),
                "javafx-base", "javafx-graphics");
        assertEquals("javafx-base:javafx-graphics", newCp);
    }

    @Test
    public void mapWithLibsAndThrowException() {
        var cp = new ClassPath("javafx-base:javafx-graphics");
        assertThrows(IllegalArgumentException.class, () -> cp.mapWithLibs(Path.of("/aa/bb/"),
                null,
                p -> {
                    if (!Files.exists(p)) {
                        throw new IllegalArgumentException("Error: " + p + " not found");
                    }
                    return true;
                },
                "javafx-base", "javafx-graphics"));
    }

    @Test
    public void mapWithLibsMapTest() throws IOException {
        Path resourcePath = Files.createTempDirectory("substrate-tests").resolve("substrate-test.jar");
        FileOps.copyResource("/substrate-test.jar", resourcePath);
        assertNotNull(resourcePath);
        assertTrue(Files.exists(resourcePath));

        var cp = new ClassPath("substrate=test");
        String newCp = cp.mapWithLibs(resourcePath.getParent(),
                s -> s.replace("=", "-"),
                p -> {
                    if (!Files.exists(p)) {
                        throw new IllegalArgumentException("Error: " + p + " not found");
                    }
                    return true;
                },
                "substrate=test");
        assertEquals(resourcePath.toString(), newCp);
        Files.deleteIfExists(resourcePath);
    }

    @Test
    public void duplicatedJarTest() throws IOException, InterruptedException {
        var cp = new ClassPath("aaa.jar:bbb.jar:ccc.jar:aaa.jar");
        var jars = cp.getJars(false);
        assertEquals(3, jars.size());
    }

}
