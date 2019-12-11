/*
 * Copyright (c) 2019, Gluon
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

import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class Strings {

    private static final Pattern substitutionPattern = Pattern.compile("\\$\\{(.+?)}");
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
