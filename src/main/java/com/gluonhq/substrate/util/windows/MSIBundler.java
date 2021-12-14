package com.gluonhq.substrate.util.windows;

import com.gluonhq.substrate.model.InternalProjectConfiguration;
import com.gluonhq.substrate.model.ProcessPaths;
import com.gluonhq.substrate.util.FileOps;
import com.gluonhq.substrate.util.Logger;
import com.gluonhq.substrate.util.ProcessRunner;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.text.MessageFormat;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class MSIBundler {

    private final ProcessPaths paths;
    private final InternalProjectConfiguration projectConfiguration;
    private final String sourceOS;
    private final Path rootPath;

    public MSIBundler(ProcessPaths paths, InternalProjectConfiguration projectConfiguration) {
        this.paths = paths;
        this.projectConfiguration = projectConfiguration;
        this.sourceOS = projectConfiguration.getTargetTriplet().getOs();
        this.rootPath = paths.getSourcePath().resolve(sourceOS);
    }

    public boolean createPackage(boolean sign) throws IOException, InterruptedException {
        final String appName = projectConfiguration.getAppName();
        Path localAppPath = paths.getAppPath().resolve(appName + ".exe");
        if (!Files.exists(localAppPath)) {
            throw new IOException("Error: " + appName + ".exe not found");
        }
        Logger.logInfo("Building exe for " + localAppPath);

        /**
         * Wix Compile
         */
        Path tmpMSI = paths.getTmpPath().resolve("tmpMSI");
        if (Files.exists(tmpMSI)) {
            FileOps.deleteDirectory(tmpMSI);
        }
        Files.createDirectories(tmpMSI);

        Path config = tmpMSI.resolve("config");
        Files.createDirectories(config);
        Path wixPath = config.resolve("main.wxs");
        Path wixObjPath = wixPath.getParent().resolve(wixPath.getFileName() + ".wixobj");
        Path msiPath = wixPath.getParent().resolve(appName + "-" +  projectConfiguration.getReleaseConfiguration().getVersionName()  + ".msi");
        FileOps.copyResource("/native/windows/wix/main.wxs", wixPath);
        FileOps.copyResource("/native/windows/assets/icon_32.ico", config.resolve("icon.ico"));
        
        Map<String, String> userInput = createAppDetailMap();
        List<String> processArgs = new ArrayList<>(List.of(
                WixTool.CANDLE.getPath(),
                "-nologo",
                wixPath.toString(),
                "-ext", "WixUtilExtension",
                "-arch", "x64",
                "-out", wixObjPath.toString()
        ));

        userInput.entrySet().stream()
                .map(wixVar -> String.format("-d%s=%s", wixVar.getKey(), wixVar.getValue()))
                .forEachOrdered(processArgs::add);
        Logger.logInfo(String.join(" ", processArgs));
        ProcessRunner candle = new ProcessRunner(processArgs.toArray(new String[0]));
        if (candle.runProcess("Wix Compiler") != 0) {
            throw new IOException("Error running candle to generate wixobj");
        }

        /**
         * Wix Link
         */
        processArgs.clear();
        processArgs.addAll(List.of(
                WixTool.LIGHT.getPath(),
                "-nologo",
                "-spdb",
                "-ext", "WixUtilExtension",
                "-ext", "WixUIExtension",
                "-out", msiPath.toString(),
                wixObjPath.toString()
        ));

        ProcessRunner light = new ProcessRunner(processArgs.toArray(new String[0]));
        if (light.runProcess("Wix Linker") != 0) {
            throw new IOException("Error running light to generate msi");
        }

        return true;
    }

    private Map<String, String> createAppDetailMap() {
        Map<String, String> userInput = new HashMap<>();
        String appName = projectConfiguration.getReleaseConfiguration().getAppLabel();
        String executableName = appName + ".exe";
        String vendor = projectConfiguration.getReleaseConfiguration().getVendor();
        String version = projectConfiguration.getReleaseConfiguration().getVersionName();
        userInput.put("GSProductCode", createUUID("ProductCode", appName, vendor, version).toString());
        userInput.put("GSAppName", appName);
        userInput.put("GSAppExecutable", executableName);
        userInput.put("GSAppVersion", version);
        userInput.put("GSAppVendor", vendor);
        userInput.put("GSAppIconName", appName + "Icon.exe");
        userInput.put("GSAppIcon", paths.getTmpPath().resolve("tmpMSI").resolve("config").resolve("icon.ico").toString());
        userInput.put("GSLicenseRtf", paths.getTmpPath().resolve("tmpMSI").resolve("config").resolve("icon.ico").toString());
        userInput.put("GSApplicationPath", paths.getClientPath().resolve("x86_64-windows").resolve(executableName).toString());
        userInput.put("GSProductUpgradeCode", createUUID("UpgradeCode", appName, vendor, version).toString());
        userInput.put("GSAppDescription", "some-app-description");
        return userInput;
    }

    private UUID createUUID(String prefix,String appName, String vendor, String version) {
        String key = String.join(",", prefix, appName, vendor, version);
        return UUID.nameUUIDFromBytes(key.getBytes(StandardCharsets.UTF_8));
    }

    enum WixTool {
        CANDLE,
        LIGHT;

        String getPath() {
            for (var dir : findWixInstallDirs()) {
                Path path = dir.resolve(name().toLowerCase() + ".exe");
                if (Files.exists(path)) {
                    return path.toString();
                }
            }
            throw new RuntimeException("Path not found");
        }

        private List<Path> findWixInstallDirs() {
            PathMatcher wixInstallDirMatcher = FileSystems.getDefault().getPathMatcher(
                    "glob:WiX Toolset v*");

            Path programFiles = getSystemDir("ProgramFiles", "\\Program Files");
            Path programFilesX86 = getSystemDir("ProgramFiles(x86)",
                    "\\Program Files (x86)");

            // Returns list of WiX install directories ordered by WiX version number.
            // Newer versions go first.
            return Stream.of(programFiles, programFilesX86).map(path -> {
                        List<Path> result;
                        try (var paths = Files.walk(path, 1)) {
                            result = paths.collect(Collectors.toList());
                        } catch (IOException ex) {
                            Logger.logDebug(ex.getMessage());
                            result = Collections.emptyList();
                        }
                        return result;
                    }).flatMap(List::stream)
                    .filter(path -> wixInstallDirMatcher.matches(path.getFileName()))
                    .sorted(Comparator.comparing(Path::getFileName).reversed())
                    .map(path -> path.resolve("bin"))
                    .collect(Collectors.toList());
        }

        private Path getSystemDir(String envVar, String knownDir) {
            return Optional
                    .ofNullable(getEnvVariableAsPath(envVar))
                    .orElseGet(() -> Optional
                            .ofNullable(getEnvVariableAsPath("SystemDrive"))
                            .orElseGet(() -> Path.of("C:")).resolve(knownDir));
        }

        private Path getEnvVariableAsPath(String envVar) {
            String path = System.getenv(envVar);
            if (path != null) {
                try {
                    return Path.of(path);
                } catch (InvalidPathException ex) {
                    Logger.logDebug(MessageFormat.format("Invalid value of {0} environment variable", envVar));
                }
            }
            return null;
        }
    }
}
