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
package com.gluonhq.substrate.target;

import com.gluonhq.substrate.Constants;
import com.gluonhq.substrate.model.ClassPath;
import com.gluonhq.substrate.model.InternalProjectConfiguration;
import com.gluonhq.substrate.model.ProcessPaths;
import com.gluonhq.substrate.util.FileOps;
import com.gluonhq.substrate.util.Logger;
import com.gluonhq.substrate.util.web.AheadOfTimeBase;
import org.apidesign.vm4brwsr.ObfuscationLevel;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class WebTargetConfiguration extends AbstractTargetConfiguration {

    public static final List<String> WEB_AOT_DEPENDENCIES = List.of(
            "com.gluonhq:webscheduler:1.0.5",
            "com.gluonhq.compat:javadate:1.1",
            "com.gluonhq.compat:javafunctions:1.1",
            "com.gluonhq.compat:javanio:1.2",
            "org.apidesign.bck2brwsr:emul:" + Constants.WEB_AOT_VERSION,
            "org.apidesign.bck2brwsr:emul:" + Constants.WEB_AOT_VERSION + ":" + Constants.WEB_AOT_CLASSIFIER,
            "org.apidesign.bck2brwsr:emul.zip:" + Constants.WEB_AOT_VERSION,
            "org.apidesign.bck2brwsr:emul.zip:" + Constants.WEB_AOT_VERSION + ":" + Constants.WEB_AOT_CLASSIFIER);

    private static final List<String> webFiles = List.of("uongl.js", "webgl-debug.js");

    private final String sourceOS;
    private final Path rootPath;

    public WebTargetConfiguration(ProcessPaths paths, InternalProjectConfiguration configuration) {
        super(paths, configuration);
        this.sourceOS = projectConfiguration.getTargetTriplet().getOs();
        rootPath = paths.getSourcePath().resolve(sourceOS);
    }

    @Override
    public boolean compile() throws IOException, InterruptedException {
        final List<File> jars = new ClassPath(projectConfiguration.getClasspath()).getJars(true);

        Path webPath = paths.getGvmPath().resolve("web");
        if (!Files.exists(webPath)) {
            Files.createDirectory(webPath);
        }
        Path libPath = paths.getGvmPath().resolve("web").resolve("lib");
        if (!Files.exists(libPath)) {
            Files.createDirectory(libPath);
        }
        File mainJar = webPath.resolve(projectConfiguration.getAppName().concat(".jar")).toFile();
        File classes = jars.stream()
                .filter(f -> f.toString().endsWith("classes.jar"))
                .findFirst()
                .orElseThrow(() -> new IOException("Classes not found"));
        FileOps.copyFile(classes.toPath(), mainJar.toPath());
        jars.remove(classes);

        File mainJavaScript = webPath.resolve(projectConfiguration.getAppName().concat(".js")).toFile();

        Path userHtml = rootPath.resolve(Constants.WEB_INDEX_HTML);
        if (!Files.exists(userHtml)) {
            // copy index to gensrc/web
            Path genHtml = paths.getGenPath().resolve(sourceOS).resolve(Constants.WEB_INDEX_HTML);
            Logger.logDebug("Copy " + Constants.WEB_INDEX_HTML + " to " + genHtml.toString());
            FileOps.copyResource(Constants.WEB_NATIVE_FOLDER + Constants.WEB_INDEX_HTML, genHtml);
            FileOps.replaceInFile(genHtml, "WEB_TITLE", projectConfiguration.getAppName());
            FileOps.replaceInFile(genHtml, "WEB_APP_NAME", projectConfiguration.getAppName());
            FileOps.replaceInFile(genHtml, "WEB_MAIN_CLASS", projectConfiguration.getMainClassName());
            Logger.logInfo("Default " + Constants.WEB_INDEX_HTML + " generated in " + genHtml.toString() + ".\n" +
                    "Consider copying it to " + rootPath.toString() + " before performing any modification");
            FileOps.copyFile(genHtml, webPath.resolve(Constants.WEB_INDEX_HTML));
        } else {
            FileOps.copyFile(userHtml, webPath.resolve(Constants.WEB_INDEX_HTML));
        }
        for (String s : webFiles) {
            FileOps.copyResource(Constants.WEB_NATIVE_FOLDER + s, webPath.resolve(s));
        }

        class Work extends AheadOfTimeBase<File> {

            private final Map<File, String> artifacts = new HashMap<>();

            @Override
            protected File mainJavaScript() {
                return mainJavaScript;
            }

            @Override
            protected File libraryPath(String fileNameJs) {
                return libPath.resolve(fileNameJs).toFile();
            }

            @Override
            protected ObfuscationLevel obfuscation() {
                return ObfuscationLevel.NONE;
            }

            @Override
            protected String[] exports() {
                return new String[0];
            }

            @Override
            protected boolean ignoreBootClassPath() {
                return true;
            }

            @Override
            protected boolean generateAotLibraries() {
                return true;
            }

            @Override
            protected File mainJar() {
                return mainJar;
            }

            @Override
            protected File vm() {
                return webPath.resolve("bck2brwsr.js").toFile();
            }

            @Override
            protected Collection<File> artifacts() {
                return jars;
            }

            @Override
            protected void logInfo(String msg) {
                if (projectConfiguration.isVerbose()) {
                    Logger.logInfo(msg);
                } else {
                    Logger.logDebug(msg);
                }
            }

            @Override
            protected Exception failure(String msg, Throwable cause) {
                if (cause != null) {
                    return new Exception(msg, cause);
                } else {
                    return new Exception(msg);
                }
            }

            @Override
            protected File file(File a) {
                setArtifact(a);
                return a;
            }

            @Override
            protected AheadOfTimeBase.Scope scope(File a) {
                return Scope.RUNTIME;
            }

            // m2 file:
            // ~/.m2/repository/$groupId/$artifactId/$version/$artifactId-$version-$classifier.jar

            private void setArtifact(File a) {
                String artifact = null;
                if (a.toString().contains(".m2")) {
                    Path path = a.toPath();
                    int m2Index = 0;
                    while (!".m2".equals(path.getName(m2Index++).toString())) { }
                    String groupId = path.subpath(m2Index + 1, path.getNameCount() - 3).toString().replaceAll(File.separator, ".");
                    String artifactId = path.getName(path.getNameCount() - 3).toString();
                    String version = path.getName(path.getNameCount() - 2).toString();
                    artifact = groupId + ":" + artifactId + ":" + version;

                    String last = path.getName(path.getNameCount() - 1).toString();
                    String lastPrefix = artifactId(a) + "-" + version(a) + "-";
                    if (last.startsWith(lastPrefix)) {
                        String classifier = last.substring(lastPrefix.length(), last.length() - 4);
                        artifact += ":" + classifier;
                    }
                    artifacts.put(a, artifact);
                }
            }

            @Override
            protected String groupId(File a) {
                String artifact = artifacts.get(a);
                if (artifact != null) {
                    return artifact.split(":")[0];
                }
                return null;
            }

            @Override
            protected String artifactId(File a) {
                String artifact = artifacts.get(a);
                if (artifact != null) {
                    return artifact.split(":")[1];
                }
                return null;
            }

            @Override
            protected String version(File a) {
                String artifact = artifacts.get(a);
                if (artifact != null) {
                    return artifact.split(":")[2];
                }
                return null;
            }

            @Override
            protected String classifier(File a) {
                String artifact = artifacts.get(a);
                if (artifact != null) {
                    String[] split = artifact.split(":");
                    return split.length < 4 ? null : split[3];
                }
                return null;
            }

        }
        new Work().work();

        return true;
    }

    @Override
    public boolean link() throws IOException, InterruptedException {
        return true;
    }

}