/*
 * Copyright (c) 2019, 2023, Gluon
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
import com.gluonhq.substrate.target.IosTargetConfiguration;
import com.gluonhq.substrate.target.LinuxTargetConfiguration;
import com.gluonhq.substrate.target.MacOSTargetConfiguration;
import com.gluonhq.substrate.target.TargetConfiguration;
import com.gluonhq.substrate.target.WebTargetConfiguration;
import com.gluonhq.substrate.target.WindowsTargetConfiguration;
import com.gluonhq.substrate.util.Logger;
import com.gluonhq.substrate.util.ProcessRunner;
import com.gluonhq.substrate.util.Strings;
import com.gluonhq.substrate.util.Version;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URL;
import java.net.URLConnection;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.List;
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
    private static volatile boolean messagePrinted = false;

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
        List<String> nativeImageArgs = Arrays.asList(System.getProperty("nativeImageArgs", "").split(","));
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
        if (!nativeImageArgs.isEmpty()) {
            config.setCompilerArgs(nativeImageArgs);
        }
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
                Logger.logSevere("Linking failed.");
                System.exit(1);
            }
        } catch (Throwable t) {
            Logger.logFatal(t, "Linking failed with an exception.");
        }
    }

    private void printMessage(String task) {
       if (messagePrinted) {
          return;
       }    
       try {
            System.out.println(retrieveSubstrateMessage(task));
       }catch(IOException e) {
            System.out.println(" _______  ___      __   __  _______  __    _ ");
            System.out.println("|       ||   |    |  | |  ||       ||  |  | |");
            System.out.println("|    ___||   |    |  | |  ||   _   ||   |_| |");
            System.out.println("|   | __ |   |    |  |_|  ||  | |  ||       |");
            System.out.println("|   ||  ||   |___ |       ||  |_|  ||  _    |");
            System.out.println("|   |_| ||       ||       ||       || | |   |");
            System.out.println("|_______||_______||_______||_______||_|  |__|");
            System.out.println("");
            System.out.println("https://gluonhq.com/activate");
            System.out.println("");
       }
       messagePrinted = true;
    }

    public String retrieveSubstrateMessage(String task) throws IOException {
        URL url = new URL("https://info.gluonhq.com/substrate.txt");
        URLConnection con = url.openConnection();
        con.setConnectTimeout(3000);
        con.setReadTimeout(3000);
        con.setRequestProperty("User-Agent", 
            System.getProperty("os.name") + " - " +
            System.getProperty("os.arch") + " - " +
            System.getProperty("os.version") + " / " +
            System.getProperty("java.version") + " / " +
            config.getTargetTriplet().getOs() + " / " +
            task + " / " +
            getHash(config.getAppId()+config.getMainClassName()));

        StringBuilder text = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(con.getInputStream()))) {
            String l;
            while ((l = reader.readLine()) != null) {
                text.append(l).append("\n");
            }
        }
        return text.toString();
    }

    private String getHash(String input) {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuffer hexString = new StringBuffer();
            for (int i = 0; i < hash.length; i++) {
                String hex = Integer.toHexString(0xff & hash[i]);
                if(hex.length() == 1) hexString.append('0');
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (NoSuchAlgorithmException e) {
            return null;
        }
    }

    private static void executePackageStep(SubstrateDispatcher dispatcher) {
        try {
            if (!dispatcher.nativePackage()) {
                Logger.logSevere("Packaging failed.");
                System.exit(1);
            }
        } catch (Throwable t) {
            Logger.logFatal(t, "Packaging failed with an exception.");
        }
    }

    private static void executeInstallStep(SubstrateDispatcher dispatcher) {
        try {
            if (!dispatcher.nativeInstall()) {
                Logger.logSevere("Installing failed.");
                System.exit(1);
            }
        } catch (Throwable t) {
            Logger.logFatal(t, "Installing failed with an exception.");
        }
    }

    private static void executeRunStep(SubstrateDispatcher dispatcher) {
        try {
            String expected = System.getProperty("expected");
            if (expected != null) {
                Logger.logInfo(logTitle("RUN TASK (with expected)"));

                String response = dispatcher.targetConfiguration.run();
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
        this.paths = new ProcessPaths(Objects.requireNonNull(buildRoot),
                Objects.requireNonNull(config).getTargetTriplet().getArchOs());
        ProcessRunner.setProcessLogPath(paths.getClientPath().resolve(Constants.LOG_PATH));
        ProcessRunner.setConsoleProcessLog(Boolean.getBoolean("consoleProcessLog"));

        this.config = new InternalProjectConfiguration(config);
        if (this.config.isVerbose()) {
            System.out.println("Configuration: " + this.config);
        }

        Version javaVersion = this.config.checkGraalVMJavaVersion();
        this.config.checkGraalVMVersion(javaVersion);
        this.config.checkGraalVMVendor();

        Triplet targetTriplet = config.getTargetTriplet();

        this.targetConfiguration = Objects.requireNonNull(getTargetConfiguration(targetTriplet, javaVersion),
                "Error: Target Configuration was not found for " + targetTriplet);

        Logger.logInit(paths.getLogPath().toString(), this.config.isVerbose());
    }

    private TargetConfiguration getTargetConfiguration(Triplet targetTriplet, Version javaVersion) throws IOException {
        if (!Constants.OS_WEB.equals(targetTriplet.getOs()) && !config.getHostTriplet().canCompileTo(targetTriplet)) {
            throw new IllegalArgumentException("We currently can't compile to " + targetTriplet + " when running on " + config.getHostTriplet());
        }
        switch (targetTriplet.getOs()) {
            case Constants.OS_LINUX  : return new LinuxTargetConfiguration(paths, config, javaVersion);
            case Constants.OS_DARWIN : return new MacOSTargetConfiguration(paths, config, javaVersion);
            case Constants.OS_WINDOWS: return new WindowsTargetConfiguration(paths, config, javaVersion);
            case Constants.OS_IOS    : return new IosTargetConfiguration(paths, config, javaVersion);
            case Constants.OS_ANDROID: return new AndroidTargetConfiguration(paths, config, javaVersion);
            case Constants.OS_WEB    : return new WebTargetConfiguration(paths, config, javaVersion);
            default                  : return null;
        }
    }


    /**
     * This method will start native compilation for the specified configuration.
     * The result of compilation is a at least one native file (2 files in case LLVM backend is used).
     * This method returns <code>true</code> on successful compilation and <code>false</code> when compilations fails.
     * @return true if compilation succeeded, false if it fails
     * @throws Exception
     * @throws IllegalArgumentException when the supplied configuration contains illegal combinations
     */
    public boolean nativeCompile() throws Exception {
        Logger.logInfo(logTitle("COMPILE TASK"));
        printMessage("compile");

        Triplet targetTriplet  = config.getTargetTriplet();
        config.canRunLLVM(targetTriplet);

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
     * This method returns <code>true</code> on successful linking and <code>false</code> when linking fails.
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
        printMessage("link");
        return linkingSucceeded;
    }

    /**
     * This method creates a package of the native image application, that was created after {@link #nativeLink()}
     * was called and ended successfully.
     * This method returns <code>true</code> on successful packaging and <code>false</code> when packaging fails.
     * @return true if packaging succeeded, false if it fails
     * @throws IOException
     * @throws InterruptedException
     */
    public boolean nativePackage() throws IOException, InterruptedException {
        Logger.logInfo(logTitle("PACKAGE TASK"));
        boolean packagingSucceeded = targetConfiguration.packageApp();
        if (!packagingSucceeded) {
            Logger.logSevere("Packaging failed.");
        }
        printMessage("package");
        return packagingSucceeded;
    }

    /**
     * This method installs the generated package that was created after {@link #nativePackage()}
     * was called and ended successfully.
     * This method returns <code>true</code> on successful installation and <code>false</code> when installation fails.
     * @return true if installing succeeded, false if it fails
     * @throws IOException
     * @throws InterruptedException
     */
    public boolean nativeInstall() throws IOException, InterruptedException {
        Logger.logInfo(logTitle("INSTALL TASK"));
        boolean installingSucceeded = targetConfiguration.install();
        if (!installingSucceeded) {
            Logger.logSevere("Installing failed.");
        }
        printMessage("install");
        return installingSucceeded;
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
        printMessage("run");
    }

    /**
     * This method builds a native image that can be used as shared library by third
     * party projects, considering it contains one or more entry points.
     *
     * Static entry points, callable from C, can be created with the {@code @CEntryPoint}
     * annotation.
     *
     * @throws Exception
     */
    public boolean nativeSharedLibrary() throws Exception {
        Logger.logInfo(logTitle("SHARED LIBRARY TASK"));
        config.setSharedLibrary(true);
        return targetConfiguration.createSharedLib();
    }

    /**
     * This method builds a static library that can be used by third
     * party projects, considering it contains one or more entry points.
     *
     * Static entry points, callable from C, can be created with the {@code @CEntryPoint}
     * annotation.
     *
     * @throws Exception
     */
    public boolean nativeStaticLibrary() throws Exception {
        Logger.logInfo(logTitle("STATIC LIBRARY TASK"));
        config.setStaticLibrary(true);
        return targetConfiguration.createStaticLib();
    }

}
