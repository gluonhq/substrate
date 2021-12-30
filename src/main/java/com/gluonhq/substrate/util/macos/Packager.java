/*
 * Copyright (c) 2021, Gluon
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
package com.gluonhq.substrate.util.macos;

import com.dd.plist.NSData;
import com.dd.plist.NSDictionary;
import com.gluonhq.substrate.Constants;
import com.gluonhq.substrate.model.InternalProjectConfiguration;
import com.gluonhq.substrate.model.ProcessPaths;
import com.gluonhq.substrate.util.FileOps;
import com.gluonhq.substrate.util.Logger;
import com.gluonhq.substrate.util.ProcessRunner;
import com.gluonhq.substrate.util.plist.NSDictionaryEx;
import com.gluonhq.substrate.util.plist.NSObjectEx;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.xml.sax.SAXException;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class Packager {

    private final ProcessPaths paths;
    private final InternalProjectConfiguration projectConfiguration;
    private final String sourceOS;
    private final Path rootPath;
    private final boolean appStore;

    public Packager(ProcessPaths paths, InternalProjectConfiguration projectConfiguration) {
        this.paths = paths;
        this.projectConfiguration = projectConfiguration;
        this.sourceOS = projectConfiguration.getTargetTriplet().getOs();
        this.appStore = projectConfiguration.getReleaseConfiguration().isMacAppStore();
        rootPath = paths.getSourcePath().resolve(sourceOS);
    }

    public boolean createPackage(boolean sign) throws IOException, InterruptedException {
        final String appName = projectConfiguration.getAppName();
        Path localAppPath = paths.getAppPath().resolve(appName + ".app");
        if (!Files.exists(localAppPath)) {
            throw new IOException("Error: " + appName + ".app not found");
        }
        Logger.logInfo("Building pkg for " + localAppPath);

        Path tmpPkg = paths.getTmpPath().resolve("tmpPkg");
        if (Files.exists(tmpPkg)) {
            FileOps.deleteDirectory(tmpPkg);
        }
        Files.createDirectories(tmpPkg);

        Path appPkg = tmpPkg.resolve("packages").resolve(appName + "-app.pkg");
        Files.createDirectories(appPkg.getParent());
        Path root = tmpPkg.resolve("root");
        Path rootApp = root.resolve(appName + ".app");
        Files.createDirectories(rootApp);
        FileOps.copyDirectory(localAppPath, rootApp);
        Path config = tmpPkg.resolve("config");
        Files.createDirectories(config);
        Path componentPlist = config.resolve("component.plist");

        Path infoPlistPath = rootApp.resolve("Contents").resolve(Constants.MACOS_PLIST_FILE);
        NSObjectEx info;
        try {
            info = new NSObjectEx(infoPlistPath);
        } catch (Exception ex) {
            Logger.logFatal(ex,"Error reading plist " + infoPlistPath);
            return false;
        }

        Logger.logDebug("Creating component.plist");
        String bundleId = info.getValueFromDictionary("CFBundleIdentifier");
        String appVersion = info.getValueFromDictionary("CFBundleShortVersionString");
        ProcessRunner runner1 = new ProcessRunner("pkgbuild", "--analyze",
                "--root", root.toString(),
                "--identifier", bundleId,
                "--version", appVersion,
                "--install-location", "/Applications",
                componentPlist.toString());
        if (runner1.runProcess("pkg component") != 0) {
            throw new IOException("Error running pkgbuild to generate component.plist");
        }

        Logger.logDebug("Updating component.plist");
        try {
            NSObjectEx component = new NSObjectEx(componentPlist);
            if (component.setValueToDictionary("BundleIsRelocatable", "false")) {
                component.saveAsXML(componentPlist);
            }
        } catch (Exception ex) {
            Logger.logFatal(ex,"Error reading plist " + componentPlist);
            return false;
        }

        Path scripts = config.resolve("scripts");
        if (!appStore) {
            Files.createDirectories(scripts);

            Path resource = FileOps.copyResource("/native/macosx/assets/preinstall.sh", scripts.resolve("preinstall.sh"));
            FileOps.replaceInFile(resource, "InstallLocation", "/Application");
            resource = FileOps.copyResource("/native/macosx/assets/postinstall.sh", scripts.resolve("postinstall.sh"));
            FileOps.replaceInFile(resource, "InstallLocation", "/Applications");
        }

        Logger.logDebug("Building application pkg");
        ProcessRunner runner2 = new ProcessRunner("pkgbuild",
                "--root", root.toString(),
                "--component-plist", componentPlist.toString(),
                "--identifier", bundleId,
                "--version", appVersion,
                "--install-location", "/Applications");
        if (!appStore) {
            runner2.addArgs("--scripts", scripts.toString());
        }
        runner2.addArg(appPkg.toString());
        if (runner2.runProcess("pkg build") != 0) {
            throw new IOException("Error running pkgbuild to build application pkg");
        }

        Path userAssets = rootPath.resolve(Constants.MACOS_ASSETS_FOLDER);
        Path backgroundPath = null, backgroundAquaPath = null, licensePath = null;
        if (Files.exists(userAssets) && Files.isDirectory(userAssets)) {
            backgroundPath = Files.list(userAssets)
                    .filter(p -> p.toString().endsWith("-background.png"))
                    .findFirst()
                    .orElse(null);
            backgroundAquaPath = Files.list(userAssets)
                    .filter(p -> p.toString().endsWith("-background-darkAqua.png"))
                    .findFirst()
                    .orElse(null);
            licensePath = Files.list(userAssets)
                    .filter(p -> p.toString().endsWith("license.html"))
                    .findFirst()
                    .orElse(null);
        }
        // copy assets to gensrc/macos
        Path macosPath = paths.getGenPath().resolve(sourceOS);
        Path macosAssets = macosPath.resolve(Constants.MACOS_ASSETS_FOLDER);
        if (backgroundPath == null || !Files.exists(backgroundPath)) {
            backgroundPath = FileOps.copyResource("/native/macosx/assets/background.png", macosAssets.resolve(appName + "-background.png"));
            Logger.logInfo("Default background image generated in " + macosAssets.toString() + ".\n" +
                    "Consider copying it to " + rootPath.toString() + " before performing any modification");
        }
        if (backgroundAquaPath == null || !Files.exists(backgroundAquaPath)) {
            backgroundAquaPath = FileOps.copyResource("/native/macosx/assets/background.png", macosAssets.resolve(appName + "-background-darkAqua.png"));
            Logger.logInfo("Default background-darkAqua image generated in " + macosAssets.toString() + ".\n" +
                    "Consider copying it to " + rootPath.toString() + " before performing any modification");
        }

        // copy to config
        backgroundPath = FileOps.copyFile(backgroundPath, config.resolve(backgroundPath.getFileName()));
        backgroundAquaPath = FileOps.copyFile(backgroundAquaPath, config.resolve(backgroundAquaPath.getFileName()));
        if (licensePath != null) {
            licensePath = FileOps.copyFile(licensePath, config.resolve(licensePath.getFileName()));
        }

        Path configResource;
        if (appStore) {
            Logger.logDebug("Copying productInfo.plist");
            configResource = FileOps.copyResource("/native/macosx/assets/productInfo.plist", config.resolve("productInfo.plist"));
        } else {
            Logger.logDebug("Creating distribution.xml");
            Path distributionXML = config.resolve("distribution.xml");
            ProcessRunner runner3 = new ProcessRunner("productbuild", "--synthesize",
                    "--package", appPkg.toString(),
                    distributionXML.toString());
            if (runner3.runProcess("productbuild distribution") != 0) {
                throw new IOException("Error running productbuild to create distribution file");
            }

            configResource = modifyXML(distributionXML.toString(), appName,
                    backgroundPath.getFileName().toString(), backgroundAquaPath.getFileName().toString(),
                    licensePath == null ? null : licensePath.getFileName().toString());
        }

        Logger.logDebug("Building final pkg");
        Path finalPkg = paths.getAppPath().resolve(appName + "-1.0.0.pkg");
        ProcessRunner runner4 = new ProcessRunner("productbuild",
                "--resources", config.toString());
        if (appStore) {
            runner4.addArgs("--product", configResource.toString(),
                "--component", root.resolve(appName + ".app").toString(), "/Applications");
        } else {
            runner4.addArgs("--distribution", configResource.toString(),
                "--package-path", appPkg.getParent().toString());
        }
        if (sign) {
            String certificate = retrieveAllValidCertificates(appStore ? "3rd Party Mac Developer Installer" : "Developer ID Installer").stream()
                    .findFirst()
                    .orElseThrow(() -> new IOException("No valid certificate found"));
            runner4.addArgs("--sign", certificate);
        }
        runner4.addArg(finalPkg.toString());
        if (runner4.runProcess("productbuild package") != 0) {
            throw new IOException("Error running pkgbuild to build final pkg");
        }
        Logger.logInfo("Pkg built successfully at " + finalPkg);
        return true;
    }

    public boolean createDmg(boolean sign) throws IOException, InterruptedException {
        final String appName = projectConfiguration.getAppName();
        Path localAppPath = paths.getAppPath().resolve(appName + ".app");
        if (!Files.exists(localAppPath)) {
            throw new IOException("Error: " + appName + ".app not found");
        }
        Logger.logInfo("Building dmg for " + localAppPath);

        Path tmpDmg = paths.getTmpPath().resolve("tmpDmg");
        if (Files.exists(tmpDmg)) {
            FileOps.deleteDirectory(tmpDmg);
        }
        Files.createDirectories(tmpDmg);

        Path rootApp = tmpDmg.resolve(appName + ".app");
        FileOps.copyDirectory(localAppPath, rootApp);
        Path config = tmpDmg.resolve("config");
        Files.createDirectories(config);
        Path images = tmpDmg.resolve("images");
        Files.createDirectories(images);

        Path userAssets = rootPath.resolve(Constants.MACOS_ASSETS_FOLDER);
        Path backgroundPath = null, licenseContentPath = null;
        if (Files.exists(userAssets) && Files.isDirectory(userAssets)) {
            backgroundPath = Files.list(userAssets)
                    .filter(p -> p.toString().endsWith("-background.tiff"))
                    .findFirst()
                    .orElse(null);
            licenseContentPath = Files.list(userAssets)
                    .filter(p -> p.toString().endsWith("license.txt"))
                    .findFirst()
                    .orElse(null);
        }

        // copy assets to gensrc/macos
        Path macosPath = paths.getGenPath().resolve(sourceOS);
        Path macosAssets = macosPath.resolve(Constants.MACOS_ASSETS_FOLDER);
        if (backgroundPath == null || !Files.exists(backgroundPath)) {
            backgroundPath = FileOps.copyResource("/native/macosx/assets/background.tiff", macosAssets.resolve(appName + "-background.tiff"));
            Logger.logInfo("Default background image generated in " + macosAssets.toString() + ".\n" +
                    "Consider copying it to " + rootPath.toString() + " before performing any modification");
        }

        // copy to config
        backgroundPath = FileOps.copyFile(backgroundPath, config.resolve(backgroundPath.getFileName()));
        Path volumeIconPath = FileOps.copyFile(rootApp.resolve("Contents").resolve("Resources").resolve("AppIcon.icns"), config.resolve(appName + "-volume.icns"));

        // setup script
        Logger.logDebug("Setting up applescript");
        Path volumePath = images.resolve(appName);
        String volumeUrl = volumePath.toUri() + File.separator;
        Path backgroundFile = Path.of(images.toString(), appName, ".background", backgroundPath.getFileName().toString());

        Path scriptPath = FileOps.copyResource("/native/macosx/assets/setup.scpt", config.resolve(appName + "-setup.scpt"));
        FileOps.replaceInFile(scriptPath, "DeployVolumePath", volumePath.toString());
        FileOps.replaceInFile(scriptPath, "DeployVolumeURL", volumeUrl);
        FileOps.replaceInFile(scriptPath, "DeployBackgroundFile", backgroundFile.toString());
        FileOps.replaceInFile(scriptPath, "DeployInstallLocation", "/Applications");
        FileOps.replaceInFile(scriptPath, "DeployTarget", rootApp.getFileName().toString());

        // build
        Path genDMG = images.resolve(appName +"-tmp.dmg");
        Files.deleteIfExists(genDMG);
        Path finalDmg = paths.getAppPath().resolve(appName + "-1.0.0.dmg");
        Files.deleteIfExists(finalDmg);

        Logger.logDebug("Creating tmp image");
        String hdiutil = "/usr/bin/hdiutil";
        String verbose = projectConfiguration.isVerbose() ? "-verbose" : "-quiet";
        ProcessRunner runner1 = new ProcessRunner(hdiutil,
                "create",
                "-srcfolder", tmpDmg.toString(),
                "-volname", appName,
                "-ov", genDMG.toString(),
                "-fs", "HFS+",
                "-format", "UDRW",
                verbose);
        if (runner1.runProcess("dmg create tmp image") != 0) {
            throw new IOException("Error running hdiutil to create dmg tmp image");
        }

        Logger.logDebug("Mounting tmp image");
        ProcessRunner runner2 = new ProcessRunner(
                hdiutil,
                "attach",
                genDMG.toString(),
                "-mountroot", images.toString(),
                verbose);
        if (runner2.runProcess("dmg mount tmp image") != 0) {
            throw new IOException("Error running hdiutil to mount dmg tmp image");
        }

        Path backgroundDir = volumePath.resolve(".background");
        Files.createDirectories(backgroundDir);
        FileOps.copyFile(backgroundPath, backgroundDir.resolve(backgroundPath.getFileName()));

        Logger.logDebug("Running apple script");
        ProcessRunner runner3 = new ProcessRunner("/usr/bin/osascript", scriptPath.toString());
        if (!runner3.runTimedProcess("dmg run script", 180)) {
            throw new IOException("Error running osascript to run applescript");
        }

        Logger.logDebug("Add volume icon");
        Path volumeIcon = images.resolve(appName).resolve(".VolumeIcon.icns");
        FileOps.copyFile(volumeIconPath, volumeIcon);

        String setFile = "/usr/bin/SetFile";
        if (!Files.exists(Path.of(setFile))) {
            setFile = ProcessRunner.runProcessForSingleOutput("find SetFile", "/usr/bin/xcrun", "-find", "SetFile");
            if (setFile == null || setFile.isEmpty()) {
                throw new IOException("Error finding SetFile");
            }
        }

        if (!volumeIcon.toFile().setWritable(true)) {
            throw new IOException("Error: " + volumeIcon + " can be set writable");
        }
        ProcessRunner runner4 = new ProcessRunner(
                setFile,
                "-c", "icnC",
                volumeIcon.toString());
        if (runner4.runProcess("dmg setfile icon") != 0) {
            throw new IOException("Error running setFile to create icon attributes");
        }
        if (!volumeIcon.toFile().setReadOnly()) {
            throw new IOException("Error: " + volumeIcon + " can be set read only");
        }

        ProcessRunner runner5 = new ProcessRunner(
                setFile,
                "-a", "C",
                volumePath.toString());
        if (runner5.runProcess("dmg setfile app") != 0) {
            throw new IOException("Error running setFile to create app attributes");
        }

        // cleanup
        FileOps.deleteDirectory(volumePath.resolve(".fseventsd"));
        FileOps.deleteDirectory(volumePath.resolve("images"));
        FileOps.deleteDirectory(volumePath.resolve("config"));

        Logger.logDebug("Detach tmp image");
        ProcessRunner runner6 = new ProcessRunner(
                hdiutil,
                "detach",
                volumePath.toString(),
                verbose);
        if (runner6.runProcess("dmg detach tmp image") != 0) {
            throw new IOException("Error running hdiutil to detach dmg tmp image");
        }

        Logger.logDebug("Build final image");
        ProcessRunner runner7 = new ProcessRunner(
                hdiutil,
                "convert",
                genDMG.toString(),
                "-format", "UDZO",
                "-o", finalDmg.toString(),
                verbose);
        if (runner7.runProcess("dmg convert tmp image") != 0) {
            throw new IOException("Error running hdiutil to convert dmg tmp image");
        }

        if (licenseContentPath != null) {
            byte[] licenseContent = Files.readAllBytes(licenseContentPath);
            String licenseContent64 = Base64.getEncoder().encodeToString(licenseContent);
            Path licPlist = FileOps.copyResource("/native/macosx/assets/license.plist", config.resolve(appName + "-license.plist"));
            try {
                NSDictionaryEx dict = new NSDictionaryEx(licPlist);
                NSDictionary textDict = (NSDictionary) dict.getArray("TEXT")[0];
                textDict.put("Data", new NSData(licenseContent64));
                dict.saveAsXML(licPlist);
            } catch (Exception e) {
                throw new IOException(("Error writing data to license: " + e.getMessage()));
            }

            Logger.logDebug("Add license to final image");
            ProcessRunner runner8 = new ProcessRunner(
                    hdiutil,
                    "udifrez",
                    finalDmg.toString(),
                    "-xml",
                    licPlist.toString()
                );
            if (runner8.runProcess("dmg add license") != 0) {
                throw new IOException("Error running hdiutil to add license to dmg");
            }
        }

        Files.deleteIfExists(genDMG);

        if (sign) {
            Logger.logDebug("Sign final image");
            CodeSigning codeSigning = new CodeSigning(paths, projectConfiguration);
            if (!codeSigning.signDmg(finalDmg)) {
                throw new RuntimeException("Error signing the dmg bundle");
            }
        }

        Logger.logInfo("Dmg built successfully at " + finalDmg);
        return true;
    }

    private static Path modifyXML(String fileName, String title, String backgroundFile, String backgroundAquaFile, String licenseFile) throws IOException {
        try {
            File xmlFile = new File(Objects.requireNonNull(fileName));
            if (!xmlFile.exists() || !fileName.endsWith(".xml")) {
                throw new IOException("Not a valid file: " + fileName);
            }

            DocumentBuilder builder =  DocumentBuilderFactory.newInstance().newDocumentBuilder();
            Document document = builder.parse(xmlFile);

            Node root = document.getFirstChild();
            Element element = document.createElement("title");
            element.appendChild(document.createTextNode(title));
            root.appendChild(element);

            if (backgroundFile != null && !backgroundFile.isEmpty()) {
                element = document.createElement("background");
                element.setAttribute("file", backgroundFile);
                element.setAttribute("mime-type", "image/png");
                element.setAttribute("alignment", "bottomleft");
                element.setAttribute("scaling", "none");
                root.appendChild(element);
            }

            if (backgroundAquaFile != null && !backgroundAquaFile.isEmpty()) {
                element = document.createElement("background-darkAqua");
                element.setAttribute("file", backgroundAquaFile);
                element.setAttribute("mime-type", "image/png");
                element.setAttribute("alignment", "bottomleft");
                element.setAttribute("scaling", "none");
                root.appendChild(element);
            }

            if (licenseFile != null && !licenseFile.isEmpty()) {
                element = document.createElement("license");
                element.setAttribute("file", licenseFile);
                element.setAttribute("mime-type", "text/rtf");
                root.appendChild(element);
            }

            Transformer transformer = TransformerFactory.newInstance().newTransformer();
            transformer.setOutputProperty(OutputKeys.INDENT, "yes");
            transformer.transform(new DOMSource(document), new StreamResult(fileName));
        } catch (SAXException | ParserConfigurationException | TransformerException ex) {
            Logger.logSevere("Error parsing xml file: " + ex.getMessage());
        }
        return Path.of(fileName);
    }

    private static List<String> retrieveAllValidCertificates(String type) {
        final Pattern pattern = Pattern.compile("\"alis\"<blob>=\"([^\"]+)\"");

        ProcessRunner runner = new ProcessRunner("security", "find-certificate", "-a", "-c", type);
        try {
            if (runner.runProcess("certificates") == 0) {
                return runner.getResponses().stream()
                        .map(line -> pattern.matcher(line.trim()))
                        .filter(Matcher::find)
                        .map(matcher -> matcher.group(1))
                        .filter(Objects::nonNull)
                        .filter(Packager::verifyCertificate)
                        .sorted(Comparator.comparing(s -> s.toLowerCase(Locale.ROOT)))
                        .collect(Collectors.toList());
            }
        } catch (IOException | InterruptedException e) {
            Logger.logFatal(e, "There was an error retrieving certificates: " + e.getMessage());
        }
        return new ArrayList<>();
    }

    private static boolean verifyCertificate(String certificate) {
        ProcessRunner runner = new ProcessRunner("/bin/sh", "-c", "security find-certificate -c \"" + certificate
                + "\" -p | openssl x509 -checkend 0 > /dev/null && echo VALID || echo EXPIRED");
        try {
            if (runner.runProcess("certificate") == 0) {
                return "VALID".equals(runner.getResponse());
            }
        } catch (IOException | InterruptedException e) {
            Logger.logFatal(e, "There was an error retrieving certificate: " + e.getMessage());
        }
        return false;
    }
}
