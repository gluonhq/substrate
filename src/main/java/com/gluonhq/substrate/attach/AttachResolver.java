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
package com.gluonhq.substrate.attach;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

public class AttachResolver {

    public static final String DEPENDENCY_GROUP = "com.gluonhq.attach";
    public static final String DEPENDENCY_M2_GROUP = "com/gluonhq/attach/";
    public static final String UTIL_ARTIFACT = "util";

    /**
     * Extract Attach implementation Service classes
     * @param paths List of paths with all the jars in the classpath,
     *              including Attach jars
     * @return a list of Service classes, that can be added
     *          to reflection and jni lists
     */
    public static List<String> attachServices(List<Path> paths) {
        List<String> list = new ArrayList<>();
        paths.stream()
                .filter(s -> s.toString().contains(DEPENDENCY_GROUP) ||
                        s.toString().contains(DEPENDENCY_M2_GROUP))
                .forEach(jar -> {
                    try {
                        ZipFile zf = new ZipFile(jar.toFile());
                        zf.stream()
                                .map(ZipEntry::getName)
                                .filter(ze -> ze.endsWith("Service.class"))
                                .filter(ze -> ze.contains("impl") && ! ze.contains("Dummy")
                                        && ! ze.contains("Default"))
                                .forEach(ze -> list.add(ze
                                        .replaceAll("/", ".")
                                        .replace(".class", "")));
                    } catch (IOException ex) {
                        System.err.println("Error: " + ex);
ex.printStackTrace();
                    }
                });
        return list.stream()
                .distinct()
                .collect(Collectors.toList());
    }

}
