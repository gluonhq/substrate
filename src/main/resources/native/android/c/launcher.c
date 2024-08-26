/*
 * Copyright (c) 2020, 2022, Gluon
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
#include <stdlib.h>
#include <string.h>
#include <errno.h>
#include "grandroid.h"

extern int *run_main(int argc, char *argv[]);
extern void registerJavaFXMethodHandles(JNIEnv* aenv);

jclass activityClass;
jclass permissionActivityClass;
jobject activity;
jmethodID activity_showIME;
jmethodID activity_hideIME;

JavaVM *androidVM;
JNIEnv *androidEnv;
ANativeWindow *window;
jfloat density;

int start_logger(const char *app_name);

extern int __svm_vm_is_static_binary __attribute__((weak)) = 1;

// this array is filled during compile/link phases
const char *userArgs[] = {
// USER_RUNTIME_ARGS
};

const char *origArgs[] = {
    "myapp",
    "-Djavafx.platform=android",
    "-Dmonocle.platform=Android", // used in com.sun.glass.ui.monocle.NativePlatformFactory
    "-Dembedded=monocle",
    "-Dglass.platform=Monocle",
    "-Duse.egl=true",
    "-Dcom.sun.javafx.isEmbedded=true",
    "-Dcom.sun.javafx.touch=true",
    "-Dcom.sun.javafx.gestures.zoom=true",
    "-Dcom.sun.javafx.gestures.rotate=true",
    "-Dcom.sun.javafx.gestures.scroll=true",
    "-Djavafx.verbose=false",
    "-Dmonocle.input.touchRadius=1",
    "-Dmonocle.input.traceEvents.verbose=false",
    "-Dprism.verbose=true",
    "-Dprism.useFontConfig=true",
    "-Xmx4g"};

void registerMethodHandles(JNIEnv *aenv)
{
    activityClass = (*aenv)->NewGlobalRef(aenv, (*aenv)->FindClass(aenv, "com/gluonhq/helloandroid/MainActivity"));
    permissionActivityClass = (*aenv)->NewGlobalRef(aenv, (*aenv)->FindClass(aenv, "com/gluonhq/helloandroid/PermissionRequestActivity"));
    activity_showIME = (*aenv)->GetStaticMethodID(aenv, activityClass, "showIME", "()V");
    activity_hideIME = (*aenv)->GetStaticMethodID(aenv, activityClass, "hideIME", "()V");
    registerJavaFXMethodHandles(aenv);
}

int JNI_OnLoad(JavaVM *vm, void *reserved)
{
    androidVM = vm;
    (*vm)->GetEnv(vm, (void **)&androidEnv, JNI_VERSION_1_6);
    start_logger("GraalCompiled");
    registerMethodHandles(androidEnv);
    LOGE(stderr, "AndroidVM called JNI_OnLoad, vm = %p, androidEnv = %p", androidVM, androidEnv);
    return JNI_VERSION_1_6;
}

JavaVM* substrateGetAndroidVM() {
    return androidVM;
}

JNIEnv* substrateGetAndroidEnv() {
    return androidEnv;
}

jclass substrateGetActivityClass() {
    return activityClass;
}

jclass substrateGetPermissionActivityClass() {
    return permissionActivityClass;
}

jobject substrateGetActivity() {
    return activity;
}


// === called from DALVIK. Minimize work/dependencies here === //

JNIEXPORT void JNICALL Java_com_gluonhq_helloandroid_MainActivity_startGraalApp
        (JNIEnv *env, jobject activityObj, jobjectArray launchArgsArray)
{
    activity = (*env)->NewGlobalRef(env, activityObj);
    LOGE(stderr, "Start GraalApp, DALVIK env at %p\n", env);
    LOGE(stderr, "PAGESIZE = %ld\n", sysconf(_SC_PAGE_SIZE));
    LOGE(stderr, "EnvVersion = %d\n", (*env)->GetVersion(env));

    int userArgsSize = sizeof(userArgs) / sizeof(char *);
    int origArgsSize = sizeof(origArgs) / sizeof(char *);
    int launchArgsSize = (*env)->GetArrayLength(env, launchArgsArray);
    int argsSize = userArgsSize + origArgsSize + launchArgsSize;
    char **graalArgs = (char **)malloc(argsSize * sizeof(char *));
    for (int i = 0; i < origArgsSize; i++)
    {
         graalArgs[i] = (char *)origArgs[i];
    }
    for (int i = 0; i < launchArgsSize; i++)
    {
        jstring jlaunchItem = (jstring) ((*env)->GetObjectArrayElement(env, launchArgsArray, i));
        const char *launchString = (*env)->GetStringUTFChars(env, jlaunchItem, NULL);
        graalArgs[origArgsSize + i] = (char *)launchString;
    }
    for (int i = 0; i < userArgsSize; i++)
    {
        graalArgs[origArgsSize + launchArgsSize + i] = (char *)userArgs[i];
    }

    LOGE(stderr, "calling JavaMainWrapper_run with argsize: %d\n", argsSize);

    (*run_main)(argsSize, graalArgs);
    free(graalArgs);

    LOGE(stderr, "called JavaMainWrapper_run\n");
}

// == expose window functionality to JavaFX native code == //

ANativeWindow *_GLUON_getNativeWindow()
{
    return window;
}

float _GLUON_getDensity()
{
    return density;
}

ANativeWindow *getNativeWindow()
{
    return window;
}

// ======== missing functions ==== //

int *__errno_location(void)
{
    int *a = &errno;
    return a;
}

void getEnviron()
{
    LOGE(stderr, "\n\ngetEnviron NYI\n\n");
}

int getdtablesize() {
    return sysconf(_SC_OPEN_MAX);
}

// AWT: GraalVM native-image explicitly adds (unresolved) references to libawt
// so we need to make sure the JNI_OnLoad symbols are there.

void Java_java_awt_Font_initIDs() {
    fprintf(stderr, "We should never reach here (Java_java_awt_Font_initIDs)\n");
}

void Java_java_awt_Toolkit_initIDs() {
    fprintf(stderr, "We should never reach here (Java_java_awt_Toolkit_initIDs)\n");
}

void JNI_OnLoad_awt() {
    fprintf(stderr, "We should never reach here (JNI_OnLoad_awt)\n");
}

void JNI_OnLoad_awt_headless() {
    fprintf(stderr, "We should never reach here (JNI_OnLoad_awt_headless)\n");
}
