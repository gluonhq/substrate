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
package com.gluonhq.substrate.target;

import com.gluonhq.substrate.Constants;
import com.gluonhq.substrate.config.ConfigResolver;
import com.gluonhq.substrate.model.ClassPath;
import com.gluonhq.substrate.model.InternalProjectConfiguration;
import com.gluonhq.substrate.model.ProcessPaths;
import com.gluonhq.substrate.model.Triplet;
import com.gluonhq.substrate.util.FileDeps;
import com.gluonhq.substrate.util.FileOps;
import com.gluonhq.substrate.util.Logger;
import com.gluonhq.substrate.util.ProcessRunner;
import com.gluonhq.substrate.util.Strings;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
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

    private static final String URL_CLIBS_ZIP = "http://download2.gluonhq.com/substrate/clibs/${osarch}.zip";
    private static final List<String> RESOURCES_BY_EXTENSION = Arrays.asList(
            "frag", "vert", "fxml", "css", "gls", "ttf", "xml",
            "png", "jpg", "jpeg", "gif", "bmp",
            "license", "json");
    private static final List<String> BUNDLES_LIST = new ArrayList<>(Arrays.asList(
            "com/sun/javafx/scene/control/skin/resources/controls",
            "com.sun.javafx.tk.quantum.QuantumMessagesBundle"
    ));

    final FileDeps fileDeps;
    final InternalProjectConfiguration projectConfiguration;
    final ProcessPaths paths;
    protected final boolean crossCompile;

    private ConfigResolver configResolver;
    private List<String> defaultAdditionalSourceFiles = Collections.singletonList("launcher.c");

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
     * @param cp The classpath of the project to be run
     * @return true if the process ends successfully, false otherwise
     * @throws IOException
     * @throws InterruptedException
     */
    @Override
    public boolean compile(String cp) throws IOException, InterruptedException {
        String classPath = processClassPath(cp);
        extractNativeLibs(cp);
        Triplet target =  projectConfiguration.getTargetTriplet();
        String suffix = target.getArchOs();
        String jniPlatform = getJniPlatform(target);
        if (!compileAdditionalSources()) {
            return false;
        }
        Path gvmPath = paths.getGvmPath();
        FileOps.rmdir(paths.getTmpPath());
        String tmpDir = paths.getTmpPath().toFile().getAbsolutePath();
        String mainClassName = projectConfiguration.getMainClassName();

        if (mainClassName == null || mainClassName.isEmpty()) {
            throw new IllegalArgumentException("No main class is supplied. Cannot compile.");
        }
        if (cp == null || cp.isEmpty()) {
            throw new IllegalArgumentException("No classpath specified. Cannot compile");
        }
        configResolver = new ConfigResolver(cp);
        String nativeImage = getNativeImagePath();
        ProcessBuilder compileBuilder = new ProcessBuilder(nativeImage);
        List<String> buildTimeList = getInitializeAtBuildTimeList(suffix);
        if (!buildTimeList.isEmpty()) {
            compileBuilder.command().add("--initialize-at-build-time=" + String.join(",", buildTimeList));
        }
        compileBuilder.command().add("--report-unsupported-elements-at-runtime");
        compileBuilder.command().add("-Djdk.internal.lambda.eagerlyInitialize=false");
        compileBuilder.command().add("-H:+ExitAfterRelocatableImageWrite");
        compileBuilder.command().add("-H:TempDirectory="+tmpDir);
        compileBuilder.command().add("-H:+SharedLibrary");
        compileBuilder.command().add("-H:+AddAllCharsets");
        if (allowHttps()) {
            compileBuilder.command().add("-H:EnableURLProtocols=http,https");
        }
        compileBuilder.command().add("-H:ReflectionConfigurationFiles=" + createReflectionConfig(suffix));
        compileBuilder.command().add("-H:JNIConfigurationFiles=" + createJNIConfig(suffix));
        if (projectConfiguration.isVerbose()) {
            compileBuilder.command().add("-H:+PrintAnalysisCallTree");
        }
        compileBuilder.command().add("-H:ResourceConfigurationFiles=" + createResourceConfig(suffix));
        if (projectConfiguration.isVerbose()) {
            compileBuilder.command().add("-H:Log=registerResource:");
        }
        compileBuilder.command().addAll(getTargetSpecificAOTCompileFlags());
        if (!getBundlesList().isEmpty()) {
            String bundles = String.join(",", getBundlesList());
            compileBuilder.command().add("-H:IncludeResourceBundles=" + bundles);
        }
        compileBuilder.command().add("-Dsvm.platform=org.graalvm.nativeimage.Platform$"+jniPlatform);
        compileBuilder.command().add("-cp");
        compileBuilder.command().add(classPath);
        compileBuilder.command().addAll(projectConfiguration.getCompilerArgs());
        compileBuilder.command().add(mainClassName);

        postProcessCompilerArguments(compileBuilder.command());

        Logger.logDebug("compile command: " + String.join(" ", compileBuilder.command()));
        Path workDir = gvmPath.resolve(projectConfiguration.getAppName());
        compileBuilder.directory(workDir.toFile());
        compileBuilder.redirectErrorStream(true);
        Process compileProcess = compileBuilder.start();
        InputStream inputStream = compileProcess.getInputStream();
        asynPrintFromInputStream(inputStream);
        int result = compileProcess.waitFor();
        // we will print the output of the process only if we don't have the resulting objectfile

        boolean failure = result != 0;
        String extraMessage = null;
        if (!failure) {
            String nameSearch = mainClassName.toLowerCase() + "." + getObjectFileExtension();
            if (FileOps.findFile(gvmPath, nameSearch).isEmpty()) {
                failure = true;
                extraMessage = "Objectfile should be called "+nameSearch+" but we didn't find that under "+gvmPath.toString();
            }
        }
        if (failure) {
            System.err.println("Compilation failed with result = " + result);
            printFromInputStream(inputStream);

            if (extraMessage!= null) {
                System.err.println("Additional information: "+extraMessage);
            }
        }
        return !failure;
    }

    /**
    * Links a previously created objectfile with the required
    * dependencies into a native executable.
    * @return true if linking succeeded, false otherwise
    */
    @Override
    public boolean link() throws IOException, InterruptedException {
        compileAdditionalSources();
        ensureClibs();

        String appName = projectConfiguration.getAppName();
        String objectFilename = projectConfiguration.getMainClassName().toLowerCase() + "." + getObjectFileExtension();
        Path gvmPath = paths.getGvmPath();
        Path objectFile = FileOps.findFile(gvmPath, objectFilename).orElseThrow( () ->
            new IllegalArgumentException(
                    "Linking failed, since there is no objectfile named " + objectFilename + " under " + gvmPath.toString())
        );

        ProcessBuilder linkBuilder = new ProcessBuilder(getLinker());

        Path gvmAppPath = gvmPath.resolve(appName);
        getAdditionalSourceFiles().forEach(sourceFile -> linkBuilder.command()
                .add(gvmAppPath.resolve(sourceFile.replaceAll("\\..*", "." + getObjectFileExtension())).toString()));

        linkBuilder.command().add(objectFile.toString());
        linkBuilder.command().addAll(getTargetSpecificObjectFiles());

        getNativeCodeList().stream()
            .map(s -> s.replaceAll("\\..*", "." + getObjectFileExtension()))
            .distinct()
            .collect(Collectors.toList())
            .forEach(sourceFile -> linkBuilder.command().add(gvmAppPath.resolve(sourceFile).toString()));

        getTargetSpecificLinkLibraries().forEach(linkBuilder.command()::add);
        linkBuilder.command().addAll(getTargetSpecificLinkFlags(projectConfiguration.isUseJavaFX(), projectConfiguration.isUsePrismSW()));

        getTargetSpecificLinkOutputFlags().forEach(linkBuilder.command()::add);

        addGraalStaticLibsPathToLinkProcess(linkBuilder);
        addJavaStaticLibsPathToLinkProcess(linkBuilder);
        if (projectConfiguration.isUseJavaFX()) {
            addJavaFXStaticLibsPathToLinkProcess(linkBuilder);
        }
        linkBuilder.command().addAll(getNativeLibsLinkFlags());

        linkBuilder.redirectErrorStream(true);
        String cmds = String.join(" ", linkBuilder.command());
        Logger.logDebug("link command: "+cmds);
        Process compileProcess = linkBuilder.start();
        System.err.println("started linking");
        int result = compileProcess.waitFor();
        System.err.println("done linking");
        if (result != 0) {
            System.err.println("Linking failed. Details from linking below:");
            System.err.println("Command was: "+cmds);
            printFromInputStream(compileProcess.getInputStream());
            return false;
        }
        return true;
    }

    /**
     * Runs the generated native image
     * @param appPath Path to the application to be run
     * @param appName application name
     * @return a string with the last logged output of the process
     * @throws IOException
     * @throws InterruptedException
     */
    @Override
    public String run(Path appPath, String appName) throws IOException, InterruptedException {
        Path app = Objects.requireNonNull(appPath, "Application path can't be null")
                .resolve(Objects.requireNonNull(appName, "Application name can't be null"));
        if (!Files.exists(app)) {
            throw new IOException("Application not found at path " + app.toString());
        }
        ProcessRunner runner = new ProcessRunner(app.toString());
        runner.setInfo(true);
        if (runner.runProcess("run " + appName) == 0) {
            return runner.getLastResponse();
        } else {
            System.err.println("Run process failed. Command line was: " + runner.getCmd() + "\nOutput was:");
            runner.getResponses().forEach(System.err::println);
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
        Process runProcess = startAppProcess(paths.getAppPath(), projectConfiguration.getAppName());
        InputStream is = runProcess.getInputStream();
        asynPrintFromInputStream(is);
        int result = runProcess.waitFor();
        if (result != 0) {
            printFromInputStream(is);
            return false;
        }
        return true;
    }

    @Override
    public boolean packageApp() throws IOException, InterruptedException {
        return false;
    }

    // --- private methods

    protected boolean compileAdditionalSources()
            throws IOException, InterruptedException {

        String appName = projectConfiguration.getAppName();
        Path workDir = paths.getGvmPath().resolve(appName);
        Files.createDirectories(workDir);

        ProcessBuilder processBuilder = new ProcessBuilder(getCompiler());
        processBuilder.command().add("-c");
        if (projectConfiguration.isVerbose()) {
            processBuilder.command().add("-DGVM_VERBOSE");
        }
        processBuilder.command().addAll(getTargetSpecificCCompileFlags());

        processBuilder.command().add("-I" + workDir.toString());

        for( String fileName: getAdditionalSourceFiles() ) {
            FileOps.copyResource(getAdditionalSourceFileLocation()  + fileName, workDir.resolve(fileName));
            processBuilder.command().add(fileName);
        }
        
        Path nativeCodeDir = paths.getNativeCodePath();
        FileOps.copyDirectory(nativeCodeDir, workDir);

        for( String fileName: getNativeCodeList() ) {
            processBuilder.command().add(fileName);
        }

        for( String fileName: getAdditionalHeaderFiles() ) {
            FileOps.copyResource(getAdditionalSourceFileLocation()  + fileName, workDir.resolve(fileName));
        }
  
        processBuilder.directory(workDir.toFile());
        String cmds = String.join(" ", processBuilder.command());
        processBuilder.redirectErrorStream(true);
        Process p = processBuilder.start();
        int result = p.waitFor();
        if (result != 0) {
            System.err.println("Compilation of additional sources failed with result = " + result);
            System.err.println("Original command was "+cmds);
            printFromInputStream(p.getInputStream());
            return false;
        } // we need more checks (e.g. do launcher.o and thread.o exist?)
        return true;
    }

    private String getJniPlatform(Triplet target) {
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
                return "DARWIN_AARCH64";
            case Constants.OS_DARWIN:
                return "DARWIN_AMD64";
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
        if (!Files.exists(clibPath)) {
            String url = Strings.substitute(URL_CLIBS_ZIP, Map.of("osarch", target.getOsArch()));
            FileOps.downloadAndUnzip(url,
                    clibPath.getParent().getParent(),
                    "clibraries.zip",
                    "clibraries",
                    target.getOsArch2());
        }
        if (!Files.exists(clibPath)) throw new IOException("No clibraries found for the required architecture in "+clibPath);
        checkPlatformSpecificClibs(clibPath);
    }

    protected Path getCLibPath() {
        Triplet target = projectConfiguration.getTargetTriplet();
        return projectConfiguration.getGraalPath()
                .resolve("lib")
                .resolve("svm")
                .resolve("clibraries")
                .resolve(target.getOsArch2());
    }

    private void addGraalStaticLibsPathToLinkProcess(ProcessBuilder linkBuilder) {
        linkBuilder.command().add(getLinkLibraryPathOption() + getCLibPath());
    }

    private void addJavaStaticLibsPathToLinkProcess(ProcessBuilder linkBuilder) throws IOException {
        Path javaSDKPath = fileDeps.getJavaSDKLibsPath(useGraalVMJavaStaticLibraries());
        linkBuilder.command().add(getLinkLibraryPathOption() + javaSDKPath);
    }

    private void addJavaFXStaticLibsPathToLinkProcess(ProcessBuilder linkBuilder) throws IOException {
        Path javafxSDKPath = fileDeps.getJavaFXSDKLibsPath();
        linkBuilder.command().add(getLinkLibraryPathOption() + javafxSDKPath);
    }

    private void asynPrintFromInputStream (InputStream inputStream) {
        Thread t = new Thread(() -> {
            try {
                printFromInputStream(inputStream);
            } catch (IOException e) {
                e.printStackTrace();
            }
        });
        t.start();
    }

    private void printFromInputStream(InputStream inputStream) throws IOException {
        BufferedReader br = new BufferedReader(new InputStreamReader(inputStream));
        String l = br.readLine();
        while (l != null) {
            System.err.println(l);
            l = br.readLine();
        }
    }

    private String getNativeImagePath() {
        return projectConfiguration.getGraalPath()
                .resolve("bin")
                .resolve(getNativeImageCommand())
                .toString();
    }

    private Process startAppProcess( Path appPath, String appName ) throws IOException {
        ProcessBuilder runBuilder = new ProcessBuilder(appPath.resolve(appName).toString());
        runBuilder.redirectErrorStream(true);
        return runBuilder.start();
    }

    private List<String> getReflectionClassList(String suffix, boolean useJavaFX, boolean usePrismSW) {
        List<String> answer = new LinkedList<>();
        answer.add(Constants.REFLECTION_JAVA_FILE);
        if (useJavaFX) {
            answer.add(Constants.REFLECTION_JAVAFX_FILE);
            answer.add(Strings.substitute(Constants.REFLECTION_JAVAFX_ARCH_FILE, Map.of("archOs", suffix)));

            if (usePrismSW) {
                answer.add(Constants.REFLECTION_JAVAFXSW_FILE);
            }
        }
        return answer;
    }

    private List<String> getJNIClassList(String suffix, boolean useJavaFX, boolean usePrismSW) {
        List<String> answer = new LinkedList<>();
        answer.add(Constants.JNI_JAVA_FILE);
        if (useJavaFX) {
            answer.add(Constants.JNI_JAVAFX_FILE);
            answer.add(Strings.substitute(Constants.JNI_JAVAFX_ARCH_FILE, Map.of("archOs", suffix)));
            if (usePrismSW) {
                answer.add(Constants.JNI_JAVAFXSW_FILE);
            }
        }
        return answer;
    }

    private List<String> getBundlesList() {
        List<String> list = new ArrayList<>(projectConfiguration.getBundlesList());
        if (projectConfiguration.isUseJavaFX()) {
            list.addAll(BUNDLES_LIST);
        }
        return list;
    }

    private Path createReflectionConfig(String suffix) throws IOException {
        Path gvmPath = paths.getGvmPath();
        Path reflectionPath = gvmPath.resolve(
                Strings.substitute( Constants.REFLECTION_ARCH_FILE, Map.of("archOs", suffix)));
        Files.deleteIfExists(reflectionPath);
        try (BufferedWriter bw = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(reflectionPath.toFile())))) {
            bw.write("[\n");
            writeSingleEntry(bw, projectConfiguration.getMainClassName(), false);
            for (String javaFile : getReflectionClassList(suffix, projectConfiguration.isUseJavaFX(), projectConfiguration.isUsePrismSW())) {
                bw.write(",\n");
                InputStream inputStream = AbstractTargetConfiguration.class.getResourceAsStream(Constants.CONFIG_FILES + javaFile);
                if (inputStream == null) {
                    throw new IOException("Missing a reflection configuration file named "+javaFile);
                }
                List<String> lines = FileOps.readFileLines(inputStream,
                        line -> !line.startsWith("[") && !line.startsWith("]"));
                for (String line : lines) {
                    bw.write(line + "\n");
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

    private Path createJNIConfig(String suffix) throws IOException {
        Path gvmPath = paths.getGvmPath();
        Path jniPath = gvmPath.resolve(Strings.substitute(Constants.JNI_ARCH_FILE, Map.of("archOs", suffix)));
        Files.deleteIfExists(jniPath);
        try (BufferedWriter bw = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(jniPath.toFile())))) {
            bw.write("[\n");
            bw.write("  {\n    \"name\" : \"" + projectConfiguration.getMainClassName() + "\"\n  }\n");
            for (String javaFile : getJNIClassList(suffix, projectConfiguration.isUseJavaFX(), projectConfiguration.isUsePrismSW())) {
                bw.write(",\n");
                InputStream inputStream = AbstractTargetConfiguration.class.getResourceAsStream(Constants.CONFIG_FILES + javaFile);
                if (inputStream == null) {
                    throw new IOException("Missing a jni configuration file named "+javaFile);
                }
                List<String> lines = FileOps.readFileLines(inputStream,
                        line -> !line.startsWith("[") && !line.startsWith("]"));
                for (String line : lines) {
                    bw.write(line + "\n");
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

    private Path createResourceConfig(String suffix) throws IOException {
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
            if (patternHasBeenWritten) {
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

    private static void writeSingleEntry (BufferedWriter bw, String javaClass, boolean exclude) throws IOException {
        bw.write("  {\n");
        bw.write("    \"name\" : \"" + javaClass + "\"");
        if (! exclude) {
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
     * For every jar in the classpath, checks for native libraries (*.a)
     * and if found, extracts them to a folder, for later link
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
            FileOps.extractFilesFromJar(".a", Path.of(jar), libPath, getTargetSpecificNativeLibsFilter());
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
            linkFlags.add("-L" + libPath.toString());
            List<String> libs;
            try (Stream<Path> files = Files.list(libPath)) {
                libs = files.map(p -> p.getFileName().toString())
                        .filter(s -> s.startsWith("lib") && s.endsWith(".a"))
                        .collect(Collectors.toList());
            }
            linkFlags.addAll(getTargetSpecificNativeLibsFlags(libPath, libs));
        }
        return linkFlags;
    }

    // --- package protected methods

    /*
     * Returns the path to an llc compiler
     * First, the projectConfiguration is checked for llcPath.
     * If that property is set, it will be used. If the property is set, but the llc compiler is not at the
     * pointed location or is not working, an IllegalArgumentException will be thrown.
     *
     * If there is no llcPath property in the projectConfiguration, the file cache is checked for an llc version
     * that works for the current architecture.
     * If there is no llc in the file cache, it is retrieved from the download site, and added to the cache.
     */
    Path getLlcPath() throws IOException {
        if (projectConfiguration.getLlcPath() != null) {
            Path llcPath = Path.of(projectConfiguration.getLlcPath());
            if (!Files.exists(llcPath)) {
                throw new IllegalArgumentException("Configuration points to an llc that does not exist: "+llcPath);
            } else {
                return llcPath;
            }
        }
        // there is no pre-configured llc, search it in the cache, or populare the cache
        Path llcPath = fileDeps.getLlcPath();
        return llcPath;
    }

    // Methods below with default implementation, can be overridden by subclasses

    String processClassPath(String cp) throws IOException {
        return cp;
    }

    // by default, we allow the HTTPS protocol, but subclasses can decide against it.
    boolean allowHttps() {
        return true;
    }

    boolean useGraalVMJavaStaticLibraries() {
        return true;
    }

    /**
     * Allow platforms to check if specific libraries (e.g. libjvm.a) are present in the specified clib path
     * @param clibPath
     */
    void checkPlatformSpecificClibs(Path clibPath) throws IOException {}

    String getAdditionalSourceFileLocation() {
        return "/native/linux/";
    }

    List<String> getAdditionalSourceFiles() {
        return defaultAdditionalSourceFiles;
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

    String getLinkLibraryPathOption() {
        return "-L";
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

    List<String> getTargetSpecificLinkLibraries() {
        return Arrays.asList("-ljava", "-lnio", "-lzip", "-lnet", "-lprefs", "-ljvm", "-lstrictmath", "-lz", "-ldl",
                "-lj2pkcs11", "-lsunec");
    }

    List<String> getTargetSpecificLinkOutputFlags() {
        String appName = projectConfiguration.getAppName();
        return Arrays.asList("-o", getAppPath(appName));
    }

    protected List<String> getNativeCodeList() throws IOException {
        Path nativeCodeDir = paths.getNativeCodePath();
        if (!Files.exists(nativeCodeDir))
            return Collections.emptyList();
        return Files.list(nativeCodeDir)
            .map(p -> p.getFileName().toString())
            .filter(s -> s.endsWith(".c"))
            .collect(Collectors.toList());
    }

    List<String> getTargetSpecificLinkFlags(boolean useJavaFX, boolean usePrismSW) {
        return Collections.emptyList();
    }

    List<String> getTargetSpecificCCompileFlags() {
        return Collections.emptyList();
    }

    List<String> getTargetSpecificAOTCompileFlags() throws IOException {
        return Collections.emptyList();
    }

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

    /**
     * Generates a list with class names that should be added to the
     * initialize in build time flag
     *
     * @return a list with fully qualified class names
     */
    private List<String> getInitializeAtBuildTimeList(String suffix) throws IOException {
        List<String> list = new ArrayList<>(projectConfiguration.getInitBuildTimeList());
        list.addAll(configResolver.getUserInitBuildTimeList(suffix));
        return list;
    }
    
}
