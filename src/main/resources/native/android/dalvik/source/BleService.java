/*
 * Copyright (c) 2020, Gluon
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
package com.gluonhq.helloandroid;

import android.app.Activity;
import static android.app.Activity.RESULT_OK;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanRecord;
import android.bluetooth.le.ScanResult;

import android.content.Intent;

import java.util.Arrays;
import java.util.Formatter;
import java.util.LinkedList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

public class BleService  {

    private final MainActivity activity;
    private static final Logger LOG = Logger.getLogger(BleService.class.getName());
    private BluetoothLeScanner scanner;

    private final static int REQUEST_ENABLE_BT = 1001;
    private final List<String> uuids = new LinkedList<>();


    public BleService(MainActivity a) {
System.err.println("BLEService constructed");
        this.activity = a;
        init();
System.err.println("BLEService constructed done, start scanning now");
        startScanning();
System.err.println("BLEService constructed done111");
    }

    private void init() {
System.err.println("init 0");
        BluetoothAdapter adapter = BluetoothAdapter.getDefaultAdapter();
System.err.println("init 1");
        if (!adapter.isEnabled()) {
System.err.println("init 2");
            Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            IntentHandler intentHandler = new IntentHandler() {
                @Override
                public void gotActivityResult (int requestCode, int resultCode, Intent intent) {
System.err.println("init 3");
                    if (requestCode == REQUEST_ENABLE_BT && resultCode == RESULT_OK) {
                        scanner = adapter.getBluetoothLeScanner();
                    }
                }
            };

System.err.println("init 4");
            if (activity == null) {
                LOG.log(Level.WARNING, "Activity not found. This service is not allowed when "
                        + "running in background mode or from wearable");
                return;
            }
System.err.println("init 5");
            activity.setOnActivityResultHandler(intentHandler);

            // A dialog will appear requesting user permission to enable Bluetooth
System.err.println("init 6");
            activity.startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT);
System.err.println("init 7");

        } else {
System.err.println("init 8");
            scanner = adapter.getBluetoothLeScanner();
System.err.println("init 9");
        }

    }

    private ScanCallback scanCallback;

    private void startScanning() {
        if (scanner == null) {
            System.err.println("Scanner still null");
            return;
        }
            this.scanCallback = createCallback();

            scanner.startScan(scanCallback);

    }

    private ScanCallback createCallback() {
        ScanCallback answer = new ScanCallback() {
            @Override
            public void onScanResult(int callbackType, ScanResult result) {
System.err.println("BLESERVICE: ONSCANRESULT, callbacktype = "+callbackType);
                ScanRecord mScanRecord = result.getScanRecord();
                byte[] scanRecord = mScanRecord.getBytes();

                // https://www.pubnub.com/blog/2015-04-14-building-android-beacon-android-ibeacon-tutorial-overview/
                int index = 0;
                while (index < scanRecord.length) {
                    int length = scanRecord[index++];
                    if (length == 0) {
                        break;
                    }

                    int type = (scanRecord[index] & 0xff);
System.err.println("BLEService, type = "+type);

                    // process data
                    if (type == 0xff) {
                        processAD(scanRecord, index + 1, result.getRssi());
                        // ScanDetection detection = processAD(scanRecord, index + 1, result.getRssi());
                        // if (detection != null) {
                            // Platform.runLater(() -> callback.accept(detection));
                        // }
                    }

                    index += length;
                }
            }
        };
        return answer;
    }

   private void processAD(byte[] scanRecord, int init, int mRssi) {
        // AD: (Length FF) mID0 mID1 bID1 bID0 uuid15 ... uuid0 M1 M0 m1 m0 tx
        int startByte = init;
        // Manufacturer ID (little endian)
        // https://www.bluetooth.org/en-us/specification/assigned-numbers/company-identifiers
        int mID = ((scanRecord[startByte+1] & 0xff) << 8) | (scanRecord[startByte] & 0xff);

        startByte += 2;
        // Beacon ID (big endian)
        int beaconID = ((scanRecord[startByte] & 0xff) << 8) | (scanRecord[startByte+1] & 0xff);
        startByte += 2;
        // UUID (big endian)
        byte[] uuidBytes = Arrays.copyOfRange(scanRecord, startByte, startByte+16);
        String scannedUuid = ByteArrayToUUIDString(uuidBytes);
System.err.println("BLEService, uuid = "+scannedUuid);

        if (uuids.isEmpty() || uuids.contains(scannedUuid.toLowerCase())) {
            startByte += 16;
            // major (big endian)
            int major = ((scanRecord[startByte] & 0xff) << 8) | (scanRecord[startByte+1] & 0xff);
            startByte += 2;
            // minor (big endian)
            int minor = ((scanRecord[startByte] & 0xff) << 8) | (scanRecord[startByte+1] & 0xff);
            startByte += 2;
            // power in dB
            int power = (scanRecord[startByte] & 0xff);
            power -= 256;
            double acc = calculateAccuracy(power, mRssi);

            System.out.println("Scan: mID: "+mID+", beaconID: "+beaconID+", uuid: "+scannedUuid+
                    ", major: "+major+", minor: "+minor+", power: "+power+", distance: "+acc);

        }
    }

    private static String ByteArrayToUUIDString(byte[] ba) {
        StringBuilder hex = new StringBuilder();
        for (byte b : ba) {
            hex.append(new Formatter().format("%02x", b));
        }
        return hex.toString().replaceFirst("(\\p{XDigit}{8})(\\p{XDigit}{4})(\\p{XDigit}{4})(\\p{XDigit}{4})(\\p{XDigit}+)",
                "$1-$2-$3-$4-$5" );
    }

    private static double calculateAccuracy(int txPower, double rssi) {
        if (rssi == 0 || txPower == 0) {
            return -1.0; // if we cannot determine accuracy, return -1.
        }

        double ratio = rssi * 1.0 / txPower;
        if (ratio < 1.0) {
            return Math.pow(ratio, 10);
        } else {
            double accuracy = 0.89976 * Math.pow(ratio, 7.7095) + 0.111;
            return accuracy;
        }
    }




}
