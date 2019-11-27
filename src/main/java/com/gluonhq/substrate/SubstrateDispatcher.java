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
package com.gluonhq.substrate;

import com.gluonhq.substrate.model.ProcessPaths;
import com.gluonhq.substrate.model.ProjectConfiguration;
import com.gluonhq.substrate.model.Triplet;
import com.gluonhq.substrate.target.TargetConfiguration;
import com.gluonhq.substrate.util.Logger;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Stream;

public class SubstrateDispatcher {

    private static volatile boolean run = true;

    public static void main(String[] args) throws Exception {

        String classPath = requireArg("imagecp","Use -Dimagecp=/path/to/classes");
        String graalVM   = requireArg( "graalvm","Use -Dgraalvm=/path/to/graalvm");
        String mainClass = requireArg( "mainclass", "Use -Dmainclass=main.class.name" );
        String appName   = Optional.ofNullable(System.getProperty("appname")).orElse("anonymousApp");
        String reflectionList = System.getProperty("reflectionlist");
        String jniList = System.getProperty("jnilist");
        String bundlesList = System.getProperty("bundleslist");
        String targetProfile = System.getProperty("targetProfile");
        boolean usePrismSW = Boolean.parseBoolean(System.getProperty("prism.sw", "false"));
        boolean skipCompile = Boolean.parseBoolean(System.getProperty("skipcompile", "false"));
        boolean skipSigning = Boolean.parseBoolean(System.getProperty("skipsigning", "false"));
        String staticLibs = System.getProperty("javalibspath");
        String staticJavaFXSDK = System.getProperty("javafxsdk");

        String expected  = System.getProperty("expected");

        Triplet targetTriplet = targetProfile != null? new Triplet(Constants.Profile.valueOf(targetProfile.toUpperCase()))
                :Triplet.fromCurrentOS();

        ProjectConfiguration config = new ProjectConfiguration();
        config.setGraalPath(graalVM);
        config.setMainClassName(mainClass);
        config.setAppName(appName);
        config.setJavafxStaticSdkVersion(Constants.DEFAULT_JAVAFX_STATIC_SDK_VERSION);
        config.setTarget(targetTriplet);
        config.setUsePrismSW(usePrismSW);
        config.getIosSigningConfiguration().setSkipSigning(skipSigning);
        Optional.ofNullable(staticLibs).ifPresent(config::setJavaStaticLibs);
        Optional.ofNullable(staticJavaFXSDK).ifPresent(config::setJavaFXStaticSDK);
        if (reflectionList != null && !reflectionList.trim().isEmpty()) {
            config.setReflectionList(Arrays.asList(reflectionList.split(",")));
        }
        if (jniList != null && !jniList.trim().isEmpty()) {
            config.setJniList(Arrays.asList(jniList.split(",")));
        }
        if (bundlesList != null && !bundlesList.trim().isEmpty()) {
            config.setBundlesList(Arrays.asList(bundlesList.split(",")));
        }
        TargetConfiguration targetConfiguration = Objects.requireNonNull(targetTriplet.getConfiguration(),
                "Error: Target Configuration was null");
        Path buildRoot = Paths.get(System.getProperty("user.dir"), "build", "autoclient");
        ProcessPaths paths = new ProcessPaths(buildRoot, targetTriplet.getArchOs());

        Thread timer = new Thread(() -> {
            int counter = 1;
            while (run) {
                try {
                    Thread.sleep(60000);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                System.err.println("NativeCompile is still running, please hold [" + counter++ + " minute(s)]");
            }
        });
        timer.setDaemon(true);
        timer.start();

        var dispatcher = new SubstrateDispatcher(buildRoot, config);

        boolean nativeCompileSucceeded = dispatcher.nativeCompile(classPath);
        run = false;
        if (!nativeCompileSucceeded) {
            System.err.println("Compiling failed");
            System.exit(1);
        }

        try {
            System.err.println("Linking...");
            if (!dispatcher.nativeLink()) {
                System.err.println("Linking failed");
                System.exit(1);
            }
        } catch (Throwable t) {
            System.err.println("Linking failed with an exception");
            t.printStackTrace();
            System.exit(1);
        }
        System.err.println("Running...");
        if (expected != null) {
            String response = targetConfiguration.run(paths.getAppPath(), appName);
            if (expected.equals(response)) {
                System.err.println("Run ended successfully, the output: " + expected + " matched the expected result.");
            } else {
                System.err.println("Run failed, expected output: " + expected + ", output: " + response);
                System.exit(1);
            }
        } else {
            dispatcher.nativeRun();
        }
    }

    private static String requireArg(String argName, String errorMessage ) {
        String arg = System.getProperty(argName);
        if (arg == null || arg.trim().isEmpty()) {
            printUsage();
            throw new IllegalArgumentException( String.format("No '%s' specified. %s", argName, errorMessage));
        }
        return arg;
    }

    private static void printUsage() {
        System.err.println("Usage:\n java -Dimagecp=... -Dgraalvm=... -Dmainclass=... com.gluonhq.substrate.SubstrateDispatcher");
    }


    private final Path buildRoot;
    private final ProjectConfiguration config;

    /**
     * Dispatches calls to different process steps. Uses shared build root path and project configuration
     * @param buildRoot the root, relative to which the compilation step can create object files and temporary files
     * @param config the ProjectConfiguration, including the target triplet
     */
    public SubstrateDispatcher(Path buildRoot, ProjectConfiguration config) {
        this.buildRoot = Objects.requireNonNull(buildRoot);
        this.config = Objects.requireNonNull(config);
    }

    /**
     * This method will start native compilation for the specified configuration. The classpath and the buildroot need
     * to be provided separately.
     * The result of compilation is a at least one native file (2 files in case LLVM backend is used).
     * This method returns <code>true</code> on successful compilation and <code>false</code> when compilations fails
     * @param classPath the classpath needed to compile the application (this is not the classpath for native-image)
     * @return true if compilation succeeded, false if it fails
     * @throws Exception
     * @throws IllegalArgumentException when the supplied configuration contains illegal combinations
     */
     public boolean nativeCompile(String classPath) throws Exception {
         config.canRunNativeImage();
        if (classPath != null) {
            boolean useJavaFX = Stream.of(classPath.split(File.pathSeparator))
                    .anyMatch(s -> s.contains("javafx"));
            config.setUseJavaFX(useJavaFX);
        }

        Triplet targetTriplet  = config.getTargetTriplet();
        if (!config.getHostTriplet().canCompileTo(targetTriplet)) {
            throw new IllegalArgumentException("We currently can't compile to "+targetTriplet+" when running on "+config.getHostTriplet());
        }
        TargetConfiguration targetConfiguration = targetTriplet.getConfiguration();
        if (targetConfiguration == null) {
            throw new IllegalArgumentException("We don't have a configuration to compile "+targetTriplet);
        }
        ProcessPaths paths = new ProcessPaths(buildRoot, targetTriplet.getArchOs());
        Logger.logInit(paths.getLogPath().toString(), "==================== COMPILE TASK ====================",
                config.isVerbose());
        System.err.println("We will now compile your code for "+targetTriplet.toString()+". This may take some time.");
        boolean compile = targetConfiguration.compile(paths, config, classPath);
        if (compile) {
            System.err.println("Compilation succeeded.");
        } else {
            System.err.println("Compilation failed. The error should be printed above.");
        }
        return compile;
    }

    public boolean nativeLink() throws IOException, InterruptedException {
        Triplet targetTriplet  = config.getTargetTriplet();
        TargetConfiguration targetConfiguration = targetTriplet.getConfiguration();
        if (targetConfiguration == null) {
            throw new IllegalArgumentException("We don't have a configuration to link " + targetTriplet);
        }
        ProcessPaths paths = new ProcessPaths(buildRoot, targetTriplet.getArchOs());
        Logger.logInit(paths.getLogPath().toString(), "==================== LINK TASK ====================",
                config.isVerbose());
        return targetConfiguration.link(paths, config);
    }

    public void nativeRun() throws IOException, InterruptedException {
        Triplet targetTriplet  = config.getTargetTriplet();
        TargetConfiguration targetConfiguration = Objects.requireNonNull(targetTriplet.getConfiguration(), "Target Configuration was null");
        ProcessPaths paths = new ProcessPaths(buildRoot, targetTriplet.getArchOs());
        Logger.logInit(paths.getLogPath().toString(), "==================== RUN TASK ====================",
                config.isVerbose());
        targetConfiguration.runUntilEnd(paths, config);
    }

}
