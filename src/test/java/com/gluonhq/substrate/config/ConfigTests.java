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
package com.gluonhq.substrate.config;

import com.gluonhq.substrate.util.FileOps;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertEquals;

class ConfigTests {

    private static ConfigResolver resolver;

    @BeforeAll
    static void setClassPath() throws IOException {
        Path jarPath = Files.createTempDirectory("substrate-tests").resolve("substrate-test.jar");
        Path resourcePath = FileOps.copyResource("/substrate-test.jar", jarPath);
        assertNotNull(resourcePath);
        assertTrue(Files.exists(resourcePath));
        resolver = new ConfigResolver(resourcePath.toString());
    }

    @Test
    void testInitBuildNullArch() throws IOException {
        List<String> initList = resolver.getUserInitBuildTimeList(null);
        assertNotNull(initList);
        assertEquals(1, initList.size());
        assertTrue(initList.contains("this.is.a.test"));
    }

    @Test
    void testInitBuild() throws IOException {
        List<String> initList = resolver.getUserInitBuildTimeList("test");
        assertNotNull(initList);
        assertEquals(2, initList.size());
        assertTrue(initList.contains("this.is.a.test"));
        assertTrue(initList.contains("this.is.a.target.test"));
    }

    @Test
    void testReflectionNullArch() throws IOException {
        List<String> reflectionList = resolver.getUserReflectionList(null);
        assertNotNull(reflectionList);
        assertEquals(5, reflectionList.size());
        assertTrue(reflectionList.stream()
                .anyMatch(s -> "\"name\":\"this.is.a.test\",".equals(s.trim())));
        assertTrue(reflectionList.stream()
                .anyMatch(s -> "\"methods\":[{\"name\":\"test\",\"parameterTypes\":[\"int\"] }]".equals(s.trim())));
    }

    @Test
    void testReflection() throws IOException {
        List<String> reflectionList = resolver.getUserReflectionList("test");
        assertNotNull(reflectionList);
        assertEquals(10, reflectionList.size());
        assertTrue(reflectionList.stream()
                .anyMatch(s -> "\"name\":\"this.is.a.target.test\",".equals(s.trim())));
        assertTrue(reflectionList.stream()
                .anyMatch(s -> "\"methods\":[{\"name\":\"test\",\"parameterTypes\":[\"int\"] }]".equals(s.trim())));
    }

    @Test
    void testJNINullArch() throws IOException {
        List<String> jniList = resolver.getUserJNIList(null);
        assertNotNull(jniList);
        assertEquals(5, jniList.size());
        assertTrue(jniList.stream()
                .anyMatch(s -> "\"name\":\"this.is.a.test\",".equals(s.trim())));
        assertEquals(1, jniList.stream()
                .filter(s -> ",".equals(s.trim()))
                .count());
    }

    @Test
    void testJNI() throws IOException {
        List<String> jniList = resolver.getUserJNIList("test");
        assertNotNull(jniList);
        assertEquals(10, jniList.size());
        assertTrue(jniList.stream()
                .anyMatch(s -> "\"name\":\"this.is.a.test\",".equals(s.trim())));
        assertEquals(2, jniList.stream()
                .filter(s -> ",".equals(s.trim()))
                .count());
    }

}
