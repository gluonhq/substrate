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
import com.gluonhq.substrate.target.Architecture;
import com.gluonhq.substrate.target.OS;
import com.gluonhq.substrate.target.TripletProfile;
import com.gluonhq.substrate.target.Vendor;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SubstrateTest {

    @Test
    void testTriplets() {
        Triplet triplet = new Triplet(TripletProfile.LINUX);
        assertEquals(triplet.getArch(), Architecture.AMD64);
        assertEquals(triplet.getVendor(), Vendor.LINUX);
        assertEquals(triplet.getOs(), OS.LINUX);

        triplet = new Triplet(TripletProfile.MACOS);
        assertEquals(triplet.getArch(), Architecture.AMD64);
        assertEquals(triplet.getVendor(), Vendor.APPLE);
        assertEquals(triplet.getOs(), OS.DARWIN);
    }

    @Test
    void testWindowsTriplet() {
        Triplet triplet = new Triplet(TripletProfile.WINDOWS);
        assertEquals(triplet.getArch(), Architecture.AMD64);
        assertEquals(triplet.getVendor(), Vendor.MICROSOFT);
        assertEquals(triplet.getOs(), OS.WINDOWS);
    }

    @Test
    void testIOSTriplet() throws IOException {
        Triplet iosTriplet = new Triplet(TripletProfile.IOS);
        Triplet currentOsTriplet = Triplet.fromCurrentOS();
        ProjectConfiguration config = new ProjectConfiguration("");
        config.setTarget(iosTriplet);

        // when on linux, nativeCompile should throw an illegalArgumentException
        if (currentOsTriplet.getOs().toString().indexOf("nux") > 0) {
            var dispatcher = new SubstrateDispatcher(Path.of(System.getProperty("user.home")), config);
            assertThrows(NullPointerException.class, () -> dispatcher.nativeCompile(null));
        }
    }

    @Test
    void testAssertGraal() {
        ProjectConfiguration publicConfig = new ProjectConfiguration("");
        InternalProjectConfiguration config = new InternalProjectConfiguration(publicConfig);
        assertThrows(NullPointerException.class, config::canRunNativeImage);
    }

    @Test
    void testMainClassName() {
        assertThrows(NullPointerException.class, () -> new ProjectConfiguration(null));;
        var config = new InternalProjectConfiguration( new ProjectConfiguration("a.b.Foo"));
        assertEquals("a.b.Foo", config.getMainClassName());
        config = new InternalProjectConfiguration( new ProjectConfiguration("name/a.b.Foo"));
        assertEquals("a.b.Foo", config.getMainClassName());
    }
}
