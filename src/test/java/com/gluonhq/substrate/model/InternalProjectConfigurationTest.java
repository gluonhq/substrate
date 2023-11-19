/*
 * Copyright (c) 2023, Gluon
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
package com.gluonhq.substrate.model;

import com.gluonhq.substrate.ProjectConfiguration;
import com.gluonhq.substrate.util.Version;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.io.IOException;
import java.nio.file.Path;
import java.util.stream.Stream;

import static org.gradle.internal.impldep.org.junit.Assert.assertEquals;

class InternalProjectConfigurationTest {

    @ParameterizedTest
    @MethodSource("versioningSchemeParameters")
    void testIsOldGraalVMVersioningScheme(String version, boolean usesOldScheme) throws IOException {
        Version javaVersion = new Version(version);
        ProjectConfiguration publicConfig = new ProjectConfiguration("", "");
        publicConfig.setGraalPath(Path.of(System.getenv("GRAALVM_HOME")));
        InternalProjectConfiguration config = new InternalProjectConfiguration(publicConfig);
        assertEquals(usesOldScheme, config.isOldGraalVMVersioningScheme(javaVersion));
    }

    static Stream<Arguments> versioningSchemeParameters() {
        return Stream.of(
                Arguments.of("11.0.0", true),
                Arguments.of("12.0.0", true),
                Arguments.of("13.0.0", true),
                Arguments.of("14.0.0", true),
                Arguments.of("15.0.0", true),
                Arguments.of("16.0.0", true),
                Arguments.of("17.0.0", true),
                Arguments.of("17.0.6", true),
                Arguments.of("17.0.7", false),
                Arguments.of("17.0.8", false),
                Arguments.of("17.0.9", false),
                Arguments.of("18.0.0", true),
                Arguments.of("19.0.0", true),
                Arguments.of("20.0.1", false),
                Arguments.of("21.0.0", false),
                Arguments.of("21.0.1", false),
                Arguments.of("21.0.2", false),
                Arguments.of("22.0.0", false),
                Arguments.of("22.0.1", false),
                Arguments.of("22.0.2", false)
        );
    }

    @ParameterizedTest
    @MethodSource("graalVersionOutputs")
    void testParseGraalVMVersion(Version graalVersion, Version javaVersion, String output) {
        assertEquals(graalVersion.toString(), InternalProjectConfiguration.parseGraalVersion(output).toString());
    }

    @ParameterizedTest
    @MethodSource("graalVersionOutputs")
    void testParseGraalVMJavaVersion(Version graalVersion, Version javaVersion, String output) {
        assertEquals(javaVersion.toString(), InternalProjectConfiguration.parseGraalVMJavaVersion(output).toString());
    }

    static Stream<Arguments> graalVersionOutputs() {
        // outputs of 'java -version'
        return Stream.of(
                // ======== Gluon Builds ========

                // graalvm-svm-java17-linux-gluon-22.1.0.1-Final
                Arguments.of(new Version("22.1.0"), new Version("17.0.3"), "openjdk version \"17.0.3\" 2022-04-19\n" +
                        "OpenJDK Runtime Environment GraalVM 22.1.0.1 (build 17.0.3+7-jvmci-22.1-b06)\n" +
                        "OpenJDK 64-Bit Server VM GraalVM 22.1.0.1 (build 17.0.3+7-jvmci-22.1-b06, mixed mode, sharing)"),

                // graalvm-svm-java17-windows-gluon-22.0.0.3-Final
                Arguments.of(new Version("22.0.0"), new Version("17.0.2"), "openjdk version \"17.0.2\" 2022-01-18\n" +
                        "OpenJDK Runtime Environment GraalVM 22.0.0.2 (build 17.0.2+8-jvmci-22.0-b05)\n" +
                        "OpenJDK 64-Bit Server VM GraalVM 22.0.0.2 (build 17.0.2+8-jvmci-22.0-b05, mixed mode, sharing)"),

                // graalvm-svm-java17-windows-gluon-22.1.0.1-Final
                Arguments.of(new Version("22.1.0"), new Version("17.0.3"), "openjdk version \"17.0.3\" 2022-04-19\n" +
                        "OpenJDK Runtime Environment GraalVM 22.1.0.1 (build 17.0.3+7-jvmci-22.1-b06)\n" +
                        "OpenJDK 64-Bit Server VM GraalVM 22.1.0.1 (build 17.0.3+7-jvmci-22.1-b06, mixed mode, sharing)"),

                // ======== Oracle Builds ========

                // graalvm-jdk-17.0.7+8.1
                Arguments.of(new Version("17.0.7"), new Version("17.0.7"), "java version \"17.0.7\" 2023-04-18 LTS\n" +
                        "Java(TM) SE Runtime Environment Oracle GraalVM 17.0.7+8.1 (build 17.0.7+8-LTS-jvmci-23.0-b12)\n" +
                        "Java HotSpot(TM) 64-Bit Server VM Oracle GraalVM 17.0.7+8.1 (build 17.0.7+8-LTS-jvmci-23.0-b12, mixed mode, sharing)"),

                // graalvm-jdk-20.0.1+9.1
                Arguments.of(new Version("20.0.1"), new Version("20.0.1"), "java version \"20.0.1\" 2023-04-18\n" +
                        "Java(TM) SE Runtime Environment Oracle GraalVM 20.0.1+9.1 (build 20.0.1+9-jvmci-23.0-b12)\n" +
                        "Java HotSpot(TM) 64-Bit Server VM Oracle GraalVM 20.0.1+9.1 (build 20.0.1+9-jvmci-23.0-b12, mixed mode, sharing)"),

                // graalvm-jdk-21+35.1
                Arguments.of(new Version("21"), new Version("21"), "java version \"21\" 2023-09-19\n" +
                        "Java(TM) SE Runtime Environment Oracle GraalVM 21+35.1 (build 21+35-jvmci-23.1-b15)\n" +
                        "Java HotSpot(TM) 64-Bit Server VM Oracle GraalVM 21+35.1 (build 21+35-jvmci-23.1-b15, mixed mode, sharing)"),

                // graalvm-jdk-17.0.9+11.1
                Arguments.of(new Version("17.0.9"), new Version("17.0.9"), "java version \"17.0.9\" 2023-10-17 LTS\n" +
                        "Java(TM) SE Runtime Environment Oracle GraalVM 17.0.9+11.1 (build 17.0.9+11-LTS-jvmci-23.0-b21)\n" +
                        "Java HotSpot(TM) 64-Bit Server VM Oracle GraalVM 17.0.9+11.1 (build 17.0.9+11-LTS-jvmci-23.0-b21, mixed mode, sharing)"),

                // graalvm-jdk-21.0.1+12.1
                Arguments.of(new Version("21.0.1"), new Version("21.0.1"), "java version \"21.0.1\" 2023-10-17\n" +
                        "Java(TM) SE Runtime Environment Oracle GraalVM 21.0.1+12.1 (build 21.0.1+12-jvmci-23.1-b19)\n" +
                        "Java HotSpot(TM) 64-Bit Server VM Oracle GraalVM 21.0.1+12.1 (build 21.0.1+12-jvmci-23.1-b19, mixed mode, sharing)"),

                Arguments.of(new Version("17.0.7"), new Version("17.0.7"), "openjdk version \"17.0.7\" 2023-04-18\n" +
                        "OpenJDK Runtime Environment GraalVM CE 17.0.7+7.1 (build 17.0.7+7-jvmci-23.0-b12)\n" +
                        "OpenJDK 64-Bit Server VM GraalVM CE 17.0.7+7.1 (build 17.0.7+7-jvmci-23.0-b12, mixed mode, sharing)"),

                Arguments.of(new Version("21"), new Version("21"), "openjdk version \"21\" 2023-09-19\n" +
                        "OpenJDK Runtime Environment GraalVM CE 21+35.1 (build 21+35-jvmci-23.1-b15)\n" +
                        "OpenJDK 64-Bit Server VM GraalVM CE 21+35.1 (build 21+35-jvmci-23.1-b15, mixed mode, sharing)")

        );
    }

}
