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
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Message;
import android.os.Messenger;
import android.os.RemoteException;
import android.util.Log;

import java.util.Arrays;

/**
 * If targetSdkVersion &gt;= 23, and any of the plugin requires a dangerous
 * permission, this activity must be added to the AndroidManifest.xml:
 *
 * <pre>
 * {@code
 * <activity a:name="com.gluonhq.helloandroid.PermissionRequestActivity" />
 * }
 * </pre>
 *
 * See list of dangerous permission here:
 * https://developer.android.com/guide/topics/permissions/requesting.html#normal-dangerous
 */
public class PermissionRequestActivity extends Activity {
    private static final String TAG     = "GraalActivity";

    private static final String KEY_PERMISSIONS = "permissions";
    private static final String KEY_MESSENGER = "messenger";
    private static final String KEY_REQUEST_CODE = "requestCode";
    private static final int PERMISSION_REQUEST_CODE = 10010;

    private static boolean result;
    private static Messenger messenger;
    private static int requestCode;

    @Override
    protected void onStart() {
        super.onStart();
        Log.v(TAG, "PermissionRequestActivity::onStart");
        String[] permissions = this.getIntent().getStringArrayExtra(KEY_PERMISSIONS);
        requestCode = this.getIntent().getIntExtra(KEY_REQUEST_CODE, 0);
        messenger = this.getIntent().getParcelableExtra(KEY_MESSENGER);
        if (permissions == null) {
            Log.e(TAG, "PermissionRequestActivity: no permissions were found");
            finish();
            return;
        }

        if (verify(this, permissions)) {
            int[] result = new int[permissions.length];
            Arrays.fill(result, PackageManager.PERMISSION_GRANTED);
            Log.v(TAG, "PermissionRequestActivity: All permissions are granted");
            processResult(result);
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Log.v(TAG, "PermissionRequestActivity::requesting permissions");
            requestPermissions(permissions, requestCode);
        } else {
            Log.v(TAG, "RequestPermissions requires Build.VERSION.SDK_INT (" + Build.VERSION.SDK_INT+ ") >= 23");
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        if (PermissionRequestActivity.requestCode == requestCode) {
            Log.v(TAG, "PermissionRequestActivity::onRequestPermissionsResult");
            processResult(grantResults);
        }
    }

    private void processResult(int[] grantResults) {
        try {
            Message message = Message.obtain();
            Bundle bundle = new Bundle();
            bundle.putIntArray(KEY_PERMISSIONS, grantResults);
            message.setData(bundle);
            messenger.send(message);
        } catch (RemoteException e) {
            e.printStackTrace();
        }
        finish();
    }

    private static void requestPermission(Activity activity, String[] permissionsName, Messenger messenger) {
        Intent intent = new Intent(activity, PermissionRequestActivity.class);
        intent.putExtra(KEY_PERMISSIONS, permissionsName);
        intent.putExtra(KEY_REQUEST_CODE, PERMISSION_REQUEST_CODE);
        intent.putExtra(KEY_MESSENGER, messenger);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

        Log.v(TAG, "PermissionRequestActivity::start intent to requestPermission");
        activity.startActivity(intent);
    }

    private static boolean verify(Activity activity, String[] permissionsName) {
        if (activity == null || permissionsName == null) {
            return false;
        }
        for (String permission : permissionsName) {
            int result = activity.checkSelfPermission(permission);
            if (result != PackageManager.PERMISSION_GRANTED) {
                Log.v(TAG, String.format("Permission %s is not granted", permission));
                return false;
            }
        }
        Log.v(TAG, "All requested permissions are granted");
        return true;
    }

    public static boolean verifyPermissions(final Activity activity, final String[] permissionsName) {
        Log.v(TAG, "PermissionRequestActivity::Calling verifyPermissions");
        if (verify(activity, permissionsName)) {
            return true;
        }
        final HandlerThread handlerThread = new HandlerThread("handler thread");
        handlerThread.start();

        Handler handler = new Handler(handlerThread.getLooper()) {
            @Override
            public void handleMessage(Message msg) {
                Log.v(TAG, "Received message in handler: " + msg);
                int[] results = msg.getData().getIntArray(KEY_PERMISSIONS);
                result = true;
                for (int i = 0; i < results.length; i++) {
                    if (results[i] != PackageManager.PERMISSION_GRANTED) {
                        result = false;
                        break;
                    }
                }
                handlerThread.quit();
            }
        };

        Messenger messenger = new Messenger(handler);
        requestPermission(activity, permissionsName, messenger);

        try {
            handlerThread.join();
        } catch (InterruptedException e) {
            return false;
        }
        return result;
    }

}