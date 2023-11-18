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
package com.gluonhq.substrate.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class LibTest {
    @Test
    void testNoBound() {
        Lib lib = Lib.of("");
        assertTrue(lib.inRange(0));
        assertTrue(lib.inRange(100));
    }

    @Test
    void testLowerBound() {
        Lib lib = Lib.from(10, "");
        assertFalse(lib.inRange(9));
        assertTrue(lib.inRange(10));
        assertTrue(lib.inRange(11));
    }

    @Test
    void testUpperBound() {
        Lib lib = Lib.upTo(10, "");
        assertTrue(lib.inRange(9));
        assertTrue(lib.inRange(10));
        assertFalse(lib.inRange(11));
    }

    @Test
    void testRange() {
        Lib lib = Lib.range(10, 12, "");
        assertFalse(lib.inRange(9));
        assertTrue(lib.inRange(10));
        assertTrue(lib.inRange(11));
        assertTrue(lib.inRange(12));
        assertFalse(lib.inRange(13));
    }
}
