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

import com.dd.plist.NSArray;
import com.dd.plist.NSData;
import com.dd.plist.NSDate;
import com.dd.plist.NSDictionary;
import com.dd.plist.NSNumber;
import com.dd.plist.NSObject;
import com.dd.plist.NSString;
import com.dd.plist.PropertyListFormatException;
import com.dd.plist.PropertyListParser;
import com.gluonhq.substrate.util.Logger;
import org.bouncycastle.cms.CMSSignedData;
import org.xml.sax.SAXException;

import javax.xml.parsers.ParserConfigurationException;
import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.text.ParseException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class NSDictionaryEx {

    private NSDictionary dictionary;

    public NSDictionaryEx() {
        this(new NSDictionary());
    }

    NSDictionaryEx(NSDictionary dict) {
        this.dictionary = Objects.requireNonNull(dict);
    }

    public NSDictionaryEx(Path path) throws ParserConfigurationException, ParseException, SAXException, PropertyListFormatException, IOException {
        this((NSDictionary) PropertyListParser.parse(path.toFile()));
    }

    public NSDictionaryEx(File file) throws ParserConfigurationException, ParseException, SAXException, PropertyListFormatException, IOException {
        this((NSDictionary) PropertyListParser.parse(file));
    }

    public NSDictionaryEx(String filePath) throws PropertyListFormatException, ParserConfigurationException, SAXException, ParseException, IOException {
        this((NSDictionary) PropertyListParser.parse(filePath));
    }

    public NSDictionaryEx(byte[] bytes) throws ParserConfigurationException, ParseException, SAXException, PropertyListFormatException, IOException {
        this((NSDictionary) PropertyListParser.parse(bytes));
    }

    public NSDictionaryEx(InputStream inputStream) throws ParserConfigurationException, ParseException, SAXException, PropertyListFormatException, IOException {
        this((NSDictionary) PropertyListParser.parse(inputStream));
    }

    public void saveAsXML(Path destination) throws IOException {
        PropertyListParser.saveAsXML(dictionary, destination.toFile());
    }

    public void saveAsBinary(Path destination) throws IOException {
        PropertyListParser.saveAsBinary(dictionary, destination.toFile());
    }

    public void put(String key, Object value) {
        dictionary.put(key, value);
    }

    public void put(String key, NSObject value) {
        dictionary.put(key, value);
    }

    public String[] getAllKeys() {
        return dictionary.allKeys();
    }

    public Set<String> getKeySet() {
        return dictionary.keySet();
    }

    public Set<Map.Entry<String, NSObject>> getEntrySet() {
        return dictionary.entrySet();
    }

    public NSObject get(String key) {
        return dictionary.objectForKey(key);
    }

    public void remove(String key) {
        dictionary.remove(key);
    }


    public boolean getBoolean(String key) {
        NSNumber nsNumber = (NSNumber) dictionary.objectForKey(key);
        if (nsNumber != null) {
            return nsNumber.boolValue();
        }
        return false;
    }

    public int getInteger(String key) {
        NSNumber nsNumber = (NSNumber) dictionary.objectForKey(key);
        if (nsNumber != null) {
            return nsNumber.intValue();
        }
        return 0;
    }

    public String getString(String key) {
        NSString nsString = (NSString) dictionary.objectForKey(key);
        if (nsString != null) {
            return nsString.toString();
        }
        return "";
    }

    public LocalDate getDate(String key) {
        Date date = ((NSDate) dictionary.objectForKey(key)).getDate();
        return Instant.ofEpochMilli(date.getTime()).atZone(ZoneId.systemDefault()).toLocalDate();
    }

    public NSObject[] getArray(String key) {
        return ((NSArray) dictionary.objectForKey(key)).getArray();
    }

    public String getFirstString(String key) {
        List<String> array = getArrayString(key);
        if (array != null && ! array.isEmpty()) {
            return array.get(0);
        }
        return null;
    }

    public List<String> getArrayString(String key) {
        NSObject nsObject = dictionary.objectForKey(key);
        if (nsObject != null && nsObject instanceof NSArray) {
            NSObject[] array = ((NSArray) nsObject).getArray();
            if (array != null && array.length > 0) {
                return Stream.of(array)
                        .map(Object::toString)
                        .collect(Collectors.toList());
            }
        }
        return null;
    }

    public NSDictionaryEx getDictionary(String key) {
        return new NSDictionaryEx(((NSDictionary) dictionary.objectForKey(key)));
    }

    public static NSDictionaryEx dictionaryFromProvisioningPath(Path provisioningPath) {
        NSDictionaryEx dictionary = null;
        try (InputStream is = new BufferedInputStream(Files.newInputStream(provisioningPath))) {
            CMSSignedData signedData = new CMSSignedData(is);
            byte[] content = (byte[]) signedData.getSignedContent().getContent();
            dictionary = new NSDictionaryEx(content);
        } catch (Exception e) {
            Logger.logSevere(e,"Error creating NSDictionaryEx for path " + provisioningPath);
        }
        return dictionary;
    }

    public static List<String> certificates(NSObject[] nsObjects) {
        return Stream.of(nsObjects)
            .map(NSData.class::cast)
            .map(data -> getCertificate(data.bytes()))
            .collect(Collectors.toList());
    }

    private static String getCertificate(byte[] certificateData) {
        try {
            CertificateFactory x509Factory = CertificateFactory.getInstance("X.509");
            ByteArrayInputStream bais = new ByteArrayInputStream(certificateData);
            X509Certificate certificate = (X509Certificate) x509Factory.generateCertificate(bais);
            MessageDigest md = MessageDigest.getInstance("SHA-1");
            return bytesToHex(md.digest(certificate.getEncoded()));
        } catch (CertificateException | NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
    }

    private final static char[] hexArray = "0123456789ABCDEF".toCharArray();

    private static String bytesToHex(byte[] bytes) {
        char[] hexChars = new char[bytes.length * 2];
        for (int j = 0; j < bytes.length; j++) {
            int v = bytes[j] & 0xFF;
            hexChars[j * 2] = hexArray[v >>> 4];
            hexChars[j * 2 + 1] = hexArray[v & 0x0F];
        }
        return new String(hexChars);
    }
}
