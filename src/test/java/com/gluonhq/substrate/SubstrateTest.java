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
package com.gluonhq.substrate;

import com.gluonhq.substrate.model.InternalProjectConfiguration;
import com.gluonhq.substrate.model.Triplet;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class SubstrateTest {

    @Test
    void testTriplets() {
        Triplet triplet = new Triplet(Constants.Profile.LINUX);
        assertEquals(Constants.ARCH_AMD64, triplet.getArch());
        assertEquals(Constants.VENDOR_LINUX, triplet.getVendor());
        assertEquals(Constants.OS_LINUX, triplet.getOs());

        triplet = new Triplet(Constants.Profile.MACOS);
        assertEquals(Constants.ARCH_AMD64, triplet.getArch());
        assertEquals(Constants.VENDOR_APPLE, triplet.getVendor());
        assertEquals(Constants.OS_DARWIN, triplet.getOs());
    }

    @Test
    void testWindowsTriplet() {
        Triplet triplet = new Triplet(Constants.Profile.WINDOWS);
        assertEquals(Constants.ARCH_AMD64, triplet.getArch());
        assertEquals(Constants.VENDOR_MICROSOFT, triplet.getVendor());
        assertEquals(Constants.OS_WINDOWS, triplet.getOs());
    }

    @Test
    void testIOSTripletOnLinux() {
        assumeTrue(Triplet.fromCurrentOS().getOs().indexOf("nux") > 0);
        Triplet iosTriplet = new Triplet(Constants.Profile.IOS);
        ProjectConfiguration config = new ProjectConfiguration("", "");
        config.setTarget(iosTriplet);
        config.setGraalPath(Path.of(System.getenv("GRAALVM_HOME")));
        Path userHome = Path.of(System.getProperty("user.home"));

        // when on linux, nativeCompile should throw an illegalArgumentException
        assertThrows(IllegalArgumentException.class, () -> new SubstrateDispatcher(userHome, config));
    }

    @Test
    void testAssertGraal() {
        ProjectConfiguration publicConfig = new ProjectConfiguration("", "");
        assertThrows(NullPointerException.class, () -> new InternalProjectConfiguration(publicConfig));
    }

    @Test
    void testMainClassName() throws IOException {
        assertThrows(NullPointerException.class, () -> new ProjectConfiguration(null, ""));
        ProjectConfiguration publicConfig = new ProjectConfiguration("a.b.Foo", "a.b-1.0.jar");
        publicConfig.setGraalPath(Path.of(System.getenv("GRAALVM_HOME")));
        var config = new InternalProjectConfiguration(publicConfig);
        assertEquals("a.b.Foo", config.getMainClassName());
        publicConfig = new ProjectConfiguration("name/a.b.Foo", "");
        publicConfig.setGraalPath(Path.of(System.getenv("GRAALVM_HOME")));
        config = new InternalProjectConfiguration(publicConfig);
        assertEquals("a.b.Foo", config.getMainClassName());
    }

    @Test
    void testClasspath() throws IOException {
        assertThrows(NullPointerException.class, () -> new ProjectConfiguration("", null));
        ProjectConfiguration publicConfig = new ProjectConfiguration("", "a.b-1.0.jar");
        publicConfig.setGraalPath(Path.of(System.getenv("GRAALVM_HOME")));
        var config = new InternalProjectConfiguration(publicConfig);
        assertEquals("a.b-1.0.jar", config.getClasspath());
    }

    @Test
    void testAssertSW() throws IOException {
        ProjectConfiguration publicConfig = new ProjectConfiguration("a.b.Foo", "");
        publicConfig.setGraalPath(Path.of(System.getenv("GRAALVM_HOME")));
        InternalProjectConfiguration config = new InternalProjectConfiguration(publicConfig);
        assertFalse(config.isUsePrismSW());

        publicConfig = new ProjectConfiguration("a.b.Foo", "");
        publicConfig.setGraalPath(Path.of(System.getenv("GRAALVM_HOME")));
        publicConfig.setUsePrismSW(true);
        config = new InternalProjectConfiguration(publicConfig);
        assertTrue(config.isUsePrismSW());
    }

    @Test
    void testAssertUseJavaFX() throws IOException {
        ProjectConfiguration publicConfig = new ProjectConfiguration("", "javafx-base-14-linux.jar");
        publicConfig.setGraalPath(Path.of(System.getenv("GRAALVM_HOME")));
        InternalProjectConfiguration config = new InternalProjectConfiguration(publicConfig);
        assertTrue(config.isUseJavaFX());

        publicConfig = new ProjectConfiguration("", "apache-commons.jar");
        publicConfig.setGraalPath(Path.of(System.getenv("GRAALVM_HOME")));
        config = new InternalProjectConfiguration(publicConfig);
        assertFalse(config.isUseJavaFX());
    }
}
