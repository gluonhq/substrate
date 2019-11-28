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

import java.io.*;
import java.nio.file.FileVisitOption;
import java.nio.file.FileVisitResult;
import java.nio.file.FileVisitor;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;
import java.util.function.Predicate;
import java.util.stream.Collectors;

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
            throw new IOException("Could not copy resource named " + resource + ", as it doesn't exist");
        }
        return copyStream(is, destination);
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

    public static InputStream resourceAsStream(String res) throws IOException {
        String actualResource = Objects.requireNonNull(res).startsWith("/") ? res : "/" + res;
        Logger.logDebug("Looking for resource: " + res);
        InputStream answer = SubstrateDispatcher.class.getResourceAsStream(actualResource);
        if (answer == null) {
            throw new IOException("Resource " + actualResource + " not found");
        }
        return answer;
    }

    public static Path copyResourceToTmp(String resource) throws IOException {
        String tmpDir = System.getProperty("java.io.tmpdir");
        Path target = Paths.get(tmpDir,resource);
        return copyResource(resource, target);
    }

    // Copies source to destination, ensuring that destination exists
    public static Path copyFile(Path source, Path destination)  {
        try {
            Files.createDirectories(destination.getParent());
            Files.copy(source, destination,  REPLACE_EXISTING);
            Logger.logDebug("Copied resource " + source + " to " + destination);
        } catch (IOException ex) {
            Logger.logFatal(ex, "Failed copying " + source + " to " + destination + ": " + ex);
        }
        return destination;
    }

    /**
     * Deletes recursively a directory and all its content
     * @param start the top level directory to be removed
     * @throws IOException if a file or directory can't be deleted
     */
    public static void deleteDirectory(Path start) throws IOException {
        if (start == null || !Files.exists(start)) {
            throw new RuntimeException("Error: path " + start + " doesn't exist");
        }

        Files.walkFileTree(start, new HashSet(), Integer.MAX_VALUE, new FileVisitor<>() {
            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                Files.delete(file);
                return FileVisitResult.CONTINUE;

            }

            @Override
            public FileVisitResult visitFileFailed(Path file, IOException exc) throws IOException {
                return FileVisitResult.TERMINATE;

            }

            @Override
            public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
                Files.delete(dir);
                return FileVisitResult.CONTINUE;
            }
        });
    }

    /**
     * Copies recursively a directory and all its content
     * @param source path of the directory to be copied
     * @param destination path where the directory will be copied
     * @throws IOException if an exception happens when listing the content
     */
    public static void copyDirectory(Path source, Path destination) throws IOException {
        copyFile(source, destination);
        if (Files.isDirectory(source)) {
            List<Path> fileNames = Files.list(source)
                    .map(Path::getFileName)
                    .collect(Collectors.toList());
            for (Path fileName : fileNames) {
                copyDirectory(source.resolve(fileName), destination.resolve(fileName));
            }
        }
    }

    /**
     * Reads a file from an inputStream and returns a list with its lines
     * @param inputStream The input stream of bytes
     * @return a list of strings with the lines read from the input stream
     * @throws IOException
     */
    public static List<String> readFileLines(InputStream inputStream) throws IOException {
        return readFileLines(inputStream, null);
    }

    /**
     * Reads a file from an inputStream and returns a list with its lines
     * @param inputStream The input stream of bytes
     * @param predicate A predicate of content found in the lines
     * @return a list of strings with the lines read from the input stream,
     *          that match the predicate
     * @throws IOException
     */
    public static List<String> readFileLines(InputStream inputStream, Predicate<String> predicate) throws IOException {
        List<String> lines = new ArrayList<>();
        try (BufferedReader br = new BufferedReader(new InputStreamReader(inputStream))) {
            String line;
            while ((line = br.readLine()) != null) {
                if (predicate == null || predicate.test(line)) {
                    lines.add(line);
                }
            }
        }
        return lines;
    }

    /**
     * Return the hashmap associated with this nameFile.
     * If a file named <code>nameFile</code> exists, and it contains  a serialized version of a Map, this
     * Map will be returned.
     * If the file doesn't exist or is corrupt, this method returns null
     * @param nameFile
     * @return the Map contained in the file named nameFile, or null in all other cases.
     */
    static Map<String, String> getHashMap(String nameFile) {
        Map<String, String> hashes = null;
        if (!Files.exists(Paths.get(nameFile))) {
            return null;
        }
        try (FileInputStream fis = new FileInputStream(new File(nameFile));
             ObjectInputStream ois = new ObjectInputStream(fis)) {
            hashes = (Map<String, String>) ois.readObject();
        } catch (IOException | ClassNotFoundException e) {
            Logger.logDebug("Exception trying to get hashmap for "+nameFile+": "+e);
            return null;
        }
        return hashes;
    }

    static String calculateCheckSum(File file) {
        try {
            // not looking for security, just a checksum. MD5 should be faster than SHA
            MessageDigest md5 = MessageDigest.getInstance("MD5");
            try (final InputStream stream = new FileInputStream(file);
                 final DigestInputStream dis = new DigestInputStream(stream, md5)) {
                md5.reset();
                byte[] buffer = new byte[4096];
                while (dis.read(buffer) != -1) { /* empty loop body is intentional */ }
                return Arrays.toString(md5.digest());
            }

        } catch (IllegalArgumentException | NoSuchAlgorithmException | IOException | SecurityException e) {
            return "";
        }
    }
}
