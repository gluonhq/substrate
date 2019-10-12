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

import com.gluonhq.substrate.SubstrateDispatcher;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.FileVisitOption;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import static java.nio.file.StandardCopyOption.REPLACE_EXISTING;

public class FileOps {

    /**
     * Find the file with the provided name in the provided directory.
     * @param workDir
     * @param name
     * @return the path to the file, or <code>null</code> if no such file is found
     * @throws IOException
     */
    public static Path findFile(Path workDir, String name) throws IOException {
        List<Path> answers = new LinkedList<>();
        Optional<Path> objectPath;
        SimpleFileVisitor<Path> visitor = new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                Path fileName = file.getFileName();
                if (fileName != null && fileName.toString().endsWith(name)) {
                    answers.add(file);
                    return FileVisitResult.TERMINATE;
                }
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                return FileVisitResult.CONTINUE;
            }
        };

        Files.walkFileTree(workDir, EnumSet.of(FileVisitOption.FOLLOW_LINKS), Integer.MAX_VALUE, visitor);
        if (answers.size() < 1) return null;
        return answers.get(0);
    }


    /**
     * Recursively delete the directory specified by path, if it exists (otherwise ignore)
     * @param path
     * @throws IOException
     */
    public static void rmdir(Path path) throws IOException {
        if (path.toFile().exists())
            Files.walk(path)
                    .sorted(Comparator.reverseOrder())
                    .map(Path::toFile)
                    .forEach(File::delete);
    }

    public static Path copyResource(String resource, Path destination) throws IOException {
        InputStream is = resourceAsStream(resource);
        if (is == null) {
            throw new IOException("Could not copy resource named "+resource+" as it doesn't exist");
        }
        return copyStream(resourceAsStream(resource), destination);
    }

    public static Path copyStream(InputStream sourceStream, Path destination) throws IOException {
        Path parent = destination.getParent();
        if (!parent.toFile().exists()) {
            Files.createDirectories(parent);
        }
        if (!parent.toFile().isDirectory()) {
            throw new IllegalArgumentException("Could not copy " + destination + " because its parent already exists as a file!");
        } else {
            File f = destination.toFile();
            if (f.exists()) {
                f.delete();
            }
            Files.copy(sourceStream, destination, REPLACE_EXISTING);
        }
        return destination;
    }

    public static InputStream resourceAsStream(String res) {
        String actualResource = Objects.requireNonNull(res).startsWith(File.separator) ? res : File.separator + res;
        InputStream answer = SubstrateDispatcher.class.getResourceAsStream(actualResource);
        return answer;
    }
}
