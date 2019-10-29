/*
 * Copyright (c) 2019, Gluon
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
package com.gluonhq.substrate.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class VersionTest {

    @Test
    public void constructsVersionWithMajor() {
        Version version = new Version(5);
        assertEquals(5, version.getMajor());
        assertEquals(0, version.getMinor());
        assertEquals(0, version.getPatch());
        assertEquals("5", version.toString());
    }

    @Test
    public void constructsVersionWithMajorAndMinor() {
        Version version = new Version(5, 4);
        assertEquals(5, version.getMajor());
        assertEquals(4, version.getMinor());
        assertEquals(0, version.getPatch());
        assertEquals("5.4", version.toString());
    }

    @Test
    public void constructsVersionWithMajorAndMinorAndPatch() {
        Version version = new Version(5, 4, 28);
        assertEquals(5, version.getMajor());
        assertEquals(4, version.getMinor());
        assertEquals(28, version.getPatch());
        assertEquals("5.4.28", version.toString());
    }

    @Test
    public void constructsVersionWithMajorAndPatch() {
        Version version = new Version(5, 0, 1);
        assertEquals(5, version.getMajor());
        assertEquals(0, version.getMinor());
        assertEquals(1, version.getPatch());
        assertEquals("5.0.1", version.toString());
    }

    @Test
    public void comparesVersionsWithMajor() {
        Version version1 = new Version(2);
        Version version2 = new Version(5);
        assertEquals(-1, version1.compareTo(version2));
        assertEquals(1, version2.compareTo(version1));
    }

    @Test
    public void comparesVersionsWithMajorAndMinor() {
        Version version1 = new Version(2, 3);
        Version version2 = new Version(2, 6);
        assertEquals(-1, version1.compareTo(version2));
        assertEquals(1, version2.compareTo(version1));
    }

    @Test
    public void comparesVersionsWithMajorAndMinorAndPatch() {
        Version version1 = new Version(2, 3, 8);
        Version version2 = new Version(2, 3, 19);
        assertEquals(-1, version1.compareTo(version2));
        assertEquals(1, version2.compareTo(version1));
    }
}
