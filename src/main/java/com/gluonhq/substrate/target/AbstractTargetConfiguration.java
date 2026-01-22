/*
 * Copyright (c) 2019, 2025, Gluon
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
import com.gluonhq.substrate.config.ConfigResolver;
import com.gluonhq.substrate.model.ClassPath;
import com.gluonhq.substrate.model.InternalProjectConfiguration;
import com.gluonhq.substrate.model.ProcessPaths;
import com.gluonhq.substrate.model.Triplet;
import com.gluonhq.substrate.util.FileDeps;
import com.gluonhq.substrate.util.FileOps;
import com.gluonhq.substrate.util.Lib;
import com.gluonhq.substrate.util.Logger;
import com.gluonhq.substrate.util.ProcessRunner;
import com.gluonhq.substrate.util.Strings;
import com.gluonhq.substrate.util.Version;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStreamWriter;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * AbstractTargetConfiguration is the main class that implements the necessary
 * methods to compile, link and run a native image
 *
 * It is extended by different subclasses according to the selected target OS
 */
public abstract class AbstractTargetConfiguration implements TargetConfiguration {

    private static final String URL_CLIBS_ZIP = "https://download2.gluonhq.com/substrate/clibs/${osarch}${version}.zip";
    private static final List<String> RESOURCES_BY_EXTENSION = Arrays.asList(
            "png", "jpg", "jpeg", "gif", "bmp", "ttf", "raw",
            "xml", "fxml", "css", "gls", "json", "dat",
            "license", "frag", "vert", "obj", "mtl", "js", "zip");
    /**
     * Manual registration of the HomeFinderFeature required until GraalVM for JDK 21.
     */
    private static final String HOME_FINDER_FEATURE = "org.graalvm.home.HomeFinderFeature";

    private static final List<String> baseNativeImageArguments = Arrays.asList(
            "-Djdk.internal.lambda.eagerlyInitialize=false",
            "--no-server",
            "-H:+SharedLibrary",
            "-H:+AddAllCharsets",
            "-H:+ReportExceptionStackTraces",
            "-H:-DeadlockWatchdogExitOnTimeout",
            "-H:DeadlockWatchdogInterval=0",
            "-H:+RemoveSaturatedTypeFlows"
    );
    private static final List<String> verboseNativeImageArguments = Arrays.asList(
            "-H:+PrintAnalysisCallTree",
            "-H:Log=registerResource:"
    );

    final FileDeps fileDeps;
    final InternalProjectConfiguration projectConfiguration;
    final ProcessPaths paths;
    protected final boolean crossCompile;

    private final List<String> defaultAdditionalSourceFiles = Collections.singletonList("launcher.c");
    private final List<Lib> defaultStaticJavaLibs = List.of(
            Lib.of("java"), Lib.of("nio"), Lib.of("zip"), Lib.of("net"),
            Lib.of("prefs"), Lib.of("jvm"), Lib.upTo(20, "fdlibm"), Lib.of("z"),
            Lib.of("dl"), Lib.of("j2pkcs11"), Lib.upTo(11, "sunec"), Lib.of("jaas"),
            Lib.of("extnet")
    );

    AbstractTargetConfiguration(ProcessPaths paths, InternalProjectConfiguration configuration) {
        this.projectConfiguration = configuration;
        this.fileDeps = new FileDeps(configuration);
        this.paths = paths;
        this.crossCompile = !configuration.getHostTriplet().equals(configuration.getTargetTriplet());
    }

    // --- public methods

    /**
     * Compile sets the required command line arguments and runs
     * native-image
     *
     * @return true if the process ends successfully, false otherwise
     * @throws IOException
     * @throws InterruptedException
     */
    @Override
    public boolean compile() throws IOException, InterruptedException {
        String substrateClasspath = "";
        try {
            substrateClasspath = new File(AbstractTargetConfiguration.class.getProtectionDomain()
                    .getCodeSource().getLocation().toURI()).getPath();
        } catch (URISyntaxException ex) {
            throw new IOException("Can't locate Substrate.jar", ex);
        }
        String processedClasspath = validateCompileRequirements();

        extractNativeLibs(processedClasspath);

        if (!compileAdditionalSources()) {
            return false;
        }

        ProcessRunner compileRunner = new ProcessRunner(getNativeImagePath());

        baseNativeImageArguments.forEach(compileRunner::addArg);

        compileRunner.addArgs(getNativeImageArguments());

        if (!projectConfiguration.isSharedLibrary() ||
                !projectConfiguration.getTargetTriplet().equals(Triplet.fromCurrentOS())) {
            compileRunner.addArg("-H:+ExitAfterRelocatableImageWrite");
        }

        compileRunner.addArgs(getEnabledFeaturesArgs());

        compileRunner.addArg(createTempDirectoryArg());

        if (allowHttps()) {
            compileRunner.addArg("-H:EnableURLProtocols=http,https");
        }

        if (projectConfiguration.isVerbose()) {
            verboseNativeImageArguments.forEach(compileRunner::addArg);
        }

        compileRunner.addArgs(getConfigurationFileArgs(processedClasspath));

        compileRunner.addArgs(getTargetSpecificAOTCompileFlags());
        List<String> bundlesList = getBundlesList(processedClasspath);
        if (!bundlesList.isEmpty()) {
            String bundles = String.join(",", bundlesList);
            compileRunner.addArg("-H:IncludeResourceBundles=" + bundles);
        }
        compileRunner.addArg(getJniPlatformArg());
        compileRunner.addArg(Constants.NATIVE_IMAGE_ARG_CLASSPATH);
        compileRunner.addArg(substrateClasspath + File.pathSeparator + FileOps.createPathingJar(paths.getTmpPath(), processedClasspath));
        projectConfiguration.getCompilerArgs().stream()
            .filter(arg -> arg != null && !arg.isEmpty())
            .forEach(compileRunner::addArg);
        compileRunner.addArg(projectConfiguration.getMainClassName());

        postProcessCompilerArguments(compileRunner.getCmdList());

        compileRunner.setInfo(true);
        compileRunner.setLogToFile(true);

        Path gvmPath = paths.getGvmPath();
        Path workDir = gvmPath.resolve(projectConfiguration.getAppName());
        int result = compileRunner.runProcess("compile", workDir.toFile());

        return validateCompileResult(result);
    }

    @Override
    public boolean link() throws IOException, InterruptedException {
        compileAdditionalSources();
        ensureClibs();

        String appName = projectConfiguration.getAppName();
        Path gvmPath = paths.getGvmPath();
        Path objectFile = getProjectObjectFile();

        if (projectConfiguration.isStaticLibrary()) {
            return createStaticLib();
        }
        ProcessRunner linkRunner = new ProcessRunner(getLinker());

        Path gvmAppPath = gvmPath.resolve(appName);
        linkRunner.addArgs(getAdditionalObjectFiles());

        linkRunner.addArg(objectFile.toString());
        linkRunner.addArgs(getTargetSpecificObjectFiles());

        linkRunner.addArgs(getNativeCodeList().stream()
            .map(s -> s.replaceAll("\\..*", "." + getObjectFileExtension()))
            .distinct()
            .map(sourceFile -> gvmAppPath.resolve(sourceFile).toString())
            .collect(Collectors.toList()));

        linkRunner.addArgs(getTargetSpecificJavaLinkLibraries());
        linkRunner.addArgs(getTargetSpecificLinkFlags(projectConfiguration.isUseJavaFX(),
                projectConfiguration.isUsePrismSW()));

        linkRunner.addArgs(getTargetSpecificLinkOutputFlags());

        linkRunner.addArgs(getLinkerLibraryPathFlags());
        linkRunner.addArgs(getNativeLibsLinkFlags());
        linkRunner.addArgs(projectConfiguration.getLinkerArgs());
        linkRunner.setInfo(true);
        linkRunner.setLogToFile(true);
        int result = linkRunner.runProcess("link");
        return result == 0;
    }

    /**
     * Creates a package of the application (including at least executable and
     * other possible files) in a given format. By default, this method is no-op
     * returning true.
     */
    @Override
    public boolean packageApp() throws IOException, InterruptedException {
        return true;
    }

    /**
     * Installs the packaged application on the local system or on a device
     * that is attached to the local system. By default, this method is no-op
     * returning true.
     */
    @Override
    public boolean install() throws IOException, InterruptedException {
        return true;
    }

    /**
     * Runs the generated native image
     * @return a string with the last logged output of the process
     * @throws IOException
     * @throws InterruptedException
     */
    @Override
    public String run() throws IOException, InterruptedException {
        String appName = Objects.requireNonNull(getLinkOutputName(), "Application name can't be null");
        Path app = Path.of(getAppPath(appName));
        if (!Files.exists(app)) {
            throw new IOException("Application not found at path " + app.toString());
        }
        ProcessRunner runner = new ProcessRunner(app.toString());
        List<String> runtimeArgsList = projectConfiguration.getRuntimeArgsList();
        if (runtimeArgsList != null) {
            runner.addArgs(runtimeArgsList);
        }
        runner.setInfo(true);
        runner.setLogToFile(true);
        if (runner.runProcess("run " + appName) == 0) {
            return runner.getLastResponse();
        }
        return null;
    }

    /**
     * Run the generated native image and returns true if the process ended
     * successfully
     * @return true if the process ended successfully, false otherwise
     * @throws IOException
     * @throws InterruptedException
     */
    @Override
    public boolean runUntilEnd() throws IOException, InterruptedException {
        String appName = Objects.requireNonNull(getLinkOutputName(),
                "Application name can't be null");
        Path app = Path.of(getAppPath(appName));
        if (!Files.exists(app)) {
            throw new IOException("Application not found at path " + app.toString());
        }
        ProcessRunner runProcess = new ProcessRunner(app.toString());
        List<String> runtimeArgsList = projectConfiguration.getRuntimeArgsList();
        if (runtimeArgsList != null) {
            runProcess.addArgs(runtimeArgsList);
        }
        runProcess.setInfo(true);
        int result = runProcess.runProcess("run until end");
        return result == 0;
    }

    /**
     * Creates a native image that can be used as shared library
     * @return true if the process succeeded or false if the process failed
     * @throws IOException
     * @throws InterruptedException
     */
    @Override
    public boolean createSharedLib() throws IOException, InterruptedException {
        return true;
    }

    /**
     * Creates a static library
     * @return true if the process succeeded or false if the process failed
     * @throws IOException
     * @throws InterruptedException
     */
    @Override
    public boolean createStaticLib() throws IOException, InterruptedException {
        return true;
    }

    // --- private methods

    protected boolean compileAdditionalSources()
            throws IOException, InterruptedException {

        String appName = projectConfiguration.getAppName();
        Path workDir = paths.getGvmPath().resolve(appName);
        Files.createDirectories(workDir);

        if (getAdditionalSourceFiles().isEmpty()) {
            return true;
        }

        ProcessRunner processRunner = new ProcessRunner(getCompiler());
        processRunner.addArg("-c");
        if (projectConfiguration.isVerbose()) {
            processRunner.addArg("-DGVM_VERBOSE");
        }
        processRunner.addArg("-DSUBSTRATE");
        processRunner.addArgs(getTargetSpecificCCompileFlags());

        processRunner.addArg("-I" + workDir.toString());

        processRunner.addArgs(copyAdditionalSourceFiles(workDir));

        Path nativeCodeDir = paths.getNativeCodePath();
        if (Files.isDirectory(nativeCodeDir)) {
            FileOps.copyDirectory(nativeCodeDir, workDir);
        }

        processRunner.addArgs(getNativeCodeList());

        for (String fileName : getAdditionalHeaderFiles()) {
            FileOps.copyResource(getAdditionalSourceFileLocation()  + fileName, workDir.resolve(fileName));
        }
  
        int result = processRunner.runProcess("compile-additional-sources", workDir.toFile());
        // we need more checks (e.g. do launcher.o and thread.o exist?)
        return result == 0;
    }

    private String validateCompileRequirements() throws IOException {
        String mainClassName = projectConfiguration.getMainClassName();
        if (mainClassName == null || mainClassName.isEmpty()) {
            throw new IllegalArgumentException("No main class is supplied. Cannot compile.");
        }

        String processedClasspath = processClassPath(projectConfiguration.getClasspath());
        if (processedClasspath == null || processedClasspath.isEmpty()) {
            throw new IllegalArgumentException("No classpath specified. Cannot compile");
        }

        return processedClasspath;
    }

    private String getJniPlatformArg() {
        String jniPlatform = getJniPlatform();
        return "-Dsvm.platform=org.graalvm.nativeimage.Platform$" + jniPlatform;
    }

    private String getJniPlatform() {
        Triplet target = projectConfiguration.getTargetTriplet();
        Version graalVersion = projectConfiguration.getGraalVersion();
        boolean graalVM221 = ((graalVersion.getMajor() > 21) && (graalVersion.getMinor() > 0));
        String os = target.getOs();
        String arch = target.getArch();
        switch (os) {
            case Constants.OS_LINUX:
                switch (arch) {
                    case Constants.ARCH_AMD64:
                        return "LINUX_AMD64";
                    case Constants.ARCH_AARCH64:
                        return "LINUX_AARCH64";
                    default:
                        throw new IllegalArgumentException("No support yet for " + os + ":" + arch);
                }
            case Constants.OS_IOS:
                if (Constants.ARCH_AMD64.equals(arch)) {
                    if (projectConfiguration.usesJDK11()) {
                        throw new IllegalArgumentException("iOS Simulator requires JDK 17");
                    }
                    return "IOS_AMD64";
                }
                return "IOS_AARCH64";
            case Constants.OS_DARWIN:
                switch (arch) {
                    case Constants.ARCH_AMD64:
                        return graalVM221 ? "MACOS_AMD64" : "DARWIN_AMD64";
                    case Constants.ARCH_AARCH64:
                        return "MACOS_AARCH64";
                    default:
                        throw new IllegalArgumentException("No support yet for " + os + ":" + arch);
                }
            case Constants.OS_WINDOWS:
                return "WINDOWS_AMD64";
            case Constants.OS_ANDROID:
                return "LINUX_AARCH64";
            default:
                throw new IllegalArgumentException("No support yet for " + os);
        }
    }

    /*
     * Make sure the clibraries needed for linking are available for this particular configuration.
     * The clibraries path is available by default in GraalVM, but the directory for cross-platform libs may
     * not exist. In that case, retrieve the libs from our download site.
     */
    private void ensureClibs() throws IOException {
        Triplet target = projectConfiguration.getTargetTriplet();
        Path clibPath = getCLibPath();
        if (FileOps.isDirectoryEmpty(clibPath)) {
            String url = Strings.substitute(URL_CLIBS_ZIP,
                    Map.of("osarch", target.getOsArch(),
                            "version", target.getClibsVersion()));
            FileOps.downloadAndUnzip(url,
                    clibPath.getParent().getParent().getParent(),
                    "clibraries.zip",
                    "clibraries",
                    target.getClibsVersionPath(),
                    target.getOsArch2());
        }
        if (FileOps.isDirectoryEmpty(clibPath)) {
            throw new IOException("No clibraries found for the required architecture in " + clibPath);
        }
        checkPlatformSpecificClibs(clibPath);
    }

    /**
     * Generates the library search path arguments to be added to the linker.
     *
     * @return a list of library search path arguments for the linker
     * @throws IOException
     */
    private List<String> getLinkerLibraryPathFlags() throws IOException {
        return getLinkerLibraryPaths().stream()
                .map(path -> getLinkLibraryPathOption() + path)
                .collect(Collectors.toList());
    }

    /**
     * Creates a list of Paths that will be added to the library search path for the linker.
     * Targets are allowed to override this, e.g. in case they don't want the static JDK
     * directory on the library path (see https://github.com/gluonhq/substrate/issues/879)
     *
     * Note: we should probably invert this logic: the static library path should not be
     * used as linkLibraryPath unless explicitly asked by the target.
     *
     * @return a list of Paths to add to the library search path
     * @throws IOException
     */
    protected List<Path> getLinkerLibraryPaths() throws IOException {
        List<Path> linkerLibraryPaths = new ArrayList<>();
        if (projectConfiguration.isUseJavaFX()) {
            linkerLibraryPaths.add(fileDeps.getJavaFXSDKLibsPath());
        }

        linkerLibraryPaths.add(getCLibPath());
        linkerLibraryPaths.addAll(getStaticJDKLibPaths());

        return linkerLibraryPaths;
    }

    private String getNativeImagePath() {
        return projectConfiguration.getGraalPath()
                .resolve("bin")
                .resolve(getNativeImageCommand())
                .toString();
    }

    protected List<String> getNativeImageArguments() {
        return List.of();
    }

    protected List<String> getEnabledFeatures() {
        return List.of();
    }

    private List<String> getEnabledFeaturesArgs() {
        List<String> args = new ArrayList<>();
        if (projectConfiguration.getJavaVersion().getMajor() < 21) {
            args.add("--features=" + HOME_FINDER_FEATURE);
        }
        for (String feature : getEnabledFeatures()) {
            args.add("--features=" + feature);
        }
        return args;
    }

    private String createTempDirectoryArg() throws IOException {
        Path tmpPath = paths.getTmpPath();
        FileOps.rmdir(tmpPath);
        String tmpDir = tmpPath.toFile().getAbsolutePath();
        return "-H:TempDirectory=" + tmpDir;
    }

    private List<String> getReflectionClassList(String suffix, boolean useJavaFX, boolean usePrismSW) {
        List<String> answer = new LinkedList<>();
        answer.add(Constants.REFLECTION_JAVA_FILE);
        if (useJavaFX && usePrismSW) {
            answer.add(Constants.REFLECTION_JAVAFXSW_FILE);
        }
        return answer;
    }

    private List<String> getJNIClassList(String suffix, boolean useJavaFX, boolean usePrismSW) {
        List<String> answer = new LinkedList<>();
        answer.add(projectConfiguration.usesJDK11() ? Constants.JNI_JAVA_FILE11 : Constants.JNI_JAVA_FILE);
        if (useJavaFX && usePrismSW) {
            answer.add(Constants.JNI_JAVAFXSW_FILE);
        }
        return answer;
    }

    private List<String> getBundlesList(String processedClasspath) throws IOException, InterruptedException {
        List<String> list = new ArrayList<>(projectConfiguration.getBundlesList());
        String suffix = projectConfiguration.getTargetTriplet().getArchOs();
        ConfigResolver configResolver = new ConfigResolver(processedClasspath);
        list.addAll(configResolver.getResourceBundlesList(suffix));
        return list;
    }

    private List<String> getConfigurationFileArgs(String processedClasspath) throws IOException, InterruptedException {
        List<String> arguments = new ArrayList<>();

        String suffix = projectConfiguration.getTargetTriplet().getArchOs();
        ConfigResolver configResolver = new ConfigResolver(processedClasspath);

        List<String> buildTimeList = getInitializeAtBuildTimeList(suffix, configResolver);
        if (!buildTimeList.isEmpty()) {
            arguments.add("--initialize-at-build-time=" + String.join(",", buildTimeList));
        }

        arguments.add("-H:ReflectionConfigurationFiles=" + createReflectionConfig(suffix, configResolver));
        arguments.add("-H:JNIConfigurationFiles=" + createJNIConfig(suffix, configResolver));
        arguments.add("-H:ResourceConfigurationFiles=" + createResourceConfig(suffix, configResolver));

        return arguments;
    }

    /**
     * Generates a list with class names that should be added to the
     * initialize in build time flag
     *
     * @return a list with fully qualified class names
     */
    private List<String> getInitializeAtBuildTimeList(String suffix, ConfigResolver configResolver) throws IOException {
        List<String> list = new ArrayList<>(projectConfiguration.getInitBuildTimeList());
        list.addAll(configResolver.getUserInitBuildTimeList(suffix));
        return list;
    }

    private Path createReflectionConfig(String suffix, ConfigResolver configResolver) throws IOException {
        Path gvmPath = paths.getGvmPath();
        Path reflectionPath = gvmPath.resolve(
                Strings.substitute(Constants.REFLECTION_ARCH_FILE, Map.of("archOs", suffix)));
        Files.deleteIfExists(reflectionPath);
        try (BufferedWriter bw = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(reflectionPath.toFile())))) {
            bw.write("[\n");
            writeSingleEntry(bw, projectConfiguration.getMainClassName(), false);
            for (String javaFile : getReflectionClassList(suffix, projectConfiguration.isUseJavaFX(), projectConfiguration.isUsePrismSW())) {
                InputStream inputStream = AbstractTargetConfiguration.class.getResourceAsStream(Constants.CONFIG_FILES + javaFile);
                if (inputStream != null) {
                    bw.write(",\n");
                    List<String> lines = FileOps.readFileLines(inputStream,
                            line -> !line.startsWith("[") && !line.startsWith("]"));
                    for (String line : lines) {
                        bw.write(line + "\n");
                    }
                }
            }

            for (String line : configResolver.getUserReflectionList(suffix)) {
                bw.write(line + "\n");
            }

            for (String javaClass : projectConfiguration.getReflectionList()) {
                writeEntry(bw, javaClass);
            }
            bw.write("]");
        }
        return reflectionPath;
    }

    private Path createJNIConfig(String suffix, ConfigResolver configResolver) throws IOException {
        Path gvmPath = paths.getGvmPath();
        Path jniPath = gvmPath.resolve(Strings.substitute(Constants.JNI_ARCH_FILE, Map.of("archOs", suffix)));
        Files.deleteIfExists(jniPath);
        try (BufferedWriter bw = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(jniPath.toFile())))) {
            bw.write("[\n");
            bw.write("  {\n    \"name\" : \"" + projectConfiguration.getMainClassName() + "\"\n  }\n");
            for (String javaFile : getJNIClassList(suffix, projectConfiguration.isUseJavaFX(), projectConfiguration.isUsePrismSW())) {
                InputStream inputStream = AbstractTargetConfiguration.class.getResourceAsStream(Constants.CONFIG_FILES + javaFile);
                if (inputStream != null) {
                    bw.write(",\n");
                    List<String> lines = FileOps.readFileLines(inputStream,
                            line -> !line.startsWith("[") && !line.startsWith("]"));
                    for (String line : lines) {
                        bw.write(line + "\n");
                    }
                }
            }

            for (String line : configResolver.getUserJNIList(suffix)) {
                bw.write(line + "\n");
            }

            for (String javaClass : projectConfiguration.getJniList()) {
                writeEntry(bw, javaClass);
            }
            bw.write("]");
        }
        return jniPath;
    }

    private Path createResourceConfig(String suffix, ConfigResolver configResolver) throws IOException {
        Path gvmPath = paths.getGvmPath();
        Path resourcePath = gvmPath.resolve(
                Strings.substitute(Constants.RESOURCE_ARCH_FILE, Map.of("archOs", suffix)));
        Files.deleteIfExists(resourcePath);
        try (BufferedWriter bw = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(resourcePath.toFile())))) {
            bw.write("{\n");
            bw.write("  \"resources\": [\n");
            boolean patternHasBeenWritten = false;
            for (String extension : RESOURCES_BY_EXTENSION) {
                if (patternHasBeenWritten) {
                    bw.write(",\n");
                } else {
                    patternHasBeenWritten = true;
                }
                writePatternEntry(bw, ".*\\\\." + extension + "$");
            }
            for (String configurationResource : projectConfiguration.getResourcesList()) {
                if (patternHasBeenWritten) {
                    bw.write(",\n");
                } else {
                    patternHasBeenWritten = true;
                }
                writePatternEntry(bw, configurationResource);
            }

            List<String> userResourcesList = configResolver.getUserResourcesList(suffix);
            if (!userResourcesList.isEmpty()) {
                if (patternHasBeenWritten) {
                    bw.write(",\n");
                }
                for (String line : userResourcesList) {
                    bw.write(line + "\n");
                }
            } else if (patternHasBeenWritten) {
                bw.write("\n");
            }

            bw.write("  ]\n");
            bw.write("}");
        }
        return resourcePath;
    }

    private static void writeEntry(BufferedWriter bw, String javaClass) throws IOException {
        writeEntry(bw, javaClass, false);
    }

    private static void writeEntry(BufferedWriter bw, String javaClass, boolean exclude) throws IOException {
        bw.write(",\n");
        writeSingleEntry(bw, javaClass, exclude);
    }

    private static void writeSingleEntry(BufferedWriter bw, String javaClass, boolean exclude) throws IOException {
        bw.write("  {\n");
        bw.write("    \"name\" : \"" + javaClass + "\"");
        if (!exclude) {
            bw.write(",\n");
            bw.write("    \"allDeclaredConstructors\" : true,\n");
            bw.write("    \"allPublicConstructors\" : true,\n");
            bw.write("    \"allDeclaredFields\" : true,\n");
            bw.write("    \"allPublicFields\" : true,\n");
            bw.write("    \"allDeclaredMethods\" : true,\n");
            bw.write("    \"allPublicMethods\" : true\n");
        } else {
            bw.write("\n");
        }
        bw.write("  }\n");
    }

    private static void writePatternEntry(BufferedWriter bw, String pattern) throws IOException {
        bw.write("    {\"pattern\": \"" + pattern + "\"}");
    }

    /**
     * Loops over every jar on the classpath that isn't a JavaFX jar and checks
     * if it contains native static libraries (*.a or *.lib files) or object files. If found, the
     * libraries and object files are extracted into a temporary folder for use in the link step.
     *
     * @param classPath The classpath of the project
     * @throws IOException
     */
    private void extractNativeLibs(String classPath) throws IOException {
        Path libPath = paths.getGvmPath().resolve(Constants.LIB_PATH);
        if (Files.exists(libPath)) {
            FileOps.deleteDirectory(libPath);
        }
        Logger.logDebug("Extracting native libs to: " + libPath);

        List<String> jars = new ClassPath(classPath).filter(s -> s.endsWith(".jar") && !s.contains("javafx-"));
        for (String jar : jars) {
            Logger.logDebug("Extracting native libs from jar: " + jar);
            FileOps.extractFilesFromJar("." + getStaticLibraryFileExtension(), Path.of(jar),
                    libPath, getTargetSpecificNativeLibsFilter());
            FileOps.extractFilesFromJar(".o" , Path.of(jar),
                    libPath, getTargetSpecificNativeLibsFilter());

        }
    }

    /**
     * Adds the possible native libraries found in the project to
     * the link commands
     *
     * @return a list with command line options to include native libraries,
     * like the path and how to link them
     * @throws IOException
     */
    private List<String> getNativeLibsLinkFlags() throws IOException {
        List<String> linkFlags = new ArrayList<>();
        Path libPath = paths.getGvmPath().resolve(Constants.LIB_PATH);
        if (Files.exists(libPath)) {
            List<String> libs;
            try (Stream<Path> files = Files.list(libPath)) {
                libs = files.map(file -> file.getFileName().toString())
                        .filter(this::matchesStaticLibraryName)
                        .collect(Collectors.toList());
            }

            if (!libs.isEmpty()) {
                linkFlags.add(getLinkLibraryPathOption() + libPath.toString());
                linkFlags.addAll(getTargetSpecificNativeLibsFlags(libPath, libs));
            }
        }
        return linkFlags;
    }

    private boolean validateCompileResult(int result) throws IOException {
        boolean success = result == 0;
        if (success) {
            Path gvmPath = paths.getGvmPath();

            // we will print the output of the process only if we don't have the resulting objectfile
            String nameSearch = projectConfiguration.getMainClassName().toLowerCase(Locale.ROOT) + "." + getObjectFileExtension();
            if (FileOps.findFile(gvmPath, nameSearch).isEmpty()) {
                Logger.logInfo("Additional information: Objectfile should be called " + nameSearch + " but we didn't find that under " + gvmPath.toString());
                return false;
            }
        }

        return success;
    }

    /**
     * If we are not using JavaFX, we immediately return the provided classpath: no further processing
     * is needed. If we do use JavaFX, we will first {@link FileDeps#getJavaFXSDKLibsPath obtain
     * the location of the JavaFX SDK} for this configuration. After the path to the JavaFX SDK is
     * obtained, the JavaFX jars of the host platform are replaced by the JavaFX jars for the target
     * platform.
     *
     * @param classPath The provided classpath
     * @return A string with the modified classpath if JavaFX is used
     * @throws IOException when something went wrong while resolving the location of the JavaFX SDK.
     */
    private String processClassPath(String classPath) throws IOException {
        if (!projectConfiguration.isUseJavaFX()) {
            return classPath;
        }

        return new ClassPath(classPath).mapWithLibs(fileDeps.getJavaFXSDKLibsPath(),
                s -> s.replace("-", "."),
                p -> {
                    if (!Files.exists(p)) {
                        throw new IllegalArgumentException("Error: " + p + " not found. Cannot compile.");
                    }
                    return true;
                },
                "javafx-base", "javafx-graphics", "javafx-controls", "javafx-fxml", "javafx-media", "javafx-web");
    }

    // --- package protected methods

    // Methods below with default implementation, can be overridden by subclasses

    /**
     * Returns whether or not this target allows for the HTTPS protocol
     * By default, this method returns true, but subclasses can decide against it.
     */
    boolean allowHttps() {
        return true;
    }

    /**
     * Allow platforms to check if specific libraries (e.g. libjvm.a) are present in the specified clib path
     * @param clibPath
     */
    void checkPlatformSpecificClibs(Path clibPath) throws IOException {
        // empty, override by subclasses
    }

    String getAdditionalSourceFileLocation() {
        return "/native/linux/";
    }

    List<String> getAdditionalSourceFiles() {
        return defaultAdditionalSourceFiles;
    }

    List<String> copyAdditionalSourceFiles(Path workDir) throws IOException {
        List<String> files = new ArrayList<>();
        for (String fileName : getAdditionalSourceFiles()) {
            FileOps.copyResource(getAdditionalSourceFileLocation() + fileName, workDir.resolve(fileName));
            files.add(fileName);
        }
        return files;
    }

    List<String> getAdditionalHeaderFiles() {
        return Collections.emptyList();
    }

    String getCompiler() {
        return "gcc";
    }

    String getLinker() {
        return "gcc";
    }

    String getNativeImageCommand() {
        return "native-image";
    }

    String getObjectFileExtension() {
        return "o";
    }

    String getStaticLibraryFileExtension() {
        return "a";
    }

    /**
     * Defines how the library path is added to linker.
     * Implementations can override this for providing a different syntax.
     *
     * @return a search path description understood by the host-specific
     * linker when creating images for the specific target.
     */
    String getLinkLibraryPathOption() {
        return "-L";
    }

    /**
     * Defines how the linker will search for this archive or object file.
     * Implementations can override this for providing a different syntax.
     *
     * @return a file with a search description understood by the host-specific
     * linker when creating images for the specific target.
     */
    String getLinkLibraryOption(String libname) {
        if (libname.startsWith("/")) return libname;
        return "-l" + libname;
    }

    /**
     * Returns true if the provided <code>fileName</code> matches the naming pattern
     * for static libraries.
     *
     * @param fileName the filename to test
     * @return true if the filename matches, false otherwise
     */
    boolean matchesStaticLibraryName(String fileName) {
        return fileName.startsWith("lib") &&
                fileName.endsWith("." + getStaticLibraryFileExtension());
    }

    /**
     * Apply post-processing to the arguments for the compiler command.
     *
     * @param arguments the list of arguments of the compiler command.
     */
    void postProcessCompilerArguments(List<String> arguments) {
        // no post processing is required by default
    }

    /**
     * Returns a string with the application path.
     * The required folders will be created in case these don't exist
     *
     * @param appName the application name
     * @return a string with the path of the application
     */
    String getAppPath(String appName) {
        return paths.getAppPath().resolve(appName).toString();
    }

    /**
     * Return a list of static Java libraries that need to be added to the link command.
     * Implementations can override this for providing a different list.
     *
     * @return a List of Java libraries that will be understood by the host-specific
     * linker when creating images for the specific target.
     */
    List<String> getStaticJavaLibs() {
        return filterApplicableLibs(defaultStaticJavaLibs);
    }

    /**
     * Return a list of other static libraries (JVM libraries, platform-specific libraries)
     * that need to be added to the link command.
     * Implementations can override this for providing a different list.
     *
     * @return a List of other static libraries that will be understood by the host-specific
     * linker when creating images for the specific target.
     */
    List<String> getOtherStaticLibs() {
        return List.of();
    }

    /**
     * Return the list of library names applicable to the used java version.
     * @param libs List to validate based on {@link Lib#inRange(int)}.
     * @return The list of library names applicable to the used java version.
     */
    final List<String> filterApplicableLibs(List<Lib> libs) {
        int major = projectConfiguration.getJavaVersion().getMajor();
        return libs.stream()
                .filter(l -> l.inRange(major))
                .map(Lib::getLibName)
                .collect(Collectors.toList());
    }

    /**
     * Return the arguments that need to be passed to the linker for including
     * the Java libraries in the final image.
     *
     * @return a List of arguments that will be understood by the host-specific
     * linker when creating images for the specific target.
     */
    private List<String> getTargetSpecificJavaLinkLibraries() {
        return Stream.concat(getStaticJavaLibs().stream(), getOtherStaticLibs().stream())
                .map(this::getLinkLibraryOption)
                .collect(Collectors.toList());
    }

    List<String> getTargetSpecificLinkOutputFlags() {
        return Arrays.asList("-o", getAppPath(getLinkOutputName()));
    }

    String getLinkOutputName() {
        return projectConfiguration.getAppName();
    }

    protected List<String> getTargetNativeCodeExtensions() {
        return Arrays.asList(".c");
    }

    protected List<String> getNativeCodeList() throws IOException {
        Path nativeCodeDir = paths.getNativeCodePath();
        if (!Files.exists(nativeCodeDir)) {
            return Collections.emptyList();
        }
        List<String> extensions = getTargetNativeCodeExtensions();
        return Files.list(nativeCodeDir)
            .map(p -> p.getFileName().toString())
            .filter(s -> extensions.stream().anyMatch(e -> s.endsWith(e)))
            .collect(Collectors.toList());
    }

    List<String> getTargetSpecificLinkFlags(boolean useJavaFX, boolean usePrismSW) throws IOException, InterruptedException {
        return Collections.emptyList();
    }

    List<String> getTargetSpecificCCompileFlags() {
        return Collections.emptyList();
    }

    List<String> getTargetSpecificAOTCompileFlags() throws IOException {
        return Collections.emptyList();
    }

    @Deprecated
    List<String> getTargetSpecificObjectFiles() throws IOException {
        return Collections.emptyList();
    }

    /**
     * A filter can be used to verify if the native library matches certain
     * criteria, like being available for a given architecture
     *
     * @return a predicate, default is null (no filter applied)
     */
    Predicate<Path> getTargetSpecificNativeLibsFilter() {
        return null;
    }

    /**
     * It generates the link flags for a given list of native libraries,
     * at a given location
     * @param libPath the path to the folder with the native libraries
     * @param libs the list of names of native libraries
     * @return a list with link flag options
     */
    List<String> getTargetSpecificNativeLibsFlags(Path libPath, List<String> libs) {
        return Collections.emptyList();
    }

    protected Path getCLibPath() {
        Triplet target = projectConfiguration.getTargetTriplet();
        return projectConfiguration.getGraalPath()
                .resolve("lib")
                .resolve("svm")
                .resolve("clibraries")
                .resolve(target.getClibsVersionPath())
                .resolve(target.getOsArch2());
    }

    protected List<Path> getStaticJDKLibPaths() throws IOException {
        List<Path> staticJDKLibPaths = new ArrayList<>();
        if (projectConfiguration.useCustomJavaStaticLibs()) {
            staticJDKLibPaths.add(projectConfiguration.getJavaStaticLibsPath());
        }

        Triplet target = projectConfiguration.getTargetTriplet();
        Path staticJDKLibPath = projectConfiguration.getGraalPath()
                .resolve("lib")
                .resolve("static")
                .resolve(target.getOsArch2());
        if (target.getOs().equals(Constants.OS_LINUX)) {
            return Arrays.asList(staticJDKLibPath.resolve("glibc"));
        } else {
            return Arrays.asList(staticJDKLibPath);
        }
    }

    /**
     * Get the objectfiles that are compiled from the additionalSourceFiles
     *
     * @return
     */
    final List<String> getAdditionalObjectFiles() {
        String appName = projectConfiguration.getAppName();
        Path gvmPath = paths.getGvmPath();
        Path gvmAppPath = gvmPath.resolve(appName);
        List<String> answer = getAdditionalSourceFiles().stream()
                .map(s -> s.replaceAll("\\..*", "." + getObjectFileExtension()))
                .distinct()
                .map(sourceFile -> gvmAppPath.resolve(sourceFile).toString())
                .collect(Collectors.toList());

        return answer;
    }

    /**
     * Return the location of the compiled application file. This is the file
     * that is generated by the AOT compiler.
     *
     * @return the Path to the compiled application
     */
    final Path getProjectObjectFile() throws IOException {
        Path gvmPath = paths.getGvmPath();
        String objectFilename = projectConfiguration.getMainClassName().toLowerCase(Locale.ROOT) + "." + getObjectFileExtension();
        Path objectFile = FileOps.findFile(gvmPath, objectFilename).orElseThrow(()
                -> new IllegalArgumentException(
                        "Linking failed, since there is no objectfile named " + objectFilename + " under " + gvmPath.toString())
        );
        return objectFile;
    }
}
