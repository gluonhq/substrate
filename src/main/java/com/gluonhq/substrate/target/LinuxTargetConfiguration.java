/*
 * Copyright (c) 2019, 2024, Gluon
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
import com.gluonhq.substrate.model.InternalProjectConfiguration;
import com.gluonhq.substrate.model.ProcessPaths;
import com.gluonhq.substrate.util.FileOps;
import com.gluonhq.substrate.util.Logger;
import com.gluonhq.substrate.util.ProcessRunner;
import com.gluonhq.substrate.util.Version;
import com.gluonhq.substrate.util.VersionParser;
import com.gluonhq.substrate.util.linux.LinuxLinkerFlags;

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
import java.util.Objects;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class LinuxTargetConfiguration extends PosixTargetConfiguration {

    private static final Version COMPILER_MINIMAL_VERSION = new Version(6);
    private static final Version LINKER_MINIMAL_VERSION = new Version(2, 26);

    private static final List<String> linuxLibs = Arrays.asList("z", "dl", "stdc++", "pthread");

    private static final List<String> linuxfxlibs = List.of(
            "-lprism_es2", "-lglass", "-lglassgtk3", "-ljavafx_font",
            "-ljavafx_font_freetype", "-ljavafx_font_pango", "-ljavafx_iio"
    );

    // we might rename libprism_es2_monocle.a to libprism_es2.a in the future
    private static final List<String> linuxfxlibsaarch64 = List.of(
            "-lprism_es2_monocle", "-lglass", "-lglassgtk3", "-lglass_monocle", "-ljavafx_font",
            "-ljavafx_font_freetype", "-ljavafx_font_pango", "-ljavafx_iio", "-lgluon_drm"
    );

    private static final List<String> linuxfxMedialibs = List.of(
            "-ljfxmedia", "-lfxplugins", "-lavplugin"
    );
    private static final List<String> linuxfxWeblibs = List.of(
            "-ljfxwebkit",
            "-lWebCore", "-lXMLJava", "-lJavaScriptCore", "-lbmalloc",
            "-licui18n", "-lSqliteJava", "-lXSLTJava", "-lPAL", "-lWebCoreTestSupport",
            "-lWTF", "-licuuc", "-licudata"
    );

    private static final List<String> nativeImageArguments = List.of(
            "--add-exports=org.graalvm.nativeimage.builder/com.oracle.svm.core.jdk=ALL-UNNAMED" // required for the GluonFeature
    );
    private static final List<String> enabledFeatures = List.of(
            "com.gluonhq.substrate.feature.GluonFeature"
    );

    private String[] capFiles = {"AArch64LibCHelperDirectives.cap",
        "AMD64LibCHelperDirectives.cap", "BuiltinDirectives.cap",
        "JNIHeaderDirectives.cap", "LibFFIHeaderDirectives.cap",
        "PosixDirectives.cap"};

    private String llvmCapFile = "LLVMDirectives.cap";

    private final String capLocation = "/native/linux-aarch64/cap/";

    private static final List<String> linuxfxSWlibs = Arrays.asList(
            "-lprism_sw");

    private final String sysroot;

    private final boolean isAarch64;

    public LinuxTargetConfiguration(ProcessPaths paths, InternalProjectConfiguration configuration) throws IOException {
        super(paths, configuration);
        this.isAarch64 = projectConfiguration.getTargetTriplet().getArch().equals(Constants.ARCH_AARCH64);
        sysroot = fileDeps.getSysrootPath().toString();
    }

    @Override
    public boolean compile() throws IOException, InterruptedException {
        if (isAarch64) {
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
    public boolean install() throws IOException, InterruptedException {
        if (!crossCompile) {
            return super.install();
        }

        String remoteHostName = projectConfiguration.getRemoteHostName();
        String remoteDir = projectConfiguration.getRemoteDir();
        if (remoteHostName == null || remoteHostName.isEmpty() || remoteDir == null || remoteDir.isEmpty()) {
            Logger.logSevere("install was called, but remote hostName and/or remote dir were not set");
            return false;
        }

        Path appPath = Objects.requireNonNull(paths.getAppPath(), "Application path can't be null");
        String appName = Objects.requireNonNull(getLinkOutputName(), "Application name can't be null");
        Path app = appPath.resolve(appName);
        if (!Files.exists(app)) {
            throw new IOException("Application not found at path " + app.toString());
        }

        ProcessRunner checkDirRunner = new ProcessRunner("ssh", remoteHostName, "test", "-d", remoteDir, "&&", "echo 1", "||", "echo 0");
        int result = checkDirRunner.runProcess("ssh-check-dir");
        if (result != 0) {
            Logger.logSevere("Error connecting to "+ remoteHostName + ": " + result +
                    "\nMake sure the remote host is reachable and SSH is enabled on it.");
            return false;
        }

        if ("0".equals(checkDirRunner.getLastResponse())) {
            Logger.logDebug("Directory " + remoteDir + " does not exist, creating it");
            ProcessRunner mkdirRunner = new ProcessRunner("ssh", remoteHostName, "mkdir", "-p", remoteDir);
            result = mkdirRunner.runProcess("ssh-mkdir");
            if (result != 0) {
                Logger.logSevere("Error creating directory " + remoteDir + ": " + result);
                return false;
            }
        }

        ProcessRunner scpRunner = new ProcessRunner("scp",
                appPath.resolve(appName).toString(), remoteHostName + ":" + remoteDir);
        scpRunner.setInfo(true);
        scpRunner.setInteractive(true);
        return scpRunner.runProcess("scp") == 0;
    }

    @Override
    public boolean runUntilEnd() throws IOException, InterruptedException {
        if (!crossCompile) {
            return super.runUntilEnd();
        }

        String remoteHostName = projectConfiguration.getRemoteHostName();
        String remoteDir = projectConfiguration.getRemoteDir();
        if (remoteHostName == null || remoteHostName.isEmpty() || remoteDir == null || remoteDir.isEmpty()) {
            Logger.logSevere("install was called, but remote hostName and/or remote dir were not set");
            return false;
        }
        String appName = Objects.requireNonNull(getLinkOutputName(), "Application name can't be null");

        ProcessRunner checkExecRunner = new ProcessRunner("ssh",
                remoteHostName, "test", "-f", Path.of(remoteDir, appName).toString(), "&&", "echo 1", "||", "echo 0");
        int result = checkExecRunner.runProcess("ssh-check-exec");
        if (result != 0) {
            Logger.logSevere("Error connecting to "+ remoteHostName + ": " + result +
                    "\nMake sure the remote host is reachable and SSH is enabled on it.");
            return false;
        }

        if ("0".equals(checkExecRunner.getLastResponse())) {
            throw new IOException("Application " + appName + " not found at " + remoteHostName + ":" + remoteDir);
        }

        Logger.logInfo("Enabling Gluon commercial extensions. Please see https://docs.gluonhq.com/#_legal_notice");
        ProcessRunner execRunner = new ProcessRunner("ssh", "-t", "-t",
                remoteHostName, "env", "ENABLE_GLUON_COMMERCIAL_EXTENSIONS=true",
                "sudo", "-E", "\"" + Path.of(remoteDir, appName).toString() + "\"");
        if (projectConfiguration.getRuntimeArgsList() != null) {
            execRunner.addArgs(projectConfiguration.getRuntimeArgsList());
        }
        execRunner.setInfo(true);
        execRunner.setInteractive(true);
        execRunner.runProcess("ssh-run");
        return true;

    }

    @Override
    List<String> getOtherStaticLibs() {
        return Stream.concat(getStaticJavaLibs().stream().map(lib -> ":lib" + lib + ".a"), linuxLibs.stream())
                .collect(Collectors.toList());
    }

    @Override
    List<String> getAdditionalSourceFiles() {
        if (projectConfiguration.isSharedLibrary() || projectConfiguration.isStaticLibrary()) {
            return List.of();
        }
        return super.getAdditionalSourceFiles();
    }

    @Override
    protected List<String> getNativeImageArguments() {
        return nativeImageArguments;
    }

    @Override
    public List<String> getEnabledFeatures() {
        return enabledFeatures;
    }

    @Override
    List<String> getTargetSpecificLinkFlags(boolean useJavaFX, boolean usePrismSW) throws IOException, InterruptedException {
        List<String> answer = new LinkedList<>();
        answer.add("-Wl,--wrap=pow");
        answer.add("-rdynamic");
        if (projectConfiguration.isSharedLibrary()) {
            answer.add("-shared");
            answer.add("-undefined");
        }
        if (crossCompile) {
            answer.add("-fuse-ld=gold");
            answer.add("--sysroot");
            answer.add(sysroot);
        }
        if (useJavaFX) {

            if (isAarch64) {
                answer.addAll(linuxfxlibsaarch64);
            } else {
                answer.addAll(linuxfxlibs);
            }
            // TODO: Refactor
            if (projectConfiguration.getClasspath().contains("javafx-media")) {
                // for now, we don't have media on AARCH64
                if (!isAarch64) {
                    answer.addAll(linuxfxMedialibs);
                    answer.addAll(LinuxLinkerFlags.getMediaLinkerFlags());
                }
            }
            if (projectConfiguration.hasWeb()) {
                answer.addAll(linuxfxWeblibs);
            }
            answer.addAll(LinuxLinkerFlags.getLinkerFlags());
            if (usePrismSW || crossCompile) {
                answer.addAll(linuxfxSWlibs);
            }
            if (isAarch64) {
                answer.add("-lEGL");
                answer.add("-ldrm");
                answer.add("-lgbm");
            }
        }
        answer.add("-lm");
        answer.add("-ldl");
        return answer;
    }

    @Override
    List<String> getTargetSpecificNativeLibsFlags(Path libPath, List<String> libs) {
        List<String> linkFlags = new ArrayList<>();
        linkFlags.addAll(libs.stream()
                .map(s -> libPath.resolve(s).toString())
                .collect(Collectors.toList()));
        return linkFlags;
    }

    @Override
    List<String> getTargetSpecificAOTCompileFlags() throws IOException {
        if (!crossCompile) {
            return super.getTargetSpecificAOTCompileFlags();
        }
        ArrayList<String> flags = new ArrayList<>(Arrays.asList(
                "-Dsvm.targetArch=" + projectConfiguration.getTargetTriplet().getArch(),
                "-H:+UseCAPCache",
                "-H:CAPCacheDir=" + getCapCacheDir().toAbsolutePath().toString(),
                "-H:CompilerBackend=" + projectConfiguration.getBackend()));
        return flags;
    }

    @Override
    protected List<String> getTargetSpecificCCompileFlags() {
        List<String> flags = new ArrayList<>(Arrays.asList("-I"
                + projectConfiguration.getGraalPath().resolve("include").toString(),
                "-I" + projectConfiguration.getGraalPath().resolve("include").resolve("linux").toString()
        ));

        if (isAarch64) {
            flags.add("-DAARCH64");
        }
        if (crossCompile) {
            flags.add("--sysroot");
            flags.add(sysroot);
        }
        return flags;
    }

    @Override
    List<String> getTargetSpecificLinkOutputFlags() {
        if (projectConfiguration.isSharedLibrary()) {
            return Arrays.asList("-o", getSharedLibPath().toString());
        }
        return super.getTargetSpecificLinkOutputFlags();
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
            FileOps.copyResource(capLocation + cap, capPath.resolve(cap));
        }
        return capPath;
    }

    private void checkCompiler() throws IOException, InterruptedException {
        validateVersion(new String[]{"gcc", "--version"}, "compiler", COMPILER_MINIMAL_VERSION);
    }

    private void checkLinker() throws InterruptedException, IOException {
        validateVersion(new String[]{"ld", "--version"}, "linker", LINKER_MINIMAL_VERSION);
    }

    private void validateVersion(String[] processCommand, String processName, Version minimalVersion) throws InterruptedException, IOException {
        String versionLine = getFirstLineFromProcess(processCommand);
        if (versionLine == null) {
            System.err.println(
                    "WARNING: we were unable to parse the version of your " + processName + ".\n"
                    + "         The build will continue, but please bare in mind that the minimal required version for " + processCommand[0] + " is " + minimalVersion + ".");
        } else {
            VersionParser versionParser = new VersionParser();
            Version version = versionParser.parseVersion(versionLine);
            if (version == null) {
                System.err.println(
                        "WARNING: we were unable to parse the version of your " + processName + ": \"" + versionLine + "\".\n"
                        + "         The build will continue, but please bare in mind that the minimal required version for " + processCommand[0] + " is \"" + minimalVersion + "\".");
            } else if (version.compareTo(minimalVersion) < 0) {
                System.err.println(
                        "ERROR: The version of your " + processName + ": \"" + version + "\", does not match the minimal required version: \"" + minimalVersion + "\".\n"
                        + "       Please check https://docs.gluonhq.com/client/#_linux and make sure that your environment meets the requirements.");
                throw new IllegalArgumentException(processCommand[0] + " version too old");
            }
        }
    }

    private String getFirstLineFromProcess(String... command) throws InterruptedException, IOException {
        ProcessBuilder compiler = new ProcessBuilder(command);
        compiler.redirectErrorStream(true);

        Process compilerProcess = compiler.start();
        InputStream processInputStream = compilerProcess.getInputStream();
        compilerProcess.waitFor();

        try ( BufferedReader reader = new BufferedReader(new InputStreamReader(processInputStream))) {
            return reader.readLine();
        }
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

    @Override
    Predicate<Path> getTargetSpecificNativeLibsFilter() {
        return this::checkFileArchitecture;
    }

    private boolean checkFileArchitecture(Path path) {
        try {
            ProcessRunner pr = new ProcessRunner("objdump", "-f", path.toFile().getAbsolutePath());
            pr.showSevereMessage(false);
            int op = pr.runProcess("objdump");
            if (op == 0) {
                return true;
            }
        } catch (IOException | InterruptedException e) {
            Logger.logSevere("Unrecoverable error checking file " + path + ": " + e);
        }
        Logger.logDebug("Ignore file " + path + " since objdump failed on it");
        return false;
    }

    @Override
    Path getSharedLibPath() {
        return paths.getAppPath().resolve(getLinkOutputName() + ".so");
    }
}
