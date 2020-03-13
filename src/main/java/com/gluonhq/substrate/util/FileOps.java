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

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.URL;
import java.nio.channels.Channels;
import java.nio.channels.FileChannel;
import java.nio.channels.ReadableByteChannel;
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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipInputStream;

import static java.nio.file.StandardCopyOption.REPLACE_EXISTING;

public class FileOps {

    /**
     * Find the file with the provided name in the provided directory.
     * @param workDir
     * @param name
     * @return Optional path of the file
     * @throws IOException
     */
    public static Optional<Path> findFile(Path workDir, String name) throws IOException {
        Path[] paths = {null};
        SimpleFileVisitor<Path> visitor = new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                Path fileName = file.getFileName();
                if (fileName != null && fileName.toString().endsWith(name)) {
                    paths[0] = file;
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
        return Optional.ofNullable(paths[0]);
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

    /**
     * Copies resource into destination path
     * @param resource
     * @param destination
     * @return destination path
     * @throws IOException
     */
    public static Path copyResource(String resource, Path destination) throws IOException {
        InputStream is = resourceAsStream(resource);
        if (is == null) {
            throw new IOException("Could not copy resource named " + resource + ", as it doesn't exist");
        }
        return copyStream(is, destination);
    }

    /**
     * Copies source stream to a destination path
     * @param sourceStream
     * @param destination
     * @return destination path
     * @throws IOException
     */
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

    /**
     * Represents resource as InputStream
     * @param res resource
     * @return InputStream representing given resource
     * @throws IOException
     */
    public static InputStream resourceAsStream(String res) throws IOException {
        String actualResource = Objects.requireNonNull(res).startsWith("/") ? res : "/" + res;
        Logger.logDebug("Looking for resource: " + res);
        InputStream answer = SubstrateDispatcher.class.getResourceAsStream(actualResource);
        if (answer == null) {
            throw new IOException("Resource " + actualResource + " not found");
        }
        return answer;
    }

    /**
     * Copies given resource to temp directory
     * @param resource
     * @return path of the copied resource
     * @throws IOException
     */
    public static Path copyResourceToTmp(String resource) throws IOException {
        String tmpDir = System.getProperty("java.io.tmpdir");
        Path target = Paths.get(tmpDir,resource);
        return copyResource(resource, target);
    }

    /**
     * Copies source to destination, ensuring that destination exists
     * @param source source path
     * @param destination destination path
     * @return destination path
     */
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

        Files.walkFileTree(start, new HashSet<>(), Integer.MAX_VALUE, new FileVisitor<>() {
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
        if (Files.isDirectory(source)) {
            List<Path> fileNames = Files.list(source)
                    .map(Path::getFileName)
                    .collect(Collectors.toList());
            for (Path fileName : fileNames) {
                copyDirectory(source.resolve(fileName), destination.resolve(fileName));
            }
        }
        else
            copyFile(source, destination);
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
     * Writes list of lines to a text file
     * @param file Path to output file
     * @param lines A list of lines
     * @throws IOException
     */
    public static void writeFileLines(Path file, List<String> lines) throws IOException {
        FileWriter writer = new FileWriter(file.toString()); 
        for(String str: lines) {
          writer.write(str + System.lineSeparator());
        }
        writer.close();
    }

    /**
     * Replaces all occurences of one parameter in file with another
     * @param file Path to file
     * @param original String which should be replaced
     * @param replacement Replacement string
     * @throws IOException
     */
    public static void replaceInFile(Path file, String original, String replacement) throws IOException {
        InputStream inputStream = Files.newInputStream(file);
        List<String> lines = readFileLines(inputStream);
        for (int i=0; i<lines.size(); i++)
            lines.set(i, lines.get(i).replaceAll(original, replacement));
        writeFileLines(file, lines);
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

    /**
     * Calculates checksum of the file content.
     * Currently uses MD5 as it is faster them SHA
     * @param file file for which checksum is calculated
     * @return checksum as a string
     */
    public static String calculateCheckSum(File file) {
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

    /**
     * Extracts the files that match a given extension found in a jar to a target patch,
     * providing that the file passes a given filter, and it doesn't exist yet in the target path
     *
     * @param extension the extension of the files in the jar that will be extracted
     * @param sourceJar the path to the jar that will be inspected
     * @param target the path of the folder where the files will be extracted
     * @param filter a predicate that the files in the jar should match.
     * @throws IOException
     */
    public static void extractFilesFromJar(String extension, Path sourceJar, Path target, Predicate<Path> filter) throws IOException {
        if (!Files.exists(sourceJar)) {
            return;
        }
        if (!Files.exists(target)) {
            Files.createDirectories(target);
        }
        ZipFile zf = new ZipFile(sourceJar.toFile());
        List<? extends ZipEntry> entries = zf.stream()
                .filter(ze -> ze.getName().endsWith(extension))
                .collect(Collectors.toList());
        if (entries.isEmpty()) {
            return;
        }

        List<String> uniqueObjectFileNames = Files.list(target)
                .map(p -> p.getFileName().toString())
                .collect(Collectors.toList());

        for (ZipEntry ze : entries) {
            String uniqueName = new File(ze.getName()).getName();
            if (!uniqueObjectFileNames.contains(uniqueName)) {
                Path filePath = FileOps.copyStream(zf.getInputStream(ze), target.resolve(uniqueName));
                if (filter == null || filter.test(filePath)) {
                    uniqueObjectFileNames.add(uniqueName);
                } else {
                    Logger.logDebug("File not copied, doesn't pass filter: " + uniqueName);
                    Files.delete(filePath);
                }
            }
        }
    }

    /**
     * Downloads a file from a given URL (non null) into a given path (non null)
     * @param fileUrl the URL of the file
     * @param filePath the absolute path of the file where the remote file be downloaded into
     * @throws IOException
     */
    public static void downloadFile(URL fileUrl, Path filePath) throws IOException {
        Objects.requireNonNull(fileUrl);
        Objects.requireNonNull(filePath);
        try (ReadableByteChannel readableByteChannel = Channels.newChannel(fileUrl.openStream());
             FileOutputStream fileOutputStream = new FileOutputStream(filePath.toFile());
             FileChannel fileChannel = fileOutputStream.getChannel()) {
            fileChannel.transferFrom(readableByteChannel, 0, Long.MAX_VALUE);
        } catch (IOException e) {
            throw new IOException("Error downloading from " + fileUrl + " into " + filePath + ": " + e.getMessage() + ", " + Arrays.toString(e.getSuppressed()));
        }
    }

    /**
     * Extracts the files from a given zip file into a target folder, and returns a map
     * with the names of the files and their checksum values.
     * In the case that the file is not a valid zip, the returned map will be empty.
     * @param sourceZip the path of a non null zip file
     * @param targetDir the path of a folder where the zip file will be extracted
     * @return a map with the file names and their checksum values
     * @throws IOException
     */
    public static Map<String, String> unzipFile(Path sourceZip, Path targetDir) throws IOException {
        Objects.requireNonNull(sourceZip);
        Objects.requireNonNull(targetDir);
        if (!Files.exists(sourceZip)) {
            throw new IOException("Error: " + sourceZip + " does not exist");
        }
        if (Files.isRegularFile(targetDir)) {
            throw new IOException("Error: " + targetDir + " is not a directory");
        }
        if (!Files.exists(targetDir)) {
            Files.createDirectories(targetDir);
        }
        Map<String, String> hashes = new HashMap<>();
        try (ZipInputStream zis = new ZipInputStream(new FileInputStream(sourceZip.toFile()))) {
            ZipEntry zipEntry;
            while ((zipEntry = zis.getNextEntry()) != null) {
                Path destPath = targetDir.resolve(zipEntry.getName());
                if (zipEntry.isDirectory()) {
                    if (!Files.exists(destPath)) {
                        Files.createDirectories(destPath);
                    }
                } else {
                    byte[] buffer = new byte[1024];
                    try (FileOutputStream fos = new FileOutputStream(destPath.toFile())) {
                        int len;
                        while ((len = zis.read(buffer)) > 0) {
                            fos.write(buffer, 0, len);
                        }
                    }
                    hashes.put(destPath.getFileName().toString(), calculateCheckSum(destPath.toFile()));
                }
                zis.closeEntry();
            }
        } catch (IOException e) {
            throw new IOException("Error unzipping from " + sourceZip + "into " + targetDir + ": " + e.getMessage() + ", " + Arrays.toString(e.getSuppressed()));
        }
        return hashes;
    }

    /**
     * Downloads a zip file from the specified sourceUrl into the destPath where a file
     * named fileName will be created.
     * Once the zip file is downloaded, it will be unpacked into the location that starts at
     * the destPath, and is resolved under destPath/dirName/level1/...
     * A file with the checksums of all the files in the zip will be generated with name
     * "dirName-levelN.md5" under the final path: destPath/dirName/.../levelN/subDir-levelN.md5, or
     * "dirName.md5" under the final path: destPath/dirName/subDir.md5, if levels are not provided.
     *
     * @param sourceUrl a string with the location of a zip file, e.g. https://download2.gluonhq.com/substrate/bar/foo.zip
     * @param destPath the path where the file zip file will be downloaded, e.g. /opt/bar
     * @param fileName the name of the file that will be downloaded, e.g. foo.zip
     * @param dirName the folder under destPath, not null, e.g. foo1
     * @param levels an optional number of folders under dirName
     *               (null or empty values will be skipped),
     *               e.g. foo2, foo3, so the zip file will be downloaded into /opt/bar/foo.zip
     *              The contents of this zip file will be installed into
     *              /opt/bar/foo1/foo2/foo3, and also the file
     *               /opt/bar/foo1/foo2/foo3/foo1-foo3.md5 will be created
     * @throws IOException
     */
    public static void downloadAndUnzip(String sourceUrl, Path destPath, String fileName,
                                        String dirName, String... levels) throws IOException {
        Objects.requireNonNull(dirName);

        String md5name = levels == null ? dirName + ".md5" :
                dirName + "-" + Arrays.asList(levels).get(levels.length - 1) + ".md5";
        Path zipPath = destPath.resolve(fileName);
        Logger.logDebug("Processing zip file: url = " + sourceUrl +
                ", zip = " + zipPath +
                ", subDir = " + dirName +
                ", levels = " + Arrays.asList(levels) +
                ", md5 = " + md5name);

        // 1. Download zip from urlZip into zipPath
        FileOps.downloadFile(new URL(sourceUrl), zipPath);

        // 2. Set path where zip should be extracted
        Path zipDir = destPath.resolve(dirName);
        for (String level : levels) {
            if (level != null && !level.isEmpty()) {
                zipDir = zipDir.resolve(level);
            }
        }
        Files.createDirectories(zipDir);

        // 3. Extract zip from zipPath into zipDir
        Map<String, String> hashes = FileOps.unzipFile(zipPath, zipDir);

        // 4. Write hashes file into zipDir
        try (FileOutputStream fos =
                     new FileOutputStream(zipDir.resolve(md5name).toFile());
             ObjectOutputStream oos = new ObjectOutputStream(fos)) {
            oos.writeObject(hashes);
        }
    }
}
