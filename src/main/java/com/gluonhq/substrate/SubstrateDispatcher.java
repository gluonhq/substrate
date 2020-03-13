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

import com.gluonhq.substrate.model.InternalProjectConfiguration;
import com.gluonhq.substrate.model.ProcessPaths;
import com.gluonhq.substrate.model.Triplet;
import com.gluonhq.substrate.target.AndroidTargetConfiguration;
import com.gluonhq.substrate.target.MacOSTargetConfiguration;
import com.gluonhq.substrate.target.IosTargetConfiguration;
import com.gluonhq.substrate.target.LinuxTargetConfiguration;
import com.gluonhq.substrate.target.TargetConfiguration;
import com.gluonhq.substrate.target.WindowsTargetConfiguration;
import com.gluonhq.substrate.util.Logger;
import com.gluonhq.substrate.util.Strings;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

public class SubstrateDispatcher {

    /**
     * Define the different steps that can be handled by
     * the SubstrateDispatcher. The steps are only used when
     * the dispatcher is launched through the main method.
     */
    private enum Step {
        /**
         * The goal of the COMPILE step is to run GraalVM
         * native-image to generate a compiled object file.
         */
        COMPILE(),
        /**
         * The goal of the LINK step is to link all the
         * libraries into an executable or a shared library,
         * depending on the target platform.
         */
        LINK(COMPILE),
        /**
         * The goal of the PACKAGE step is to generate an
         * application package that can be distributed.
         */
        PACKAGE(LINK),
        /**
         * The goal of the INSTALL step is to install the
         * generated application package on a supported
         * target (either the host machine or an attached
         * device).
         */
        INSTALL(PACKAGE),
        /**
         * The goal of the RUN step is to run the installed
         * application. It might also take a shortcut and
         * directly run the executable that was produced by
         * the LINK step.
         */
        RUN(INSTALL);

        private final Step dep;

        Step() {
            this.dep = null;
        }

        Step(Step dep) {
            this.dep = dep;
        }

        /**
         * Checks if the provided <code>step</code> is required to be
         * executed for <code>this</code> step.
         *
         * @param step the step to check
         * @return <code>true</code> if the provided step needs to run
         */
        public boolean requires(Step step) {
            return this == step || (dep != null && dep.requires(step));
        }
    }

    private static volatile boolean compiling = true;

    public static void main(String[] args) throws IOException {
        Step step = getStepToExecute();

        Path buildRoot = Paths.get(System.getProperty("user.dir"), "build", "autoclient");
        ProjectConfiguration configuration = createProjectConfiguration();
        SubstrateDispatcher dispatcher = new SubstrateDispatcher(buildRoot, configuration);

        executeCompileStep(dispatcher);

        if (step.requires(Step.LINK)) {
            executeLinkStep(dispatcher);
        }

        if (step.requires(Step.PACKAGE)) {
            executePackageStep(dispatcher);
        }

        if (step.requires(Step.INSTALL)) {
            executeInstallStep(dispatcher);
        }

        if (step.requires(Step.RUN)) {
            executeRunStep(dispatcher);
        }
    }

    private static ProjectConfiguration createProjectConfiguration() {
        String classpath = requireSystemProperty("imagecp", "Use -Dimagecp=/path/to/classes");
        String graalVM = requireSystemProperty("graalvm", "Use -Dgraalvm=/path/to/graalvm");
        String mainClass = requireSystemProperty("mainclass", "Use -Dmainclass=main.class.name");

        String appId = Optional.ofNullable(System.getProperty("appId")).orElse("com.gluonhq.anonymousApp");
        String appName = Optional.ofNullable(System.getProperty("appname")).orElse("anonymousApp");
        boolean verbose = System.getProperty("verbose") != null;

        boolean usePrismSW = Boolean.parseBoolean(System.getProperty("prism.sw", "false"));
        boolean usePrecompiledCode = Boolean.parseBoolean(System.getProperty("usePrecompiledCode", "true"));

        String targetProfile = System.getProperty("targetProfile");
        Triplet targetTriplet = targetProfile != null ?
                new Triplet(Constants.Profile.valueOf(targetProfile.toUpperCase())) :
                Triplet.fromCurrentOS();

        ProjectConfiguration config = new ProjectConfiguration(mainClass, classpath);
        config.setGraalPath(Path.of(graalVM));
        config.setAppId(appId);
        config.setAppName(appName);
        config.setTarget(targetTriplet);
        config.setReflectionList(Strings.split(System.getProperty("reflectionlist")));
        config.setJniList(Strings.split(System.getProperty("jnilist")));
        config.setBundlesList(Strings.split(System.getProperty("bundleslist")));
        config.setVerbose(verbose);
        config.setUsePrismSW(usePrismSW);
        config.setUsePrecompiledCode(usePrecompiledCode);
        return config;
    }

    private static Step getStepToExecute() {
        return Optional.ofNullable(System.getProperty("step"))
                .map(stepProperty -> {
                    try {
                        return Step.valueOf(stepProperty.toUpperCase(Locale.ROOT));
                    } catch (IllegalArgumentException e) {
                        printUsage();
                        throw new IllegalArgumentException(String.format("Invalid value for 'step' specified. Possible values: %s", Arrays.toString(Step.values())));
                    }
                })
                .orElse(Step.RUN);
    }

    public static void executeCompileStep(SubstrateDispatcher dispatcher) {
        startNativeCompileTimer();

        try {
            boolean nativeCompileSucceeded = dispatcher.nativeCompile();
            compiling = false;

            if (!nativeCompileSucceeded) {
                Logger.logSevere("Compiling failed.");
                System.exit(1);
            }
        } catch (Throwable t) {
            Logger.logFatal(t, "Compiling failed with an exception.");
        }
    }

    private static void startNativeCompileTimer() {
        Thread timer = new Thread(() -> {
            int counter = 1;
            while (compiling) {
                try {
                    Thread.sleep(60000);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                Logger.logInfo("NativeCompile is still running, please hold [" + counter++ + " minute(s)]");
            }
        });
        timer.setDaemon(true);
        timer.start();
    }

    private static void executeLinkStep(SubstrateDispatcher dispatcher) {
        try {
            if (!dispatcher.nativeLink()) {
                Logger.logSevere("Linking failed");
                System.exit(1);
            }
        } catch (Throwable t) {
            Logger.logFatal(t, "Linking failed with an exception.");
        }
    }

    private static void executePackageStep(SubstrateDispatcher dispatcher) {
        try {
            dispatcher.nativePackage();
        } catch (Throwable t) {
            Logger.logFatal(t, "Packaging failed with an exception.");
        }
    }

    private static void executeInstallStep(SubstrateDispatcher dispatcher) {
        try {
            dispatcher.nativeInstall();
        } catch (Throwable t) {
            Logger.logFatal(t, "Installing failed with an exception.");
        }
    }

    private static void executeRunStep(SubstrateDispatcher dispatcher) {
        try {
            String expected = System.getProperty("expected");
            if (expected != null) {
                Logger.logInfo(logTitle("RUN TASK (with expected)"));

                String response = dispatcher.targetConfiguration.run(dispatcher.paths.getAppPath(),
                        dispatcher.config.getAppName());
                if (expected.equals(response)) {
                    Logger.logInfo("Run ended successfully, the output: " + expected + " matched the expected result.");
                } else {
                    Logger.logSevere("Run failed, expected output: " + expected + ", output: " + response);
                    System.exit(1);
                }
            } else {
                dispatcher.nativeRun();
            }
        } catch (Throwable t) {
            Logger.logFatal(t, "Running failed with an exception");
        }
    }

    private static String requireSystemProperty(String argName, String errorMessage ) {
        String arg = System.getProperty(argName);
        if (arg == null || arg.trim().isEmpty()) {
            printUsage();
            throw new IllegalArgumentException( String.format("No '%s' specified. %s", argName, errorMessage));
        }
        return arg;
    }

    private static String logTitle(String text) {
        return "==================== " + (text == null ? "": text) + " ====================";
    }

    private static void printUsage() {
        System.out.println("Usage:\n java -Dimagecp=... -Dgraalvm=... -Dmainclass=... com.gluonhq.substrate.SubstrateDispatcher");
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
        if (this.config.isVerbose()) {
            System.out.println("Configuration: " + this.config);
        }

        Triplet targetTriplet = config.getTargetTriplet();

        this.paths = new ProcessPaths(Objects.requireNonNull(buildRoot), targetTriplet.getArchOs());
        this.targetConfiguration = Objects.requireNonNull(getTargetConfiguration(targetTriplet),
                "Error: Target Configuration was not found for " + targetTriplet);

        Logger.logInit(paths.getLogPath().toString(), this.config.isVerbose());
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
     * This method will start native compilation for the specified configuration.
     * The result of compilation is a at least one native file (2 files in case LLVM backend is used).
     * This method returns <code>true</code> on successful compilation and <code>false</code> when compilations fails
     * @return true if compilation succeeded, false if it fails
     * @throws Exception
     * @throws IllegalArgumentException when the supplied configuration contains illegal combinations
     */
    public boolean nativeCompile() throws Exception {
        Logger.logInfo(logTitle("COMPILE TASK"));

        config.canRunNativeImage();

        Triplet targetTriplet  = config.getTargetTriplet();
        if (!config.getHostTriplet().canCompileTo(targetTriplet)) {
            throw new IllegalArgumentException("We currently can't compile to " + targetTriplet + " when running on " + config.getHostTriplet());
        }

        Logger.logInfo("We will now compile your code for " + targetTriplet + ". This may take some time.");
        boolean compilingSucceeded = targetConfiguration.compile();
        if (!compilingSucceeded) {
            Logger.logSevere("Compiling failed.");
        }
        return compilingSucceeded;
    }

    /**
     * This method will start native linking for the specified configuration, after {@link #nativeCompile()}
     * was called and ended successfully.
     * The result of linking is a at least an native image application file.
     * This method returns <code>true</code> on successful linking and <code>false</code> when linking fails
     * @return true if linking succeeded, false if it fails
     * @throws Exception
     * @throws IllegalArgumentException when the supplied configuration contains illegal combinations
     */
    public boolean nativeLink() throws IOException, InterruptedException {
        Logger.logInfo(logTitle("LINK TASK"));
        boolean linkingSucceeded = targetConfiguration.link();
        if (!linkingSucceeded) {
            Logger.logSevere("Linking failed.");
        }
        return linkingSucceeded;
    }

    /**
     * This method creates a package of the native image application, that was created after {@link #nativeLink()}
     * was called and ended successfully.
     * @throws IOException
     * @throws InterruptedException
     */
    public void nativePackage() throws IOException, InterruptedException {
        Logger.logInfo(logTitle("PACKAGE TASK"));
        targetConfiguration.packageApp();
    }

    /**
     * This method installs the generated package that was created after {@link #nativePackage()}
     * was called and ended successfully.
     * @throws IOException
     * @throws InterruptedException
     */
    public void nativeInstall() throws IOException, InterruptedException {
        Logger.logInfo(logTitle("INSTALL TASK"));
        targetConfiguration.install();
    }

    /**
     * This method runs the native image application, that was created after {@link #nativeLink()}
     * was called and ended successfully.
     * @throws IOException
     * @throws IllegalArgumentException when the supplied configuration contains illegal combinations
     */
    public void nativeRun() throws IOException, InterruptedException {
        Logger.logInfo(logTitle("RUN TASK"));
        targetConfiguration.runUntilEnd();
    }
}
