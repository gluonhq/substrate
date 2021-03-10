/*
 * Copyright (c) 2020, Gluon
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
package com.gluonhq.substrate.config;

import com.gluonhq.substrate.model.ClassPath;
import com.gluonhq.substrate.util.FileOps;
import com.gluonhq.substrate.util.Logger;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import static com.gluonhq.substrate.Constants.META_INF_SUBSTRATE_DALVIK;
import static com.gluonhq.substrate.Constants.USER_ANDROID_DEPENDENCIES_FILE;
import static com.gluonhq.substrate.Constants.USER_ANDROID_PERMISSIONS_FILE;

/**
 * Helper class that helps scanning jars in the classpath looking for
 * files in META-INF/substrate/dalvik that contain information that has
 * to be added to the Android project
 */
public class AndroidResolver {

    private final List<File> jars;

    /**
     * AndroidResolver constructor
     *
     * @param classpath a string with the full classpath of the user's project
     * @throws IOException
     * @throws InterruptedException
     */
    public AndroidResolver(String classpath) throws IOException, InterruptedException {
        this.jars = new ClassPath(classpath).getJars(true);
    }

    /**
     * Walks through the jars in the classpath,
     * and looks for META-INF/substrate/dalvik/android-permissions.txt file.
     *
     * @return a list of permission lines that should be added to the AndroidManifest file
     * @throws IOException Exception while reading the permissions file.
     */
    public Set<String> getAndroidPermissions() throws IOException {
        Logger.logDebug("Scanning for android permission files");
        return Set.copyOf(scanJars(USER_ANDROID_PERMISSIONS_FILE));
    }

    /**
     * Walks through the jars in the classpath,
     * and looks for META-INF/substrate/dalvik/android-dependencies.txt file.
     *
     * @return a list of dependencies lines that should be added to the build.gradle file
     * @throws IOException Exception while reading the file.
     */
    public Set<String> getAndroidDependencies() throws IOException {
        Logger.logDebug("Scanning for android dependencies files");
        return Set.copyOf(scanJars(USER_ANDROID_DEPENDENCIES_FILE));
    }

    private List<String> scanJars(String configName) throws IOException {
        Objects.requireNonNull(configName, "configName can't be null");
        List<String> list = new ArrayList<>();
        for (File jar : jars) {
            if (!jar.exists()) {
                continue;
            }
            try (ZipFile zip = new ZipFile(jar)) {
                Logger.logDebug("Scanning " + jar);
                for (Enumeration<? extends ZipEntry> e = zip.entries(); e.hasMoreElements(); ) {
                    ZipEntry zipEntry = e.nextElement();
                    String name = zipEntry.getName();
                    if (!zipEntry.isDirectory() &&
                            (META_INF_SUBSTRATE_DALVIK + configName).equals(name)) {
                        Logger.logDebug("Adding content from " + zip.getName() + "::" + zipEntry.getName());
                        list.addAll(FileOps.readFileLines(zip.getInputStream(zipEntry)));
                    }
                }
            }
        }
        return list;
    }
}
