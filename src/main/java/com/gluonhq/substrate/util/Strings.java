package com.gluonhq.substrate.util;

import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class Strings {

    private static Pattern substitutionPattern = Pattern.compile("\\$\\{(.+?)}");
    /**
     * Replaces keys within the template with values using context function
     * Throws IllegalArgumentException if the key found in the template has no corresponding value
     * @param template template to use
     * @param context context to get values from
     * @return resulting string with keys replaced with corresponding values
     */
    public static String substitute(String template, Function<String,String> context) {
        Objects.requireNonNull(context, "Context is required");
        Matcher matcher = substitutionPattern.matcher(Objects.requireNonNull(template, "Template is required"));
        // StringBuilder cannot be used here because Matcher expects StringBuffer
        StringBuffer buffer = new StringBuffer();
        while (matcher.find()) {
            String key = matcher.group(1);
            if (context.apply(key) != null) {
                // quote to work properly with $ and {,} signs
                matcher.appendReplacement(buffer, Matcher.quoteReplacement(context.apply(key)));
            } else {
                throw new IllegalArgumentException(String.format("Key `%s` is not found", key));
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
        return substitute(template, context::get);
    }

}
