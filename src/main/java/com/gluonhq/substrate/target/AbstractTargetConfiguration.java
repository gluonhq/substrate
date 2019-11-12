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
import com.gluonhq.substrate.model.ProcessPaths;
import com.gluonhq.substrate.model.ProjectConfiguration;
import com.gluonhq.substrate.model.Triplet;
import com.gluonhq.substrate.util.FileOps;
import com.gluonhq.substrate.util.ProcessRunner;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
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
import java.util.Objects;
import java.util.stream.Collectors;

public abstract class AbstractTargetConfiguration implements TargetConfiguration {

    ProjectConfiguration projectConfiguration;
    ProcessPaths paths;

    private List<String> defaultAdditionalSourceFiles = Arrays.asList("launcher.c");

    String processClassPath(String cp) throws IOException {
        return cp;
    }

    @Override
    public boolean compile(ProcessPaths paths, ProjectConfiguration config, String cp) throws IOException, InterruptedException {
        this.projectConfiguration = config;
        String classPath = processClassPath(cp);
        this.paths = paths;
        Triplet target =  config.getTargetTriplet();
        String suffix = target.getArchOs();
        String jniPlatform = getJniPlatform(target.getOs());
        if (!compileAdditionalSources()) {
            return false;
        }
        Path gvmPath = paths.getGvmPath();
        FileOps.rmdir(paths.getTmpPath());
        String tmpDir = paths.getTmpPath().toFile().getAbsolutePath();
        String mainClassName = config.getMainClassName();
        if (mainClassName == null || mainClassName.isEmpty()) {
            throw new IllegalArgumentException("No main class is supplied. Cannot compile.");
        }
        if (cp == null || cp.isEmpty()) {
            throw new IllegalArgumentException("No classpath specified. Cannot compile");
        }
        String nativeImage = getNativeImagePath(config);
        ProcessBuilder compileBuilder = new ProcessBuilder(nativeImage);
        compileBuilder.command().add("--report-unsupported-elements-at-runtime");
        compileBuilder.command().add("-Djdk.internal.lambda.eagerlyInitialize=false");
        compileBuilder.command().add("-H:+ExitAfterRelocatableImageWrite");
        compileBuilder.command().add("-H:TempDirectory="+tmpDir);
        compileBuilder.command().add("-H:+SharedLibrary");
        compileBuilder.command().add("-H:ReflectionConfigurationFiles=" + createReflectionConfig(suffix));
        compileBuilder.command().add("-H:JNIConfigurationFiles=" + createJNIConfig(suffix));
        compileBuilder.command().addAll(getResources());
        compileBuilder.command().addAll(getTargetSpecificAOTCompileFlags());
        if (!getBundlesList().isEmpty()) {
            String bundles = String.join(",", getBundlesList());
            System.out.println("bundles = " + bundles);
            compileBuilder.command().add("-H:IncludeResourceBundles=" + bundles);
        }
        compileBuilder.command().add("-Dsvm.platform=org.graalvm.nativeimage.Platform$"+jniPlatform);
        compileBuilder.command().add("-cp");
        compileBuilder.command().add(classPath);
        compileBuilder.command().add(mainClassName);
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
            String nameSearch = mainClassName.toLowerCase()+".o";
            Path p = FileOps.findFile(gvmPath, nameSearch);
            if (p == null) {
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

    private String getJniPlatform( String os ) {
        switch (os) {
            case Constants.OS_LINUX: return "LINUX_AMD64";
            case Constants.OS_IOS:return "DARWIN_AARCH64";
            case Constants.OS_DARWIN: return "DARWIN_AMD64";
            default: throw new IllegalArgumentException("No support yet for " + os);
        }
    }

    @Override
    public boolean link(ProcessPaths paths, ProjectConfiguration projectConfiguration) throws IOException, InterruptedException {

        if (!Files.exists(projectConfiguration.getJavaStaticLibsPath())) {
            System.err.println("We can't link because the static Java libraries are missing. " +
                    "The path "+ projectConfiguration.getJavaStaticLibsPath() + " does not exist.");
            return false;
        }

        this.paths = paths;
        this.projectConfiguration = projectConfiguration;
        String appName = projectConfiguration.getAppName();
        String objectFilename = projectConfiguration.getMainClassName().toLowerCase()+".o";
        Triplet target = projectConfiguration.getTargetTriplet();
        Path gvmPath = paths.getGvmPath();
        Path objectFile = FileOps.findFile(gvmPath, objectFilename);
        if (objectFile == null) {
            throw new IllegalArgumentException("Linking failed, since there is no objectfile named "+objectFilename+" under "
                    +gvmPath.toString());
        }
        ProcessBuilder linkBuilder = new ProcessBuilder(getLinker());

        linkBuilder.command().add("-o");
        linkBuilder.command().add(getAppPath(appName));

        Path gvmAppPath = gvmPath.resolve(appName);
        getAdditionalSourceFiles()
              .forEach( r -> linkBuilder.command().add(
                      gvmAppPath.resolve(r.replaceAll("\\..*", ".o")).toString()));

        linkBuilder.command().add(objectFile.toString());
        linkBuilder.command().addAll(getTargetSpecificObjectFiles());
        linkBuilder.command().add("-L" + projectConfiguration.getJavaStaticLibsPath());
        if (projectConfiguration.isUseJavaFX()) {
            linkBuilder.command().add("-L" + projectConfiguration.getJavafxStaticLibsPath());
        }
        linkBuilder.command().add("-L"+ Path.of(projectConfiguration.getGraalPath(), "lib", "svm", "clibraries", target.getOsArch2())); // darwin-amd64");
        linkBuilder.command().add("-ljava");
        linkBuilder.command().add("-ljvm");
        linkBuilder.command().add("-llibchelper");
        linkBuilder.command().add("-lnio");
        linkBuilder.command().add("-lzip");
        linkBuilder.command().add("-lnet");
        linkBuilder.command().add("-lstrictmath");
        linkBuilder.command().add("-lpthread");
        linkBuilder.command().add("-lz");
        linkBuilder.command().add("-ldl");
        linkBuilder.command().addAll(getTargetSpecificLinkFlags(projectConfiguration.isUseJavaFX(), projectConfiguration.isUsePrismSW()));
        linkBuilder.redirectErrorStream(true);
        String cmds = String.join(" ", linkBuilder.command());
        System.err.println("cmd = "+cmds);
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

    private static String getNativeImagePath (ProjectConfiguration configuration) {
        String graalPath = configuration.getGraalPath();
        Path path = Path.of(graalPath, "bin", "native-image");
        return path.toString();
    }

    private Process startAppProcess( Path appPath, String appName ) throws IOException {
        ProcessBuilder runBuilder = new ProcessBuilder(appPath.resolve(appName).toString());
        runBuilder.redirectErrorStream(true);
        return runBuilder.start();
    }

    public boolean compileAdditionalSources()
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
        for( String fileName: getAdditionalSourceFiles() ) {
            FileOps.copyResource(getAdditionalSourceFileLocation()  + fileName, workDir.resolve(fileName));
            processBuilder.command().add(fileName);
        }
        processBuilder.command().addAll(getTargetSpecificCCompileFlags());
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

    @Override
    public boolean runUntilEnd(ProcessPaths paths, ProjectConfiguration projectConfiguration) throws IOException, InterruptedException {
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

    private List<String> getReflectionClassList(String suffix, boolean useJavaFX, boolean usePrismSW) {
        List<String> answer = new LinkedList<>();
        answer.add(Constants.REFLECTION_JAVA_FILE);
        if (useJavaFX) {
            answer.add(Constants.REFLECTION_JAVAFX_FILE);
            answer.add(Constants.REFLECTION_JAVAFX_ARCH_FILE
                    .replace("${archOs}", suffix));
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
            answer.add(Constants.JNI_JAVAFX_ARCH_FILE
                    .replace("${archOs}", suffix));
            if (usePrismSW) {
                answer.add(Constants.JNI_JAVAFXSW_FILE);
            }
        }
        return answer;
    }

    private static final List<String> resourcesList = Arrays.asList(
            "frag", "fxml", "css", "gls", "ttf",
            "png", "jpg", "jpeg", "gif", "bmp",
            "license", "json");

    private  List<String> getResources() {
        List<String> resources = new ArrayList<>(resourcesList);
        resources.addAll(projectConfiguration.getResourcesList());

        List<String> list = resources.stream()
                .map(s -> "-H:IncludeResources=.*/.*" + s + "$")
                .collect(Collectors.toList());
        list.addAll(resources.stream()
                .map(s -> "-H:IncludeResources=.*" + s + "$")
                .collect(Collectors.toList()));
        return list;
    }

    private static final List<String> bundlesList = new ArrayList<>(Arrays.asList(
            "com/sun/javafx/scene/control/skin/resources/controls",
            "com.sun.javafx.tk.quantum.QuantumMessagesBundle"
    ));

    private List<String> getBundlesList() {
        List<String> list = new ArrayList<>(projectConfiguration.getBundlesList());
        if (projectConfiguration.isUseJavaFX()) {
            list.addAll(bundlesList);
        }
        return list;
    }

    private Path createReflectionConfig(String suffix) throws IOException {
        Path gvmPath = paths.getGvmPath();
        Path reflectionPath = gvmPath.resolve(Constants.REFLECTION_ARCH_FILE
                .replace("${archOs}", suffix));
        File f = reflectionPath.toFile();
        if (f.exists()) {
            f.delete();
        }
        try (BufferedWriter bw = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(f)))) {
            bw.write("[\n");
            writeSingleEntry(bw, projectConfiguration.getMainClassName(), false);
            for (String javaFile : getReflectionClassList(suffix, projectConfiguration.isUseJavaFX(), projectConfiguration.isUsePrismSW())) {
                bw.write(",\n");
                List<String> lines = FileOps.readFileLines(AbstractTargetConfiguration.class
                        .getResourceAsStream(Constants.CONFIG_FILES + javaFile),
                        line -> !line.startsWith("[") && !line.startsWith("]"));
                for (String line : lines) {
                    bw.write(line + "\n");
                }
            }
            for (String javaClass : projectConfiguration.getReflectionList()) {
                bw.write(",\n");
                writeSingleEntry(bw, javaClass, false);
            }
            bw.write("]");
        }
        return reflectionPath;
    }

    private Path createJNIConfig(String suffix) throws IOException {
        Path gvmPath = paths.getGvmPath();
        Path jniPath = gvmPath.resolve(Constants.JNI_ARCH_FILE
                .replace("${archOs}", suffix));
        File f = jniPath.toFile();
        if (f.exists()) {
            f.delete();
        }
        try (BufferedWriter bw = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(f)))) {
            bw.write("[\n");
            bw.write("  {\n    \"name\" : \"" + projectConfiguration.getMainClassName() + "\"\n  }\n");
            for (String javaFile : getJNIClassList(suffix, projectConfiguration.isUseJavaFX(), projectConfiguration.isUsePrismSW())) {
                bw.write(",\n");
                List<String> lines = FileOps.readFileLines(AbstractTargetConfiguration.class
                        .getResourceAsStream(Constants.CONFIG_FILES + javaFile),
                        line -> !line.startsWith("[") && !line.startsWith("]"));
                for (String line : lines) {
                    bw.write(line + "\n");
                }
            }
            bw.write("]");
        }
        return jniPath;
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

    // Default settings below, can be overridden by subclasses

    String getAdditionalSourceFileLocation() {
        return "/native/linux/";
    }


    List<String> getAdditionalSourceFiles() {
        return defaultAdditionalSourceFiles;
    }

    String getCompiler() {
        return "gcc";
    }

    String getLinker() {
        return "gcc";
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

}
