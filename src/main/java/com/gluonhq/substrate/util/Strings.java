/*
 * Copyright (c) 2019, 2021, Gluon
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.

 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
 * ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL GLUON BE LIABLE FOR ANY
 * DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
 * (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
 * ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package com.gluonhq.substrate.util;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Random;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

public final class Strings {

    private static final Pattern substitutionPattern = Pattern.compile("\\$\\{(.+?)}");
    private static final String KEEP_STRING_SUFFIX = "_$$";

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

    /**
     * Safely converts a string of comma separated strings into an array of strings
     * Returns empty list if the string is null or empty
     * @param s a string that contains comma separated strings
     * @return a list of strings
     */
    public static List<String> split(String s) {
        return split(s,",");
    }

    /**
     * Safely converts a string of 'delimiter' separated strings into an array of strings
     * Returns empty list if the string is null or empty
     * @param s a string that contains 'delimiter' separated strings
     * @param delimiter delimiter used
     * @return a list of strings
     */
    public static List<String> split(String s, String delimiter) {
        return s == null || s.trim().isEmpty() ? Collections.emptyList() : Arrays.asList(s.split(delimiter));
    }

    public static String randomString(int targetStringLength) {
        int leftLimit = 97; // letter 'a'
        int rightLimit = 122; // letter 'z'
        Random random = new Random();
        return random.ints(leftLimit, rightLimit + 1)
                .limit(targetStringLength)
                .collect(StringBuilder::new, StringBuilder::appendCodePoint, StringBuilder::append)
                .toString();
    }

    /**
     * Removes duplicated strings from a sourceList list, keeping the items with higher index
     * in the list. Any item in the keep list will not be removed.
     *
     * @param sourceList the initial list of strings with possible duplicates
     * @param keepList the list of items that can't be removed
     * @return
     */
    public static List<String> removeDuplicates(List<String> sourceList, List<String> keepList) {
        List<String> modSourceList = IntStream.range(0, sourceList.size())
                .mapToObj(i -> {
                    String s = sourceList.get(i);
                    if (keepList.stream().anyMatch(k -> k.equals(s))) {
                        return s + KEEP_STRING_SUFFIX + i;
                    }
                    return s;
                }).collect(Collectors.toList());

        List<String> uniqueReversedSourceList = reverseIndices(0, modSourceList.size())
                .mapToObj(modSourceList::get)
                .distinct()
                .collect(Collectors.toList());

        List<String> uniqueSortedSourceList = reverseIndices(0, uniqueReversedSourceList.size())
                .mapToObj(uniqueReversedSourceList::get)
                .collect(Collectors.toList());

        return uniqueSortedSourceList.stream()
                .map(s -> s.contains(KEEP_STRING_SUFFIX) ? s.substring(0, s.indexOf(KEEP_STRING_SUFFIX)) : s)
                .collect(Collectors.toList());
    }

    /**
     * Returns an IntStream with reversed indices
     * @param from initial index of a given range
     * @param to final index of a given range
     * @return an {@link IntStream}
     */
    private static IntStream reverseIndices(int from, int to) {
        return IntStream.range(from, to)
                .map(i -> to - i + from - 1);
    }
}
