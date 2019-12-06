package com.gluonhq.substrate.model;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.*;

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
        assertIterableEquals(cp.filter("bbb"::equals), Collections.singletonList("bbb"));
    }

    @Test
    public void testMapToList() {
        var cp = new ClassPath("aaa:bbb:ccc");
        assertIterableEquals(cp.mapToList( s -> "bbb".equals(s)? "xxx": s ), Arrays.asList("aaa","xxx","ccc"));
    }

    @Test
    public void testMapToString() {
        var cp = new ClassPath("aaa:bbb:ccc");
        assertEquals(cp.mapToString( s -> "bbb".equals(s)? "xxx": s ), "aaa:xxx:ccc");
    }

    @Test
    public void mapWithJavaFxLibs() {
        var cp = new ClassPath("javafx-base:javafx-graphics");
        String newCp =  cp.mapWithJavaFxLibs(Path.of("/aa/bb/"), "javafx-base", "javafx-graphics");
        assertEquals(newCp, "/aa/bb/javafx-base.jar:/aa/bb/javafx-graphics.jar");
    }


}
