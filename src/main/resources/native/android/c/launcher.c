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
#include <stdlib.h>
#include <string.h>
#include <errno.h>
#include "grandroid.h"

extern int *run_main(int argc, char *argv[]);

jclass activityClass;
jclass permissionActivityClass;
jobject activity;
jmethodID activity_showIME;
jmethodID activity_hideIME;

JavaVM *androidVM;
JNIEnv *androidEnv;
ANativeWindow *window;
jfloat density;
char *appDataDir;
char *timeZone;

int start_logger(const char *app_name);

// TODO: remove once https://github.com/oracle/graal/issues/2713 is fixed
int JNI_OnLoad_sunec(JavaVM *vm, void *reserved);

const char *origargs[] = {
    "myapp",
    "-Djavafx.platform=android",
    "-Dmonocle.platform=Android", // used in com.sun.glass.ui.monocle.NativePlatformFactory
    "-Dembedded=monocle",
    "-Dglass.platform=Monocle",
    "-Dcom.sun.javafx.isEmbedded=true",
    "-Dcom.sun.javafx.touch=true",
    "-Dcom.sun.javafx.gestures.zoom=true",
    "-Dcom.sun.javafx.gestures.rotate=true",
    "-Dcom.sun.javafx.gestures.scroll=true",
    "-Djavafx.verbose=true",
    "-Dmonocle.input.traceEvents.verbose=true",
    "-Dprism.verbose=true",
    "-Xmx4g"};

int argsize;

char **createArgs()
{
    LOGE(stderr, "createArgs for run_main");
    argsize = sizeof(origargs) / sizeof(char *);
    char **result = (char **)malloc((argsize + 2) * sizeof(char *));
    for (int i = 0; i < argsize; i++)
    {
        result[i] = (char *)origargs[i];
    }

    // user time zone
    int timeArgSize = 17 + strnlen(timeZone, 512);
    char *timeArgs = (char *)calloc(sizeof(char), timeArgSize);
    strcpy(timeArgs, "-Duser.timezone=");
    strcat(timeArgs, timeZone);
    result[argsize++] = timeArgs;

    // tmp dir
    int tmpArgSize = 18 + strnlen(appDataDir, 512);
    char *tmpArgs = (char *)calloc(sizeof(char), tmpArgSize);
    strcpy(tmpArgs, "-Djava.io.tmpdir=");
    strcat(tmpArgs, appDataDir);
    result[argsize++] = tmpArgs;

    // user home
    int userArgSize = 13 + strnlen(appDataDir, 512);
    char *userArgs = (char *)calloc(sizeof(char), userArgSize);
    strcpy(userArgs, "-Duser.home=");
    strcat(userArgs, appDataDir);
    result[argsize++] = userArgs;

    LOGE(stderr, "CREATE ARGS done");
    return result;
}

void registerMethodHandles(JNIEnv *aenv)
{
    activityClass = (*aenv)->NewGlobalRef(aenv, (*aenv)->FindClass(aenv, "com/gluonhq/helloandroid/MainActivity"));
    permissionActivityClass = (*aenv)->NewGlobalRef(aenv, (*aenv)->FindClass(aenv, "com/gluonhq/helloandroid/PermissionRequestActivity"));
    activity_showIME = (*aenv)->GetStaticMethodID(aenv, activityClass, "showIME", "()V");
    activity_hideIME = (*aenv)->GetStaticMethodID(aenv, activityClass, "hideIME", "()V");
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

JNIEXPORT void JNICALL Java_com_gluonhq_helloandroid_MainActivity_startGraalApp(JNIEnv *env, jobject activityObj)
{
    activity = (*env)->NewGlobalRef(env, activityObj);
    LOGE(stderr, "Start GraalApp, DALVIK env at %p\n", env);
    LOGE(stderr, "PAGESIZE = %ld\n", sysconf(_SC_PAGE_SIZE));
    LOGE(stderr, "EnvVersion = %d\n", (*env)->GetVersion(env));

    char **graalArgs = createArgs();
    
    LOGE(stderr, "calling JavaMainWrapper_run with %d argsize\n", argsize);
    
    (*run_main)(argsize, graalArgs);

    LOGE(stderr, "called JavaMainWrapper_run\n");

    // Invoke sunec
    JNI_OnLoad_sunec(NULL, NULL);
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

void determineCPUFeatures()
{
    LOGE(stderr,  "\n\n\ndetermineCpuFeaures\n");
}

void JVM_NativePath() {
    fprintf(stderr, "We should never reach here (JVM_nativePath)\n");
}

void JVM_RawMonitorCreate() {
    fprintf(stderr, "We should never reach here (JVM_RawMonitorCreate)\n");
}

void JVM_RawMonitorDestroy() {
    fprintf(stderr, "We should never reach here (JVM_RawMonitorDestroy)\n");
}

void JVM_RawMonitorEnter() {
    fprintf(stderr, "We should never reach here (JVM_RawMonitorEnter)\n");
}

void JVM_RawMonitorExit() {
    fprintf(stderr, "We should never reach here (JVM_RawMonitorExit)\n");
}
