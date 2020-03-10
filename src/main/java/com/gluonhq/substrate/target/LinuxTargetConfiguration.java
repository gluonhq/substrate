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
package com.gluonhq.substrate.target;

import com.gluonhq.substrate.Constants;
import com.gluonhq.substrate.model.ClassPath;
import com.gluonhq.substrate.model.InternalProjectConfiguration;
import com.gluonhq.substrate.model.ProcessPaths;
import com.gluonhq.substrate.util.FileOps;
import com.gluonhq.substrate.util.Logger;
import com.gluonhq.substrate.util.Version;
import com.gluonhq.substrate.util.VersionParser;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import java.util.stream.Collectors;

public class LinuxTargetConfiguration extends PosixTargetConfiguration {

    private static final Version COMPILER_MINIMAL_VERSION = new Version(6);
    private static final Version LINKER_MINIMAL_VERSION = new Version(2, 26);

    private static final List<String> linuxLibs = Arrays.asList("z", "dl", "stdc++", "pthread");

    private static final List<String> staticJavaLibs = Arrays.asList(
            "java", "nio", "zip", "net", "prefs", "jvm", "strictmath", "j2pkcs11", "sunec", "extnet", "libchelper"
    );

    private static final List<String> linuxfxlibs = Arrays.asList( "-Wl,--whole-archive",
            "-lprism_es2", "-lglass", "-lglassgtk3", "-ljavafx_font",
            "-ljavafx_font_freetype", "-ljavafx_font_pango", "-ljavafx_iio",
            "-ljfxmedia", "-lfxplugins", "-lavplugin",
            "-Wl,--no-whole-archive", "-lGL", "-lX11","-lgtk-3", "-lgdk-3",
            "-lpangocairo-1.0", "-lpango-1.0", "-latk-1.0",
            "-lcairo-gobject", "-lcairo", "-lgdk_pixbuf-2.0",
            "-lgio-2.0", "-lgobject-2.0", "-lglib-2.0", "-lfreetype",
            "-lpangoft2-1.0", "-lgstreamer-lite",
            "-lgthread-2.0", "-lstdc++", "-lz", "-lXtst",
            "-lavcodec", "-lavformat", "-lavutil",
            "-lasound", "-lm", "-lgmodule-2.0"
    );

    private String[] capFiles = {"AArch64LibCHelperDirectives.cap",
            "AMD64LibCHelperDirectives.cap", "BuiltinDirectives.cap",
            "JNIHeaderDirectives.cap", "LibFFIHeaderDirectives.cap",
            "LLVMDirectives.cap", "PosixDirectives.cap"};
    private final String capLocation= "/native/linux-aarch64/cap/";

    private static final List<String> linuxfxSWlibs = Arrays.asList(
            "-Wl,--whole-archive", "-lprism_sw", "-Wl,--no-whole-archive", "-lm");

    public LinuxTargetConfiguration( ProcessPaths paths, InternalProjectConfiguration configuration ) {
        super(paths, configuration);
    }

    @Override
    public boolean compile() throws IOException, InterruptedException {
        if (projectConfiguration.getTargetTriplet().getArch().equals(Constants.ARCH_AARCH64)) {
            projectConfiguration.setUsePrismSW(true); // for now, when compiling for AArch64, we should not assume hw rendering
        }
        return super.compile();
    }

    @Override
    public boolean link() throws IOException, InterruptedException {
        checkCompiler();
        checkLinker();
        return super.link();
    }

    @Override
    List<String> getTargetSpecificLinkLibraries() {
        List<String> targetLibraries = new ArrayList<>();

        targetLibraries.add("-Wl,-Bstatic");
        targetLibraries.addAll(asListOfLibraryLinkFlags(staticJavaLibs));

        targetLibraries.add("-Wl,-Bdynamic");
        targetLibraries.addAll(asListOfLibraryLinkFlags(linuxLibs));

        return targetLibraries;
    }

    @Override
    List<String> getTargetSpecificLinkFlags(boolean useJavaFX, boolean usePrismSW) {
        List<String> answer = new LinkedList<>();
        answer.add("-rdynamic");
        if (!useJavaFX) return answer;

        ProcessBuilder process = new ProcessBuilder("pkg-config", "--libs", "gtk+-3.0", "gthread-2.0", "xtst");
        process.redirectErrorStream(true);
        try {
            Process start = process.start();
            InputStream is = start.getInputStream();
            start.waitFor();
            BufferedReader br = new BufferedReader(new InputStreamReader(is));
            String line;
            while ((line = br.readLine()) != null) {
                Logger.logInfo("[SUB] " + line);
            }
        } catch (IOException | InterruptedException e) {
            e.printStackTrace();
        }

        answer.addAll(linuxfxlibs);
        if (usePrismSW) {
            answer.addAll(linuxfxSWlibs);
        }
        return answer;
    }

    @Override
    List<String> getTargetSpecificNativeLibsFlags(Path libPath, List<String> libs) {
        List<String> linkFlags = new ArrayList<>();
        linkFlags.add("-Wl,--whole-archive");
        linkFlags.addAll(libs.stream()
                .map(s -> libPath.resolve(s).toString())
                .collect(Collectors.toList()));
        linkFlags.add("-Wl,--no-whole-archive");
        return linkFlags;
    }

    @Override
    List<String> getTargetSpecificAOTCompileFlags() throws IOException {
        if (!crossCompile) {
            return super.getTargetSpecificAOTCompileFlags();
        }

        return Arrays.asList("-H:CompilerBackend=" + Constants.BACKEND_LLVM,
                "-H:-SpawnIsolates",
                "-Dsvm.targetArch=" + projectConfiguration.getTargetTriplet().getArch(),
                "-H:+UseOnlyWritableBootImageHeap",
                "-H:+UseCAPCache",
                "-H:CAPCacheDir="+ getCapCacheDir().toAbsolutePath().toString(),
                "-H:CustomLD=aarch64-linux-gnu-ld");
    }

    @Override
    String processClassPath(String classPath) throws IOException {
        if (!projectConfiguration.isUseJavaFX()) {
            return classPath;
        }

        return new ClassPath(classPath).mapWithLibs(
                fileDeps.getJavaFXSDKLibsPath(), "javafx-graphics", "javafx-base", "javafx-controls", "javafx-media");

    }

    @Override
    protected List<String> getTargetSpecificCCompileFlags() {
        List<String> flags = Arrays.asList("-I" + 
            projectConfiguration.getGraalPath().resolve("include").toString(),
            "-I" + projectConfiguration.getGraalPath().resolve("include").resolve("linux").toString()
            );
            
        if (projectConfiguration.getTargetTriplet().getArch().equals(Constants.ARCH_AARCH64)) {
            flags.add("-DAARCH64");
        }
        return flags;
    }

   /*
    * Copies the .cap files from the jar resource and store them in
    * a directory. Return that directory
    */
    private Path getCapCacheDir() throws IOException {
        Path capPath = paths.getGvmPath().resolve("capcache");
        if (!Files.exists(capPath)) {
            Files.createDirectory(capPath);
        }
        for (String cap : capFiles) {
            FileOps.copyResource(capLocation+cap, capPath.resolve(cap));
        }
        return capPath;
    }
    private void checkCompiler() throws IOException, InterruptedException {
        validateVersion(new String[] { "gcc", "--version" }, "compiler", COMPILER_MINIMAL_VERSION);
    }

    private void checkLinker() throws InterruptedException, IOException {
        validateVersion(new String[] { "ld", "--version" }, "linker", LINKER_MINIMAL_VERSION);
    }

    private void validateVersion(String[] processCommand, String processName, Version minimalVersion) throws InterruptedException, IOException {
        String versionLine = getFirstLineFromProcess(processCommand);
        if (versionLine == null) {
            System.err.println(
                    "WARNING: we were unable to parse the version of your " + processName + ".\n" +
                    "         The build will continue, but please bare in mind that the minimal required version for " + processCommand[0] + " is " + minimalVersion + ".");
        } else {
            VersionParser versionParser = new VersionParser();
            Version version = versionParser.parseVersion(versionLine);
            if (version == null) {
                System.err.println(
                        "WARNING: we were unable to parse the version of your " + processName + ": \"" + versionLine + "\".\n" +
                        "         The build will continue, but please bare in mind that the minimal required version for " + processCommand[0] + " is \"" + minimalVersion + "\".");
            } else if (version.compareTo(minimalVersion) < 0) {
                System.err.println(
                        "ERROR: The version of your " + processName + ": \"" + version + "\", does not match the minimal required version: \"" + minimalVersion + "\".\n" +
                        "       Please check https://docs.gluonhq.com/client/#_linux and make sure that your environment meets the requirements.");
                throw new IllegalArgumentException(processCommand[0] + " version too old");
            }
        }
    }

    private String getFirstLineFromProcess(String...command) throws InterruptedException, IOException {
        ProcessBuilder compiler = new ProcessBuilder(command);
        compiler.redirectErrorStream(true);

        Process compilerProcess = compiler.start();
        InputStream processInputStream = compilerProcess.getInputStream();
        compilerProcess.waitFor();

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(processInputStream))) {
            return reader.readLine();
        }
    }

    private List<String> asListOfLibraryLinkFlags(List<String> libraries) {
        return libraries.stream()
                .map(library -> "-l" + library)
                .collect(Collectors.toList());
    }

    @Override
    String getCompiler() {
        if (!crossCompile) {
            return super.getCompiler();
        }
        return "aarch64-linux-gnu-gcc";
    }

    @Override
    String getLinker() {
        if (!crossCompile) {
            return super.getLinker();
        }
        return "aarch64-linux-gnu-gcc";
    }


}
