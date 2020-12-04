package com.gluonhq.substrate.model;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertIterableEquals;
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
        String newCp =  cp.mapWithLibs(Path.of("/aa/bb/"), "javafx-base", "javafx-graphics");
        assertEquals("/aa/bb/javafx-base.jar:/aa/bb/javafx-graphics.jar", newCp);
    }

    @Test
    public void duplicatedJarTest() throws IOException, InterruptedException {
        var cp = new ClassPath("aaa.jar:bbb.jar:ccc.jar:aaa.jar");
        var jars = cp.getJars(false);
        assertEquals(3, jars.size());
    }

}
