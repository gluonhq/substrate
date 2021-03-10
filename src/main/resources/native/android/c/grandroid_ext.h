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

#include <jni.h>
#include <pthread.h>
#include <unistd.h>
#include <android/log.h>
#include <android/native_window_jni.h>

#define  ENABLE_DEBUG_LOG 1
#define  LOG_TAG "GraalGluon"

#if ENABLE_DEBUG_LOG == 1
#define  LOGD(ignore, ...)  __android_log_print(ANDROID_LOG_DEBUG, LOG_TAG, __VA_ARGS__) 
#define  LOGE(ignore, ...)  __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)
#else
#define  LOGD(ignore, ...)
#define  LOGE(ignore, ...)
#endif


extern jclass activityClass;
extern jobject activity;

// expose AndroidVM, MainActivity and its class
JavaVM* substrateGetAndroidVM();
jclass substrateGetActivityClass();
jclass substrateGetPermissionActivityClass();
jobject substrateGetActivity();

// Attach

#ifdef SUBSTRATE
void __attribute__((weak)) attach_setActivityResult(jint requestCode, jint resultCode, jobject intent) {}
void __attribute__((weak)) attach_setLifecycleEvent(const char *event) {}
#else
void attach_setActivityResult(jint requestCode, jint resultCode, jobject intent);
void attach_setLifecycleEvent(const char *event);
#endif

#define ATTACH_GRAAL() \
    JNIEnv *graalEnv; \
    JavaVM* graalVM = getGraalVM(); \
    int attach_graal_det = ((*graalVM)->GetEnv(graalVM, (void **)&graalEnv, JNI_VERSION_1_6) == JNI_OK); \
    (*graalVM)->AttachCurrentThreadAsDaemon(graalVM, (void **) &graalEnv, NULL); \
    LOGD(stderr, "ATTACH_GRAAL, tid = %d, existed? %d, graalEnv at %p\n", gettid(), attach_graal_det, graalEnv);

#define DETACH_GRAAL() \
    LOGD(stderr, "DETACH_GRAAL, tid = %d, graalVM = %p, existed = %d, env at %p\n", gettid(), graalVM, attach_graal_det, graalEnv); \
    if (attach_graal_det == 0) (*graalVM)->DetachCurrentThread(graalVM);

#define ATTACH_DALVIK() \
    JNIEnv *dalvikEnv; \
    JavaVM* dalvikVM = substrateGetAndroidVM(); \
    int attach_dalvik_det = ((*dalvikVM)->GetEnv(dalvikVM, (void **)&dalvikEnv, JNI_VERSION_1_6) == JNI_OK); \
    (*dalvikVM)->AttachCurrentThreadAsDaemon(dalvikVM, (void **) &dalvikEnv, NULL); \
    LOGD(stderr, "ATTACH_DALVIK, tid = %d, existed? %d, dalvikEnv at %p\n", gettid(), attach_dalvik_det, dalvikEnv);

#define DETACH_DALVIK() \
    LOGD(stderr, "DETACH_DALVIK, tid = %d, existed = %d, env at %p\n", gettid(), attach_dalvik_det, dalvikEnv); \
    if (attach_dalvik_det == 0) (*dalvikVM)->DetachCurrentThread(dalvikVM);

