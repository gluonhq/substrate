/*
 * Copyright (c) 2019, 2020, Gluon
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

import com.gluonhq.substrate.model.ClassPath;
import com.gluonhq.substrate.model.InternalProjectConfiguration;
import com.gluonhq.substrate.model.ProcessPaths;
import com.gluonhq.substrate.model.Triplet;
import com.gluonhq.substrate.target.AndroidTargetConfiguration;
import com.gluonhq.substrate.target.MacOSTargetConfiguration;
import com.gluonhq.substrate.target.IosTargetConfiguration;
import com.gluonhq.substrate.target.LinuxTargetConfiguration;
import com.gluonhq.substrate.target.TargetConfiguration;
import com.gluonhq.substrate.target.WindowsTargetConfiguration;
import com.gluonhq.substrate.util.Strings;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Objects;
import java.util.Optional;

import static com.gluonhq.substrate.util.Logger.logInit;
import static com.gluonhq.substrate.util.Logger.title;

public class SubstrateDispatcher {

    private static volatile boolean run = true;

    public static void main(String[] args) throws Exception {

        String classPath = requireArg("imagecp","Use -Dimagecp=/path/to/classes");
        String graalVM   = requireArg( "graalvm","Use -Dgraalvm=/path/to/graalvm");

        String mainClass = requireArg( "mainclass", "Use -Dmainclass=main.class.name" );
        String appId     = Optional.ofNullable(System.getProperty("appId")).orElse("com.gluonhq.anonymousApp");
        String appName   = Optional.ofNullable(System.getProperty("appname")).orElse("anonymousApp");
        String targetProfile = System.getProperty("targetProfile");

        Triplet targetTriplet = targetProfile != null? new Triplet(Constants.Profile.valueOf(targetProfile.toUpperCase()))
                :Triplet.fromCurrentOS();

        String expected  = System.getProperty("expected");
        boolean verbose = true;

        ProjectConfiguration config = new ProjectConfiguration(mainClass);
        config.setGraalPath(Path.of(graalVM));
        config.setAppId(appId);
        config.setAppName(appName);
        config.setTarget(targetTriplet);
        config.setReflectionList(Strings.split(System.getProperty("reflectionlist")));
        config.setJniList(Strings.split(System.getProperty("jnilist")));
        config.setBundlesList(Strings.split(System.getProperty("bundleslist")));
        config.setVerbose(verbose);
        config.setUsePrismSW(Boolean.parseBoolean(System.getProperty("prism.sw", "false")));
        config.setUsePrecompiledCode(Boolean.parseBoolean(System.getProperty("usePrecompiledCode", "true")));

        Path buildRoot = Paths.get(System.getProperty("user.dir"), "build", "autoclient");

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

        SubstrateDispatcher dispatcher = new SubstrateDispatcher(buildRoot, config);

        boolean nativeCompileSucceeded = dispatcher.nativeCompile(classPath);
        run = false;
        if (!nativeCompileSucceeded) {
            System.err.println("Compiling failed");
            System.exit(1);
        }

        try {
            System.err.println("Linking...");
            if (!dispatcher.nativeLink(classPath)) {
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
            String response = dispatcher.targetConfiguration.run(dispatcher.paths.getAppPath(), appName);
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

    private final InternalProjectConfiguration config;
    private final ProcessPaths paths;
    private final TargetConfiguration targetConfiguration;


    /**
     * Dispatches calls to different process steps. Uses shared build root path and project configuration
     * @param buildRoot the root, relative to which the compilation step can create object files and temporary files
     * @param config the ProjectConfiguration, including the target triplet
     */
    public SubstrateDispatcher(Path buildRoot, ProjectConfiguration config) throws IOException {
        this.config = new InternalProjectConfiguration(config);
        this.paths = new ProcessPaths(Objects.requireNonNull(buildRoot), config.getTargetTriplet().getArchOs());
        Triplet targetTriplet = config.getTargetTriplet();
        this.targetConfiguration = Objects.requireNonNull(getTargetConfiguration(targetTriplet),
                "Error: Target Configuration was not found for " + targetTriplet);
    }

    private TargetConfiguration getTargetConfiguration(Triplet targetTriplet) throws IOException {
        switch (targetTriplet.getOs()) {
            case Constants.OS_LINUX  : return new LinuxTargetConfiguration(paths, config);
            case Constants.OS_DARWIN : return new MacOSTargetConfiguration(paths, config);
            case Constants.OS_WINDOWS: return new WindowsTargetConfiguration(paths, config);
            case Constants.OS_IOS    : return new IosTargetConfiguration(paths, config);
            case Constants.OS_ANDROID: return new AndroidTargetConfiguration(paths, config);
            default                  : return null;
        }
    }


    /**
     * This method will start native compilation for the specified configuration. The classpath needs
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
        boolean useJavaFX = new ClassPath(classPath).contains( s -> s.contains("javafx"));
        config.setUseJavaFX(useJavaFX);

        Triplet targetTriplet  = config.getTargetTriplet();
        if (!config.getHostTriplet().canCompileTo(targetTriplet)) {
            throw new IllegalArgumentException("We currently can't compile to "+targetTriplet+" when running on "+config.getHostTriplet());
        }

        logInit(paths.getLogPath().toString(), title("COMPILE TASK"),  config.isVerbose());
        System.err.println("We will now compile your code for "+targetTriplet.toString()+". This may take some time.");
        boolean compilationSuccess = targetConfiguration.compile(classPath);
        System.err.println(compilationSuccess? "Compilation succeeded.": "Compilation failed. See error printed above.");
        return compilationSuccess;
    }

    /**
     * This method will start native linking for the specified configuration, after {@link #nativeCompile(String)}
     * was called and ended successfully.
     * The classpath needs to be provided separately.
     * The result of linking is a at least an native image application file.
     * This method returns <code>true</code> on successful linking and <code>false</code> when linking fails
     * @param classPath the classpath needed to link the application (this is not the classpath for native-image)
     * @return true if linking succeeded, false if it fails
     * @throws Exception
     * @throws IllegalArgumentException when the supplied configuration contains illegal combinations
     */
    public boolean nativeLink(String classPath) throws IOException, InterruptedException {
        logInit(paths.getLogPath().toString(), title("LINK TASK"), config.isVerbose());
        boolean useJavaFX = new ClassPath(classPath).contains(s -> s.contains("javafx"));
        config.setUseJavaFX(useJavaFX);
        return targetConfiguration.link();
    }

    /**
     * This method runs the native image application, that was created after {@link #nativeLink(String)}
     * was called and ended successfully.
     * @throws IOException
     * @throws IllegalArgumentException when the supplied configuration contains illegal combinations
     */
    public void nativeRun() throws IOException, InterruptedException {
        logInit(paths.getLogPath().toString(), title("RUN TASK"), config.isVerbose());
        targetConfiguration.runUntilEnd();
    }

    /**
     * This methods creates a package of the native image application, that was created after {@link #nativeLink(String)}
     * was called and ended successfully.
     * @throws IOException
     * @throws InterruptedException
     */
    public void nativePackage() throws IOException, InterruptedException {
        logInit(paths.getLogPath().toString(), title("PACKAGE TASK"), config.isVerbose());
        targetConfiguration.packageApp();
    }
}
