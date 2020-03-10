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
#include "grandroid.h"

jmethodID ble_startScannerMethod;
int handlesInitialized = 0;

void registerAttachMethodHandles() {
    if (handlesInitialized > 0) return;
    JNIEnv* androidEnv;
    (*androidVM)->AttachCurrentThread(androidVM, (JNIEnv **)&androidEnv, NULL);
    ble_startScannerMethod = (*androidEnv)->GetStaticMethodID(androidEnv, activityClass, "attach_ble_startScanner", "()V");
    (*androidVM)->DetachCurrentThread(androidVM);
    handlesInitialized = 1;
}

// From Android to native
JNIEXPORT void JNICALL Java_com_gluonhq_helloandroid_MainActivity_nativeDispatchLifecycleEvent(JNIEnv *env, jobject activity, jstring event)
{
    const char *chars = (*env)->GetStringUTFChars(env, event, NULL);
    LOGE(stderr, "Dispatching lifecycle event from native Dalvik layer: %s", chars);
    attach_setLifecycleEvent(chars);
    (*env)->ReleaseStringUTFChars(env, event, chars);
}

// From Graal to Android

void module_ble_startScanning() {
    JNIEnv* androidEnv;
    fprintf(stderr, "[JVDBG] AttachSubstrate, startScanning 0\n");
    registerAttachMethodHandles();
    fprintf(stderr, "[JVDBG] AttachSubstrate, startScanning 1\n");
    (*androidVM)->AttachCurrentThread(androidVM, (JNIEnv **)&androidEnv, NULL);
    fprintf(stderr, "[JVDBG] AttachSubstrate, startScanning 2\n");
    (*androidEnv)->CallStaticVoidMethod(androidEnv, activityClass, ble_startScannerMethod);
    fprintf(stderr, "[JVDBG] AttachSubstrate, startScanning 3\n");
    (*androidVM)->DetachCurrentThread(androidVM);
    fprintf(stderr, "[JVDBG] AttachSubstrate, startScanning 4\n");

}
