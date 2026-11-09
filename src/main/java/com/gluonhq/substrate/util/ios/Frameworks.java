/*
 * Copyright (c) 2026, Gluon
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
package com.gluonhq.substrate.util.ios;

import com.gluonhq.substrate.Constants;
import com.gluonhq.substrate.model.ClassPath;
import com.gluonhq.substrate.util.FileOps;
import com.gluonhq.substrate.util.Logger;
import com.gluonhq.substrate.util.ProcessRunner;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Resolves third-party iOS native dependencies (dynamic .xcframework bundles,
 * that Gluon Attach modules or other projects declare through a manifest at
 * {@code META-INF/substrate/ios/ios-frameworks.txt} inside their classpath jars.
 * <p>
 * This is the iOS counterpart of {@code META-INF/substrate/dalvik/android-dependencies.txt}.
 * The referenced SDK is not vendored anywhere: it is downloaded on demand from the
 * {@code sdk-zip} URL declared in the manifest and cached under
 * {@code ~/.gluon/substrate/ios-sdk}.
 * <p>
 * Supported manifest entries (one per line, {@code #} and blank lines are ignored):
 * <ul>
 *     <li>{@code sdk-zip <url>} download this zip and search it for the embed xcframeworks</li>
 *     <li>{@code embed <Name>.xcframework} make this framework's slice available to the linker
 *         ({@code -F <slice>} + {@code -Wl,-framework,<Name>}). If the framework's binary is a
 *         <em>static</em> archive its code is linked
 *         directly into the app, so it is <em>not</em> copied into {@code <App>.app/Frameworks};
 *         if it is a <em>dynamic</em> library it is copied there, code-signed, and reached at
 *         runtime through an {@code @executable_path/Frameworks} rpath. Static Swift frameworks
 *         additionally pull in the Swift runtime/compatibility libraries.</li>
 *     <li>{@code framework <Name>} add {@code -Wl,-framework,<Name>} (system framework)</li>
 *     <li>{@code weak-framework <Name>} add {@code -Wl,-weak_framework,<Name>}</li>
 *     <li>{@code library <name>} add {@code -l<name>}</li>
 * </ul>
 */
public class Frameworks {

    private static final String MANIFEST = "META-INF/substrate/ios/ios-frameworks.txt";
    private static final String CACHE_DIR = "ios-sdk";
    private static final String CACHE_PROPERTY = "substrate.ios.sdk.cache";
    private static final String EXTRACTED_MARKER = ".extracted";

    private static final String SLICE_DEVICE = "ios-arm64";
    private static final String SLICE_SIMULATOR = "ios-arm64_x86_64-simulator";

    private static final String FRAMEWORKS_RPATH = "@executable_path/Frameworks";
    private static final String SWIFT_RPATH = "/usr/lib/swift";

    private final String classpath;

    private boolean parsed = false;
    private final Set<String> sdkZipUrls = new LinkedHashSet<>();
    private final List<String> embeds = new ArrayList<>();
    private final List<String> systemFrameworks = new ArrayList<>();
    private final List<String> weakFrameworks = new ArrayList<>();
    private final List<String> libraries = new ArrayList<>();

    public Frameworks(String classpath) {
        this.classpath = classpath;
    }

    /**
     * @return true if any classpath jar declares an iOS frameworks manifest.
     */
    public boolean hasFrameworks() throws IOException, InterruptedException {
        parse();
        return !embeds.isEmpty() || !systemFrameworks.isEmpty()
                || !weakFrameworks.isEmpty() || !libraries.isEmpty();
    }

    /**
     * Returns the clang/linker flags required to link the application against the declared
     * frameworks and libraries. Downloads and caches the SDK zip(s) if needed.
     *
     * @param simulator whether the target is the iOS simulator
     */
    public List<String> getLinkFlags(boolean simulator) throws IOException, InterruptedException {
        parse();
        if (!hasFrameworks()) {
            return List.of();
        }
        ensureSdksDownloaded();

        List<String> flags = new ArrayList<>();
        String slice = simulator ? SLICE_SIMULATOR : SLICE_DEVICE;
        Set<String> searchPaths = new LinkedHashSet<>();
        boolean anyDynamic = false;
        boolean anyStatic = false;
        for (String embed : embeds) {
            Path xcframework = findXcframework(embed);
            if (xcframework == null) {
                Logger.logSevere("iOS framework " + embed + " not found in the downloaded SDK");
                continue;
            }
            Path sliceDir = xcframework.resolve(slice);
            if (!Files.exists(sliceDir)) {
                Logger.logSevere(embed + " has no slice " + slice + ", skipping");
                continue;
            }
            searchPaths.add(sliceDir.toString());
            flags.add("-Wl,-framework," + frameworkName(embed));
            if (isStaticFramework(sliceDir, frameworkName(embed))) {
                anyStatic = true;
            } else {
                anyDynamic = true;
            }
        }
        searchPaths.forEach(p -> flags.add("-F" + p));
        systemFrameworks.forEach(f -> flags.add("-Wl,-framework," + f));
        weakFrameworks.forEach(f -> flags.add("-Wl,-weak_framework," + f));
        libraries.forEach(l -> flags.add("-l" + l));
        if (anyDynamic) {
            // Dynamic frameworks are loaded from <App>.app/Frameworks at runtime.
            flags.add("-Wl,-rpath," + FRAMEWORKS_RPATH);
        }
        if (anyStatic) {
            // Static (and partly-Swift) frameworks are linked into the main binary and need the
            // Swift runtime / backward-compatibility libraries that the objects auto-link against.
            flags.addAll(swiftRuntimeFlags(simulator));
        }
        return flags;
    }

    /**
     * Copies each declared {@code embed} framework (proper slice) into {@code <appPath>/Frameworks},
     * stripping headers and module maps, so it can be loaded at runtime through the
     * {@code @executable_path/Frameworks} rpath.
     *
     * @param appPath the {@code .app} bundle path
     * @param simulator whether the target is the iOS simulator
     * @return the list of embedded {@code .framework} paths (to be code-signed)
     */
    public List<Path> embedFrameworks(Path appPath, boolean simulator) throws IOException, InterruptedException {
        parse();
        if (embeds.isEmpty()) {
            return List.of();
        }
        ensureSdksDownloaded();

        String slice = simulator ? SLICE_SIMULATOR : SLICE_DEVICE;
        Path frameworksDir = appPath.resolve("Frameworks");
        Files.createDirectories(frameworksDir);

        List<Path> embedded = new ArrayList<>();
        for (String embed : embeds) {
            Path xcframework = findXcframework(embed);
            if (xcframework == null) {
                throw new IOException("iOS framework " + embed + " not found in the downloaded SDK");
            }
            Path sliceDir = xcframework.resolve(slice);
            if (isStaticFramework(sliceDir, frameworkName(embed))) {
                // Static frameworks are linked into the main binary; nothing to embed or sign.
                Logger.logDebug("Skipping embed of static framework " + embed);
                continue;
            }
            Path source = sliceDir.resolve(frameworkName(embed) + ".framework");
            if (!Files.exists(source)) {
                throw new IOException("Framework not found: " + source);
            }
            Path target = frameworksDir.resolve(source.getFileName());
            if (Files.exists(target)) {
                FileOps.deleteDirectory(target);
            }
            FileOps.copyDirectory(source, target);
            stripDevelopmentArtifacts(target);
            Logger.logDebug("Embedded framework " + target);
            embedded.add(target);
        }
        return embedded;
    }

    private void parse() throws IOException, InterruptedException {
        if (parsed) {
            return;
        }
        parsed = true;
        List<File> jars = new ClassPath(classpath).getJars(true);
        for (File jar : jars) {
            if (!jar.exists()) {
                continue;
            }
            try (ZipFile zip = new ZipFile(jar)) {
                for (Enumeration<? extends ZipEntry> e = zip.entries(); e.hasMoreElements(); ) {
                    ZipEntry entry = e.nextElement();
                    if (!entry.isDirectory() && MANIFEST.equals(entry.getName())) {
                        Logger.logDebug("Reading iOS frameworks manifest from " + jar.getName());
                        for (String line : FileOps.readFileLines(zip.getInputStream(entry), l -> true)) {
                            parseLine(line);
                        }
                    }
                }
            }
        }
    }

    private void parseLine(String rawLine) {
        String line = rawLine.trim();
        if (line.isEmpty() || line.startsWith("#")) {
            return;
        }
        String[] tokens = line.split("\\s+", 2);
        if (tokens.length < 2) {
            return;
        }
        String value = tokens[1].trim();
        switch (tokens[0]) {
            case "sdk-zip":
                sdkZipUrls.add(value);
                break;
            case "embed":
                if (!embeds.contains(value)) {
                    embeds.add(value);
                }
                break;
            case "framework":
                if (!systemFrameworks.contains(value)) {
                    systemFrameworks.add(value);
                }
                break;
            case "weak-framework":
                if (!weakFrameworks.contains(value)) {
                    weakFrameworks.add(value);
                }
                break;
            case "library":
                if (!libraries.contains(value)) {
                    libraries.add(value);
                }
                break;
            default:
                Logger.logDebug("Ignoring unknown iOS frameworks manifest entry: " + line);
        }
    }

    private void ensureSdksDownloaded() throws IOException {
        Path cacheRoot = getCacheRoot();
        Files.createDirectories(cacheRoot);
        for (String url : sdkZipUrls) {
            String fileName = url.substring(url.lastIndexOf('/') + 1);
            Path extractDir = cacheRoot.resolve(fileName.replaceFirst("\\.zip$", ""));
            Path marker = extractDir.resolve(EXTRACTED_MARKER);
            if (Files.exists(marker)) {
                continue;
            }
            Path zip = cacheRoot.resolve(fileName);
            if (!Files.exists(zip)) {
                Logger.logInfo("Downloading iOS SDK: " + url);
                FileOps.downloadFile(new URL(url), zip);
            }
            Logger.logInfo("Extracting iOS SDK: " + fileName);
            FileOps.unzipFile(zip, extractDir);
            Files.createFile(marker);
        }
    }

    private Path findXcframework(String name) throws IOException {
        Path cacheRoot = getCacheRoot();
        if (!Files.exists(cacheRoot)) {
            return null;
        }
        try (Stream<Path> stream = Files.walk(cacheRoot)) {
            return stream
                    .filter(Files::isDirectory)
                    .filter(p -> name.equals(p.getFileName().toString()))
                    .findFirst()
                    .orElse(null);
        }
    }

    private static Path getCacheRoot() {
        String override = System.getProperty(CACHE_PROPERTY);
        if (override != null && !override.isEmpty()) {
            return Path.of(override);
        }
        return Constants.USER_SUBSTRATE_PATH.resolve(CACHE_DIR);
    }

    /**
     * Removes development-only artifacts (headers, module maps and the original code signature)
     * from an embedded framework. They are not needed at runtime and can lead to App Store
     * rejections; the framework is re-signed after this step.
     */
    private void stripDevelopmentArtifacts(Path framework) throws IOException {
        for (String dir : List.of("Headers", "PrivateHeaders", "Modules", "_CodeSignature")) {
            Path path = framework.resolve(dir);
            if (Files.exists(path)) {
                FileOps.deleteDirectory(path);
            }
        }
    }

    /**
     * @return true if the framework binary in {@code sliceDir} is a static archive ({@code ar
     * archive}) rather than a dynamic library. Static frameworks
     * are linked into the main binary and must not be embedded into {@code <App>.app/Frameworks}.
     */
    private boolean isStaticFramework(Path sliceDir, String name) throws IOException, InterruptedException {
        Path binary = sliceDir.resolve(name + ".framework").resolve(name);
        if (!Files.exists(binary)) {
            return false;
        }
        String description = ProcessRunner.runProcessForSingleOutput("file", "file", "-b", binary.toString());
        return description != null && description.contains("ar archive");
    }

    /**
     * Flags needed to link an application that statically links a Swift framework. The Swift
     * objects auto-link (through {@code LC_LINKER_OPTION} load commands) against the Swift
     * backward-compatibility archives ({@code libswiftCompatibility51.a}, etc.), which live in the
     * active toolchain rather than the SDK, so that directory must be on the library search path.
     * The ABI-stable Swift runtime dylibs are resolved at runtime from {@code /usr/lib/swift}.
     */
    private List<String> swiftRuntimeFlags(boolean simulator) throws IOException, InterruptedException {
        List<String> flags = new ArrayList<>();
        String developerDir = ProcessRunner.runProcessForSingleOutput("xcode-select", "xcode-select", "-p");
        if (developerDir != null && !developerDir.isEmpty()) {
            String platform = simulator ? "iphonesimulator" : "iphoneos";
            Path swiftLibDir = Path.of(developerDir.trim(), "Toolchains", "XcodeDefault.xctoolchain",
                    "usr", "lib", "swift", platform);
            if (Files.exists(swiftLibDir)) {
                flags.add("-L" + swiftLibDir);
            } else {
                Logger.logSevere("Swift toolchain library directory not found: " + swiftLibDir);
            }
        }
        flags.add("-Wl,-rpath," + SWIFT_RPATH);
        return flags;
    }

    private static String frameworkName(String xcframeworkName) {
        return xcframeworkName.replaceFirst("\\.xcframework$", "");
    }
}
