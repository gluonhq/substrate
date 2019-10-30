/*
 * Copyright (c) 2019, Gluon
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

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class FileOpsTests {


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

}
