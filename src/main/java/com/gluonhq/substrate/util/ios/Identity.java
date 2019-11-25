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
package com.gluonhq.substrate.util.ios;

import java.util.regex.Pattern;

public class Identity {

    static final Pattern IDENTITY_PATTERN = Pattern.compile("^\\d+\\)\\s+([0-9A-F]+)\\s+\"([^\"]*)\"\\s*(.*)");
    static final Pattern IDENTITY_NAME_PATTERN = Pattern.compile("(?i)iPhone Developer|Apple Development|iOS Development|iPhone Distribution");

    static final String IDENTITY_ERROR_FLAG = "CSSMERR";

    private final String commonName;
    private final String sha1;

    public Identity(String sha1, String commonName) {
        this.sha1 = sha1;
        this.commonName = commonName;
    }

    public String getCommonName() {
        return commonName;
    }

    public String getSha1() {
        return sha1;
    }

    @Override
    public String toString() {
        return "SigningIdentity{" +
                "name='" + commonName + '\'' +
                ", sha1='" + sha1 + '\'' +
                '}';
    }
}
