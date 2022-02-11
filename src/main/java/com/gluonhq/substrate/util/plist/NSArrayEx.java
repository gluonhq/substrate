/*
 * Copyright (c) 2021, Gluon
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
package com.gluonhq.substrate.util.plist;

import com.dd.plist.NSArray;
import com.dd.plist.NSDictionary;
import com.dd.plist.NSObject;
import com.dd.plist.PropertyListFormatException;
import com.dd.plist.PropertyListParser;
import org.xml.sax.SAXException;

import javax.xml.parsers.ParserConfigurationException;
import java.io.IOException;
import java.nio.file.Path;
import java.text.ParseException;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Stream;

public class NSArrayEx {

    private final NSArray array;

    public NSArrayEx() {
        this(new NSArray());
    }

    NSArrayEx(NSArray array) {
        this.array = Objects.requireNonNull(array);
    }

    public NSArrayEx(Path path) throws ParserConfigurationException, ParseException, SAXException, PropertyListFormatException, IOException {
        this((NSArray) PropertyListParser.parse(path.toFile()));
    }

    public NSObject[] getArray() {
        return array.getArray();
    }

    public void saveAsXML(Path destination) throws IOException {
        PropertyListParser.saveAsXML(array, destination.toFile());
    }

    public void saveAsBinary(Path destination) throws IOException {
        PropertyListParser.saveAsBinary(array, destination.toFile());
    }

    public Optional<NSDictionaryEx> getFirstDictionaryEx() {
        return Stream.of(array.getArray())
                .filter(NSDictionary.class::isInstance)
                .map(NSDictionary.class::cast)
                .map(NSDictionaryEx::new)
                .findFirst();
    }

}
