package com.gluonhq.substrate.util;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;


import org.junit.jupiter.api.Test;
//import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class StringsTests {

    @Test
    void testSubstituteSuccess() {
        var vars = new HashMap<String,String>();
        vars.put("var1", "VAR1");
        vars.put("var2", "VAR2");

        String result = Strings.substitute( "123 ${var1} 456 ${var2} 789", vars::get);
        assertEquals( "123 VAR1 456 VAR2 789", result );

    }

    @Test
    void testSubstituteFailure() {
        var vars = new HashMap<String,String>();
        String template = "123 ${var1} 456 ${var2} 789";

        assertThrows( IllegalArgumentException.class,
                () -> Strings.substitute(template, vars::get) );

        assertThrows( NullPointerException.class,
                () -> Strings.substitute( null, vars::get) );

        assertThrows( NullPointerException.class,
                () -> Strings.substitute(template, (Function<String, String>) null) );

        assertThrows( NullPointerException.class,
                () -> Strings.substitute(template, (Map<String, String>) null) );
    }

}
