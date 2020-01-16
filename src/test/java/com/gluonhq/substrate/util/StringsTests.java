/*
 * Copyright (c) 2019, 2020, Gluon
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

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.*;

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

    @Test
    void testSplit() {

        assertIterableEquals(
            Arrays.asList("aaa","bbb","ccc"),
            Strings.split( "aaa:bbb:ccc", ":" ));

        assertIterableEquals(
                Collections.emptyList(),
                Strings.split( "", ":" ));

        assertIterableEquals(
                Collections.emptyList(),
                Strings.split( null, ":" ));

    }

    @Test
    void testCommaSplit() {

        assertIterableEquals(
                Arrays.asList("aaa","bbb","ccc"),
                Strings.split( "aaa,bbb,ccc"));

        assertIterableEquals(
                Collections.emptyList(),
                Strings.split( "" ));

        assertIterableEquals(
                Collections.emptyList(),
                Strings.split( null ));

    }
    
    @Test
    void randomStringGeneration() {
        final String randomString = Strings.randomString(6);
        assertEquals(6, randomString.length());
        assertTrue(randomString.matches("^[a-z]*$"));
    }


}
