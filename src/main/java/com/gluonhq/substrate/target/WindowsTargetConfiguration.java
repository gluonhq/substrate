/*
 * Copyright (c) 2019, 2022, Gluon
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
import com.gluonhq.substrate.util.windows.MSIBundler;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class WindowsTargetConfiguration extends AbstractTargetConfiguration {

    private static final List<String> javaWindowsLibs = Arrays.asList(
            "advapi32", "iphlpapi", "secur32", "userenv", "version", "ws2_32", "winhttp", "ncrypt", "crypt32");
    private static final List<String> staticJavaLibs = Arrays.asList(
            "j2pkcs11", "java", "net", "nio", "prefs", "fdlibm", "sunec", "zip", "sunmscapi");
    private static final List<String> staticJvmLibs = Arrays.asList(
            "jvm", "libchelper");

    private static final List<String> javaFxWindowsLibs = List.of(
            "comdlg32", "dwmapi", "gdi32", "imm32", "shell32",
            "uiautomationcore", "urlmon", "winmm");
    private static final List<String> staticJavaFxLibs = List.of(
            "glass", "javafx_font", "javafx_iio",
            "prism_common", "prism_d3d");
    private static final List<String> staticJavaFxSwLibs = List.of(
            "prism_sw");

    public WindowsTargetConfiguration(ProcessPaths paths, InternalProjectConfiguration configuration ) {
        super(paths, configuration);
    }

    @Override
    String getAdditionalSourceFileLocation() {
        return "/native/windows/";
    }

    @Override
    List<String> getTargetSpecificCCompileFlags() {
        return Arrays.asList("/MD", "/D_UNICODE", "/DUNICODE", "/DWIN32", "/D_WINDOWS");
    }

    /**
     * The arguments to native-image.cmd must be wrapped in double quotes for the
     * value of the classpath argument (-cp ...) or when the value of the argument is
     * assigned by using the '=' character (i.e. -H:IncludeResourceBundles=abc).
     */
    @Override
    void postProcessCompilerArguments(List<String> arguments) {
        int classPathArgumentIdx = Integer.MIN_VALUE;
        for (int i = 0; i < arguments.size(); i++) {
            String argument = arguments.get(i);
            if (argument.contains("=") || (i == classPathArgumentIdx + 1)) {
                arguments.set(i, "\"" + argument + "\"");
            } else if (Constants.NATIVE_IMAGE_ARG_CLASSPATH.equals(argument)) {
                classPathArgumentIdx = i;
            }
        }
    }

    @Override
    String getCompiler() {
        return "cl";
    }

    @Override
    String getLinker() {
        return "link";
    }

    @Override
    String getNativeImageCommand() {
        return "native-image.cmd";
    }

    @Override
    String getObjectFileExtension() {
        return "obj";
    }

    @Override
    String getStaticLibraryFileExtension() {
        return "lib";
    }

    @Override
    boolean matchesStaticLibraryName(String fileName) {
        return fileName.endsWith("." + getStaticLibraryFileExtension());
    }

    @Override
    List<String> getTargetSpecificLinkOutputFlags() {
        return Collections.singletonList("/OUT:" + getAppPath(getLinkOutputName()));
    }

    @Override
    String getLinkOutputName() {
        String appName = projectConfiguration.getAppName();
        return appName + ".exe";
    }

    @Override
    List<String> getStaticJavaLibs() {
        return staticJavaLibs;
    }

    @Override
    List<String> getOtherStaticLibs() {
        return Stream.concat(staticJvmLibs.stream(), javaWindowsLibs.stream())
                .collect(Collectors.toList());
    }

    @Override
    List<String> getTargetSpecificNativeLibsFlags(Path libPath, List<String> libs) {
        List<String> linkFlags = new ArrayList<>();
        linkFlags.addAll(libs);
        linkFlags.addAll(asListOfWholeArchiveLinkFlags(libs));
        return linkFlags;
    }

    @Override
    List<String> getTargetSpecificLinkFlags(boolean useJavaFX, boolean usePrismSW) {
        List<String> flags = new ArrayList<>();
        flags.add("/NODEFAULTLIB:libcmt.lib");

        if (useJavaFX) {
            flags.add("/SUBSYSTEM:WINDOWS");
            flags.add("/ENTRY:mainCRTStartup");

            flags.addAll(asListOfLibraryLinkFlags(javaFxWindowsLibs));
            flags.addAll(asListOfLibraryLinkFlags(staticJavaFxLibs));
            flags.addAll(asListOfWholeArchiveLinkFlags(staticJavaFxLibs));

            if (usePrismSW) {
                flags.addAll(asListOfLibraryLinkFlags(staticJavaFxSwLibs));
                flags.addAll(asListOfWholeArchiveLinkFlags(staticJavaFxSwLibs));
            }
        }

        return flags;
    }

    @Override
    String getLinkLibraryPathOption() {
        return "/LIBPATH:";
    }

    @Override
    String getLinkLibraryOption(String libname) {
        return libname + "." + getStaticLibraryFileExtension();
    }

    @Override
    public boolean packageApp() throws IOException, InterruptedException {
        MSIBundler msiBundler = new MSIBundler(paths, projectConfiguration);
        return msiBundler.createPackage(false);
    }

    private List<String> asListOfLibraryLinkFlags(List<String> libraries) {
        return libraries.stream()
                .map(this::getLinkLibraryOption)
                .collect(Collectors.toList());
    }

    private List<String> asListOfWholeArchiveLinkFlags(List<String> libraries) {
        List<String> linkFlags = new ArrayList<>();

        // add libraries whose name end with .lib unmodified
        linkFlags.addAll(libraries.stream()
                .filter(library -> library.endsWith("." + getStaticLibraryFileExtension()))
                .map(library -> "/WHOLEARCHIVE:" + library)
                .collect(Collectors.toList()));

        // add libraries whose name don't end with .lib by appending .lib first
        linkFlags.addAll(libraries.stream()
                .filter(library -> !library.endsWith("." + getStaticLibraryFileExtension()))
                .map(library -> "/WHOLEARCHIVE:" + library + "." + getStaticLibraryFileExtension())
                .collect(Collectors.toList()));

        return linkFlags;
    }

    @Override
    public boolean link() throws IOException, InterruptedException {
        try {
            createIconResource();
            super.link();
            clearExplorerCache();
        } catch (IOException ioe) {
            ioe.printStackTrace();
            return false;
        }
        return true;
    }

    // During development if user changes the application icon, the same is not reflected immediately in Explorer.
    // To fix this, a cache clearance of the Windows explorer is required.
    private void clearExplorerCache() throws IOException, InterruptedException {
        ProcessRunner clearCache;
        String osName = System.getProperty("os.name").toLowerCase(Locale.ROOT);
        // For Windows version < 10, use `ie4uinit.exe -ClearIconCache`
        if (osName.equals("windows 10") || osName.equals("windows 11")) {
            clearCache = new ProcessRunner("ie4uinit", "-show");
        } else {
            clearCache = new ProcessRunner("ie4uinit", "-ClearIconCache");
        }
        clearCache.runProcess("Clear Explorer Cache");
    }

    List<String> getTargetSpecificObjectFiles() {
        Path gvmAppPath = paths.getGvmPath().resolve(projectConfiguration.getAppName());
        return Collections.singletonList(gvmAppPath.resolve("IconGroup.obj").toString());
    }

    private void createIconResource() throws InterruptedException, IOException {
        Logger.logDebug("Creating icon resource");
        String sourceOS = projectConfiguration.getTargetTriplet().getOs();
        Path rootPath = paths.getSourcePath().resolve(sourceOS);
        Path userAssets = rootPath.resolve(Constants.WIN_ASSETS_FOLDER);
        Path iconDir = paths.getTmpPath().resolve("icon");
        Path gvmAppPath = paths.getGvmPath().resolve(projectConfiguration.getAppName());

        // Copy icon.ico to gvm/tmp/icon
        if (Files.exists(userAssets) && Files.isDirectory(userAssets) && Files.exists(userAssets.resolve("icon.ico"))) {
            FileOps.copyFile(userAssets.resolve("icon.ico"), iconDir.resolve("icon.ico")) ;
            Logger.logInfo("User provided icon.ico image used as application icon.");
        } else {
            FileOps.copyResource("/native/windows/assets/icon.ico", iconDir.resolve("icon.ico"));
            Logger.logInfo("Default icon.ico image used. " +
                    "Consider adding a custom icon to '/native/windows/assets'.");
        }

        // Create resource from icon
        FileOps.copyResource("/native/windows/assets/IconGroup.rc", iconDir.resolve("IconGroup.rc"));
        Path resPath = iconDir.resolve("IconGroup.res");
        ProcessRunner rc = new ProcessRunner("rc", "-fo", resPath.toString(), iconDir.resolve("IconGroup.rc").toString());
        if (rc.runProcess("rc compile") == 0) {
            Path objPath = resPath.getParent().resolve("IconGroup.obj");
            ProcessRunner cvtres = new ProcessRunner("cvtres ", "/machine:x64", "-out:" + objPath, resPath.toString());
            if (cvtres.runProcess("cvtres") == 0 ) {
                Logger.logDebug("IconGroup.obj created successfully");
                FileOps.copyFile(objPath, gvmAppPath.resolve("IconGroup.obj"));
            }
        }
    }
}
