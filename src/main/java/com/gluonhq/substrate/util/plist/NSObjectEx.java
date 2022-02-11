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
import com.dd.plist.NSString;
import com.dd.plist.PropertyListParser;
import com.gluonhq.substrate.util.Logger;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Objects;
import java.util.Optional;

public class NSObjectEx {

    private final NSObject object;

    NSObjectEx(NSObject object) {
        this.object = Objects.requireNonNull(object);
    }

    public NSObjectEx(Path path) throws Exception {
        this(PropertyListParser.parse(path.toFile()));
    }

    public void saveAsXML(Path destination) throws IOException {
        Objects.requireNonNull(destination, "destination can't be null");
        PropertyListParser.saveAsXML(object, destination.toFile());
    }

    public String getValueFromDictionary(String key) {
        Objects.requireNonNull(key, "key can't be null");

        try {
            NSDictionaryEx dict = findDictionary()
                    .orElseThrow(() -> new IOException("Error, dictionary not found"));
            return dict.getEntrySet().stream()
                    .filter(e -> e.getKey().equals(key))
                    .findFirst()
                    .map(e -> e.getValue().toString())
                    .orElseThrow(() -> new IOException(key + "was not found"));
        } catch (IOException ex) {
            Logger.logFatal(ex, "Could not find key: " + key);
        }
        return null;
    }

    public boolean setValueToDictionary(String key, String value) {
        Objects.requireNonNull(key, "key can't be null");
        Objects.requireNonNull(value, "value can't be null");

        NSDictionaryEx dict;
        try {
            dict = findDictionary()
                    .orElseThrow(() -> new IOException("Error, dictionary not found"));
        } catch (IOException ex) {
            Logger.logFatal(ex, "Could not set value for key: " + key);
            return false;
        }
        dict.getEntrySet().stream()
                .filter(e -> e.getKey().equals(key))
                .findFirst()
                .ifPresentOrElse(e -> e.setValue(new NSString(value)),
                        () -> Logger.logSevere("Key not found: " + key));
        return true;
    }

    public NSDictionaryEx getAsDictionaryEx() {
        if (object instanceof NSDictionary) {
            return new NSDictionaryEx((NSDictionary) object);
        }
        return null;
    }

    public NSArrayEx getAsArrayEx() {
        if (object instanceof NSArray) {
            return new NSArrayEx((NSArray) object);
        }
        return null;
    }

    private Optional<NSDictionaryEx> findDictionary() {
        NSDictionaryEx dict = getAsDictionaryEx();
        if (dict != null) {
            return Optional.of(dict);
        }
        NSArrayEx array = getAsArrayEx();
        if (array != null) {
            return array.getFirstDictionaryEx();
        }
        return Optional.empty();
    }

}
