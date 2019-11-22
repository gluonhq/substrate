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

import com.gluonhq.substrate.model.ProjectConfiguration;
import com.gluonhq.substrate.model.Triplet;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SubstrateTest {

    @Test
    void testTriplets() {
        Triplet triplet = new Triplet(Constants.Profile.LINUX);
        assertEquals(triplet.getArch(), Constants.ARCH_AMD64);
        assertEquals(triplet.getVendor(), Constants.VENDOR_LINUX);
        assertEquals(triplet.getOs(), Constants.OS_LINUX);

        triplet = new Triplet(Constants.Profile.MACOS);
        assertEquals(triplet.getArch(), Constants.ARCH_AMD64);
        assertEquals(triplet.getVendor(), Constants.VENDOR_APPLE);
        assertEquals(triplet.getOs(), Constants.OS_DARWIN);
    }

    @Test
    void testWindowsTriplet() {
        Triplet triplet = new Triplet(Constants.Profile.WINDOWS);
        assertEquals(triplet.getArch(), Constants.ARCH_AMD64);
        assertEquals(triplet.getVendor(), Constants.VENDOR_MICROSOFT);
        assertEquals(triplet.getOs(), Constants.OS_WINDOWS);
    }

    @Test
    void testIOSTriplet() {
        Triplet triplet = new Triplet(Constants.Profile.IOS);
        Triplet me = Triplet.fromCurrentOS();
        ProjectConfiguration config = new ProjectConfiguration();
        config.setTarget(triplet);
        // when on linux, nativeCompile should throw an illegalArgumentException
        if (me.getOs().indexOf("nux") > 0) {
            assertThrows(IllegalArgumentException.class, () -> {
                SubstrateDispatcher.nativeCompile(null, config, null);
            });
        }
    }

    @Test
    void testAssertGraal() {
        ProjectConfiguration config = new ProjectConfiguration();
        assertThrows(NullPointerException.class, () -> SubstrateDispatcher.assertGraalVM(null));
        assertThrows(IllegalArgumentException.class, () -> SubstrateDispatcher.assertGraalVM(config));
    }

    @Test
    void testMainClassName() {
        ProjectConfiguration config = new ProjectConfiguration();
        assertThrows(NullPointerException.class, () -> config.setMainClassName(null));
        config.setMainClassName("a.b.foo");
        assertEquals("a.b.foo", config.getMainClassName());
        config.setMainClassName("name/a.b.foo");
        assertEquals("a.b.foo", config.getMainClassName());
    }
}
