package com.gluonhq.substrate.util;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class Strings {

    /**
     * Replaces keys within the template with values using context function
     * Throws IllegalArgumentException if the key found in the template has no corresponding value
     * @param template template to use
     * @param context context to get values from
     * @return resulting string with keys replaced with corresponding values
     */
    public static String substitute(String template, Function<String,String> context) {
        Objects.requireNonNull(context, "Context is required");
        Pattern pattern = Pattern.compile("\\$\\{(.+?)}");
        Matcher matcher = pattern.matcher(Objects.requireNonNull(template, "Template is required"));
        // StringBuilder cannot be used here because Matcher expects StringBuffer
        StringBuffer buffer = new StringBuffer();
        while (matcher.find()) {
            String key = matcher.group(1);
            if (context.apply(key) != null) {
                // quote to work properly with $ and {,} signs
                matcher.appendReplacement(buffer, Matcher.quoteReplacement(context.apply(key)) );
            } else {
                throw new IllegalArgumentException( String.format("Key `%s` is not found", key));
            }
        }
        matcher.appendTail(buffer);
        return buffer.toString();
    }

    /**
     * Replaces keys within the template with values using context map
     * Throws IllegalArgumentException if the key found in the template has no corresponding value
     * @param template template to use
     * @param context context to get values from
     * @return resulting string with keys replaced with corresponding values
     */
    public static String substitute(String template, Map<String,String> context) {
        Objects.requireNonNull(context);
        return substitute(template,context::get);
    }

    public static void main(String[] args) {

        var vars = new HashMap<String,String>();
        vars.put("var1", "VAR1");
        vars.put("var2", "VAR2");

        System.out.println("------------------------------------");
        System.out.println(Strings.substitute( "123 ${var1} 456 ${var2} 789", vars::get));
        System.out.println("------------------------------------");
    }

}
