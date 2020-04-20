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

jclass jUtilClass;
jclass jBleServiceClass;
jclass jBrowserServiceClass;
jclass jDisplayServiceClass;
jclass jKeyboardServiceClass;
jclass jLifecycleServiceClass;
jclass jPositionServiceClass;
int handlesInitialized = 0;

jclass registerClass(JNIEnv* androidEnv, const char* name) {
    jclass jtmp = (*androidEnv)->FindClass(androidEnv, name);
    jthrowable t = (*androidEnv)->ExceptionOccurred(androidEnv);
    if (t) {
        (*androidEnv)->ExceptionClear(androidEnv);
    }
    if ((t == NULL) && (jtmp != NULL)) {
        return (jclass)(*androidEnv)->NewGlobalRef(androidEnv, jtmp);
    }
    return NULL;
}

void registerAttachMethodHandles(JNIEnv* androidEnv) {
    if (handlesInitialized > 0) {
        return;
    }
    jUtilClass = registerClass(androidEnv, "com/gluonhq/helloandroid/Util");
    jBleServiceClass = registerClass(androidEnv, "com/gluonhq/helloandroid/DalvikBleService");
    jBrowserServiceClass = registerClass(androidEnv, "com/gluonhq/helloandroid/DalvikBrowserService");
    jDisplayServiceClass = registerClass(androidEnv, "com/gluonhq/helloandroid/DalvikDisplayService");
    jKeyboardServiceClass = registerClass(androidEnv, "com/gluonhq/helloandroid/KeyboardService");
    jLifecycleServiceClass = registerClass(androidEnv, "com/gluonhq/helloandroid/DalvikLifecycleService");
    jPositionServiceClass = registerClass(androidEnv, "com/gluonhq/helloandroid/DalvikPositionService");
    handlesInitialized = 1;
}

jclass substrateGetUtilClass() {
    return jUtilClass;
}

jclass substrateGetBleServiceClass() {
    return jBleServiceClass;
}

jclass substrateGetBrowserServiceClass() {
    return jBrowserServiceClass;
}

jclass substrateGetDisplayServiceClass() {
    return jDisplayServiceClass;
}

jclass substrateGetKeyboardServiceClass() {
    return jKeyboardServiceClass;
}

jclass substrateGetLifecycleServiceClass() {
    return jLifecycleServiceClass;
}

jclass substrateGetPositionServiceClass() {
    return jPositionServiceClass;
}

// Lifecycle
JNIEXPORT void JNICALL Java_com_gluonhq_helloandroid_MainActivity_nativeDispatchLifecycleEvent(JNIEnv *env, jobject activity, jstring event)
{
    const char *chars = (*env)->GetStringUTFChars(env, event, NULL);
    LOGE(stderr, "Dispatching lifecycle event from native Dalvik layer: %s", chars);
    attach_setLifecycleEvent(chars);
    (*env)->ReleaseStringUTFChars(env, event, chars);
}

// Intent
JNIEXPORT void JNICALL Java_com_gluonhq_helloandroid_MainActivity_nativeDispatchActivityResult(JNIEnv *env, jobject activity, jint requestCode, jint resultCode, jobject intent)
{
    LOGE(stderr, "Dispatching activity result from native Dalvik layer: %d %d", requestCode, resultCode);
    attach_setActivityResult(requestCode, resultCode, intent);
}
