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

import java.util.stream.Stream;

import static org.gradle.internal.impldep.org.junit.Assert.assertEquals;

class InternalProjectConfigurationTest {

    @ParameterizedTest
    @MethodSource("versioningSchemeParameters")
    void testIsOldGraalVMVersioningScheme(String version, boolean usesOldScheme) {
        Version javaVersion = new Version(version);
        InternalProjectConfiguration config = new InternalProjectConfiguration(new ProjectConfiguration("", ""));
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
}
