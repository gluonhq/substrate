package com.gluonhq.substrate.model;

import com.gluonhq.substrate.util.FileOps;
import org.junit.jupiter.api.Test;

import java.io.File;
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

    private static final String PS = File.pathSeparator;
    private static final String SS = File.separator;

    @Test
    public void testContains() {
        var cp = new ClassPath("aaa" + PS + "bbb" + PS + "ccc");
        assertTrue( cp.contains("bbb"::equals));
        assertFalse( cp.contains("xxx"::equals));
    }

    @Test
    public void testFilter() {
        var cp = new ClassPath("aaa" + PS + "bbb" + PS + "ccc");
        assertIterableEquals(Collections.singletonList("bbb"), cp.filter("bbb"::equals));
    }

    @Test
    public void testMapToList() {
        var cp = new ClassPath("aaa" + PS + "bbb" + PS + "ccc");
        assertIterableEquals(Arrays.asList("aaa","xxx","ccc"), cp.mapToList( s -> "bbb".equals(s)? "xxx": s ));
    }

    @Test
    public void testMapToString() {
        var cp = new ClassPath("aaa" + PS + "bbb" + PS + "ccc");
        assertEquals("aaa" + PS + "xxx" + PS + "ccc", cp.mapToString( s -> "bbb".equals(s)? "xxx": s ));
    }

    @Test
    public void testMapToStringWithDuplicates() {
        var cp = new ClassPath("aaa" + PS + "bbb" + PS + "ccc" + PS + "bbb");
        assertEquals("aaa" + PS + "xxx" + PS + "ccc", cp.mapToString( s -> "bbb".equals(s)? "xxx": s ));
    }

    @Test
    public void mapWithLibs() {
        var cp = new ClassPath("javafx-base" + PS + "javafx-graphics");
        String newCp = cp.mapWithLibs(Path.of(SS + "aa" + SS + "bb" + SS), "javafx-base", "javafx-graphics");
        assertEquals(SS + "aa" + SS + "bb" + SS + "javafx-base.jar" + PS + SS + "aa" + SS + "bb" + SS + "javafx-graphics.jar", newCp);
    }

    @Test
    public void mapWithLibsAndMap() {
        var cp = new ClassPath("javafx-base" + PS + "javafx-graphics");
        String newCp = cp.mapWithLibs(Path.of(SS + "aa" + SS + "bb" + SS),
                s -> s.replace("-", "."),
                null,
                "javafx-base", "javafx-graphics");
        assertEquals(SS + "aa" + SS + "bb" + SS + "javafx.base.jar" + PS + SS + "aa" + SS + "bb" + SS + "javafx.graphics.jar", newCp);
    }

    @Test
    public void mapWithLibsAndTest() {
        var cp = new ClassPath("javafx-base" + PS + "javafx-graphics");
        String newCp = cp.mapWithLibs(Path.of(SS + "aa" + SS + "bb" + SS),
                null,
                p -> Files.exists(p),
                "javafx-base", "javafx-graphics");
        assertEquals("javafx-base" + PS + "javafx-graphics", newCp);
    }

    @Test
    public void mapWithLibsAndThrowException() {
        var cp = new ClassPath("javafx-base"+ PS +" javafx-graphics");
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
        var cp = new ClassPath("aaa.jar" + PS + "bbb.jar" + PS + "ccc.jar" + PS + "aaa.jar");
        var jars = cp.getJars(false);
        assertEquals(3, jars.size());
    }

}
