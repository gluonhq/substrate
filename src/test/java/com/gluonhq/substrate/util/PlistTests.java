/*
 * Copyright (c) 2021, Gluon
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

import com.dd.plist.NSDictionary;
import com.dd.plist.NSObject;
import com.gluonhq.substrate.util.plist.NSArrayEx;
import com.gluonhq.substrate.util.plist.NSDictionaryEx;
import com.gluonhq.substrate.util.plist.NSObjectEx;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class PlistTests {

    private Path getTempDir() throws IOException {
        return Files.createTempDirectory("substrate-tests");
    }

    @Test
    void testDictionary() throws Exception {
        InputStream stream = FileOps.resourceAsStream("/test1.plist");
        Path path1 = FileOps.copyStream(stream, getTempDir().resolve("test1.plist"));
        assertTrue(Files.exists(path1));

        NSObjectEx nsObjectEx = new NSObjectEx(path1);
        assertNotNull(nsObjectEx);
        assertNull(nsObjectEx.getAsArrayEx());

        NSDictionaryEx nsDictionaryEx = nsObjectEx.getAsDictionaryEx();
        assertNotNull(nsDictionaryEx);
        assertEquals(2, nsDictionaryEx.getAllKeys().length, "invalid length for all keys");

        assertEquals("one", nsDictionaryEx.getString("oneKey"), "invalid key-value for string");

        assertThrows(ClassCastException.class, () -> nsDictionaryEx.getString("twoKey"));
        NSObject[] array = nsDictionaryEx.getArray("twoKey");
        assertNotNull(array);
        assertEquals(1, array.length, "invalid length for array");
        assertEquals("test", array[0].toJavaObject(), "invalid value for array[0]");

        nsDictionaryEx.put("threeKey", "new test");
        assertEquals(3, nsDictionaryEx.getAllKeys().length, "invalid length for all keys");

        assertEquals("one", nsObjectEx.getValueFromDictionary("oneKey"), "invalid value for key");
        nsObjectEx.setValueToDictionary("oneKey", "new one");
        assertEquals("new one", nsObjectEx.getValueFromDictionary("oneKey"), "invalid value for key");

        Path path10 = getTempDir().resolve("test10.plist");
        nsDictionaryEx.saveAsXML(path10);
        assertTrue(Files.exists(path10));

        nsObjectEx = new NSObjectEx(path10);
        assertNotNull(nsObjectEx);
        NSDictionaryEx dict = nsObjectEx.getAsDictionaryEx();
        assertEquals(3, dict.getAllKeys().length, "invalid length for all keys");
        assertEquals("new test", dict.getString("threeKey"), "invalid key-value for string");

    }

    @Test
    void testArray() throws Exception {
        InputStream stream = FileOps.resourceAsStream("/test2.plist");
        Path path2 = FileOps.copyStream(stream, getTempDir().resolve("test2.plist"));
        assertTrue(Files.exists(path2));

        NSObjectEx nsObjectEx = new NSObjectEx(path2);
        assertNotNull(nsObjectEx);
        assertNull(nsObjectEx.getAsDictionaryEx());

        NSArrayEx nsArrayEx = nsObjectEx.getAsArrayEx();
        assertNotNull(nsArrayEx);

        assertEquals(2, nsArrayEx.getArray().length, "invalid length for array");

        assertTrue(nsArrayEx.getArray()[0] instanceof NSDictionary, "invalid object at 0");

        assertEquals("true", nsObjectEx.getValueFromDictionary("one"), "invalid value for key");
        assertEquals("test", nsObjectEx.getValueFromDictionary("two"), "invalid value for key");
        nsObjectEx.setValueToDictionary("one", "new test");
        assertEquals("new test", nsObjectEx.getValueFromDictionary("one"), "invalid value for key");

        Path path20 = getTempDir().resolve("test20.plist");
        nsArrayEx.saveAsXML(path20);
        assertTrue(Files.exists(path20));

        nsObjectEx = new NSObjectEx(path20);
        assertNotNull(nsObjectEx);
        nsArrayEx = nsObjectEx.getAsArrayEx();
        assertEquals(2, nsArrayEx.getArray().length, "invalid length for array");
        NSDictionaryEx dict = nsArrayEx.getFirstDictionaryEx().orElse(null);
        assertNotNull(dict);
        assertEquals(2, dict.getAllKeys().length, "invalid length for all keys");
        assertEquals("new test", dict.getString("one"), "invalid key-value for string");

    }
}
