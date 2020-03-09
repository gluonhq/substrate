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
import android.content.Intent;

import java.util.logging.Level;
import java.util.logging.Logger;

public class BleService  {

    private final MainActivity activity;
    private static final Logger LOG = Logger.getLogger(BleService.class.getName());
    private BluetoothLeScanner scanner;

    private final static int REQUEST_ENABLE_BT = 1001;

    public BleService(MainActivity a) {
        this.activity = a;
        init();
    }

    private void init() {
        BluetoothAdapter adapter = BluetoothAdapter.getDefaultAdapter();
        if (!adapter.isEnabled()) {
            Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            IntentHandler intentHandler = new IntentHandler() {
                @Override
                public void gotActivityResult (int requestCode, int resultCode, Intent intent) {
                    if (requestCode == REQUEST_ENABLE_BT && resultCode == RESULT_OK) {
                        scanner = adapter.getBluetoothLeScanner();
                    }
                }
            };

            if (activity == null) {
                LOG.log(Level.WARNING, "Activity not found. This service is not allowed when "
                        + "running in background mode or from wearable");
                return;
            }
            activity.setOnActivityResultHandler(intentHandler);

            // A dialog will appear requesting user permission to enable Bluetooth
            activity.startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT);

        } else {
            scanner = adapter.getBluetoothLeScanner();
        }

    }

}
