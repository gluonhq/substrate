/*
 * Copyright (c) 2019, 2020, Gluon
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
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

import com.gluonhq.substrate.target.AndroidTargetConfiguration;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class FileOpsTests {

    private static final String TEST_URL = "https://download2.gluonhq.com/substrate/test/";

    private Path getTempDir() throws IOException {
        return Files.createTempDirectory("substrate-tests");
    }

    //--- resourceAsStream ----------------

    @Test
    void nullResourceAsStream() {
        assertThrows( NullPointerException.class, () -> FileOps.resourceAsStream(null));
    }

    @Test
    void existingResourceAsStream() throws IOException {
        assertNotNull( FileOps.resourceAsStream("/test-resource.txt"));
    }

    @Test
    void nonExistingResourceAsStream() {
        assertThrows( IOException.class, () -> FileOps.resourceAsStream("/xxx/xxx.txt"));
    }


    //--- copyResource ----------------

    @Test
    void copyExistingResource() throws IOException {
        Path resourcePath = FileOps.copyResource("/test-resource.txt", getTempDir());
        assertTrue(Files.exists(resourcePath));
        Files.deleteIfExists(resourcePath);
    }

    @Test
    void copyNonExistingResource() {
        assertThrows( IOException.class, () -> FileOps.copyResource("/xxx/xxx.txt", getTempDir()));
    }


    @Test
    void copyNullResource() {
        assertThrows( NullPointerException.class, () -> FileOps.copyResource(null, getTempDir()));
    }

    @Test
    void copyNullDestination() {
        assertThrows( NullPointerException.class, () -> FileOps.copyResource("/test-resource.txt", null));
    }


    //--- copyStream ----------------

    @Test
    void copyValidStream() throws IOException {
        InputStream stream = FileOps.resourceAsStream("/test-resource.txt");
        Path dest = FileOps.copyStream( stream, getTempDir().resolve("stream.txt"));
        assertTrue(Files.exists(dest));
        Files.deleteIfExists(dest);
    }

    @Test
    void copyNullStream() {
        assertThrows( NullPointerException.class, () -> FileOps.copyStream(null, getTempDir().resolve("stream.txt")));
    }

    @Test
    void copyNullStreamDestination() throws IOException {
        InputStream stream = FileOps.resourceAsStream("/test-resource.txt");
        assertThrows( NullPointerException.class, () -> FileOps.copyStream(stream,null));
    }

    //--- downloadFile ----------------

    @Test
    void downloadNullURL() {
        assertThrows(NullPointerException.class, () -> FileOps.downloadFile(null, getTempDir().resolve("test.txt")));
    }

    @Test
    void downloadNullPath() {
        assertThrows(NullPointerException.class, () -> FileOps.downloadFile(new URL(TEST_URL), null));
    }

    @Test
    void downloadFile() throws IOException {
        Path testPath = getTempDir().resolve("test.txt");
        assertDoesNotThrow(() -> FileOps.downloadFile(new URL(TEST_URL + "test.txt"), testPath));
        assertTrue(Files.exists(testPath));
        assertEquals("some dummy tekst test", Files.readAllLines(testPath).get(0));
    }

    @Test
    void downloadZip() throws IOException {
        Path testZipPath = getTempDir().resolve("test.zip");
        assertDoesNotThrow(() -> FileOps.downloadFile(new URL(TEST_URL + "test.zip"), testZipPath));
        assertTrue(Files.exists(testZipPath));
    }

    //--- unzipFile ----------------

    @Test
    void unzipNull() {
        assertThrows(NullPointerException.class, () -> FileOps.unzipFile(null, getTempDir().resolve("test")));
        assertThrows(NullPointerException.class, () -> FileOps.unzipFile(getTempDir().resolve("test"), null));
    }

    @Test
    void unzipNotZip() throws IOException {
        Path testPath = getTempDir().resolve("test.txt");
        assertDoesNotThrow(() -> FileOps.downloadFile(new URL(TEST_URL + "test.txt"), testPath));
        assertDoesNotThrow(() -> {
            Map<String, String> map = FileOps.unzipFile(testPath, testPath.getParent());
            assertEquals(0, map.size());
        });
    }

    @Test
    void unzipNotDir() throws IOException {
        Path testZipPath = getTempDir().resolve("test.zip");
        assertDoesNotThrow(() -> FileOps.downloadFile(new URL(TEST_URL + "test.zip"), testZipPath));
        assertThrows(IOException.class, () -> FileOps.unzipFile(testZipPath, testZipPath));
    }

    @Test
    void unzipFile() throws IOException {
        Path testZip = getTempDir().resolve("test.zip");
        Path testPath = getTempDir().resolve("test");
        assertDoesNotThrow(() -> FileOps.downloadFile(new URL(TEST_URL + "test.zip"), testZip));
        assertTrue(Files.exists(testZip));
        assertDoesNotThrow(() -> {
            Map<String, String> sums = FileOps.unzipFile(testZip, testPath);
            assertEquals(1, sums.size());
            Path testFile = testPath.resolve("test.txt");
            String fileSum = sums.get(testFile.getFileName().toString());
            assertNotNull(fileSum);
            assertEquals("[51, 87, -48, -48, 101, -83, 47, -61, 24, 12, 94, -43, -13, -19, 101, -57]", fileSum);
            assertTrue(Files.exists(testFile));
            assertEquals("some dummy tekst test", Files.readAllLines(testFile).get(0));
        });
    }

    //--- processFile ----------------

    @Test
    void processFile() throws IOException {
        Path tempDir = getTempDir();
        assertDoesNotThrow(() -> {
            FileOps.downloadAndUnzip(TEST_URL + "test.zip", tempDir, "test.zip", "testZip", "1", null, "2");
            Path testFile = Path.of(tempDir.toString(), "testZip", "1", "2", "test.txt");
            Path testMd5File = Path.of(tempDir.toString(), "testZip", "1", "2", "testZip-2.md5");
            assertTrue(Files.exists(testFile));
            assertTrue(Files.exists(testMd5File));
            Map<String, String> hashMap = FileOps.getHashMap(testMd5File.toString());
            assertNotNull(hashMap);
            assertEquals(hashMap.get(testFile.getFileName().toString()), FileOps.calculateCheckSum(testFile.toFile()));
        });
    }

    @Test
    void parseXMLFile() throws IOException {
        Path xmlPath = FileOps.copyResource("/test-ops.xml", getTempDir().resolve("test-ops.xml"));
        assertTrue(Files.exists(xmlPath));
        assertNull(FileOps.getNodeValue(xmlPath.toString(), "aa", "bb"));
        assertNull(FileOps.getNodeValue(xmlPath.toString(), "manifest", "aa"));
        assertEquals("1", FileOps.getNodeValue(xmlPath.toString(), "manifest", ":versionCode"));
        assertEquals("HelloTest", FileOps.getNodeValue(xmlPath.toString(), "application", ":label"));
        Files.deleteIfExists(xmlPath);
    }

    @Test
    void parseNonXMLFile() throws IOException {
        assertThrows(IOException.class, () -> FileOps.getNodeValue("non.existent.path", "aa", "bb"));
        Path resourcePath = FileOps.copyResource("/test-resource.txt", getTempDir().resolve("test-resource.txt"));
        assertThrows(IOException.class, () -> FileOps.getNodeValue(resourcePath.toString(), "aa", "bb"));
        Files.deleteIfExists(resourcePath);
    }
}
