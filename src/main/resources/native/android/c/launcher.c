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
static int pfd[2];
static pthread_t thr;
static const char *tag = "myapp";
const char *origargs[] = {
    "myapp",
    "-Djavafx.platform=android",
    "-Dmonocle.platform=Android", // used in com.sun.glass.ui.monocle.NativePlatformFactory
    "-Dembedded=monocle",
    "-Dglass.platform=Monocle",
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
    registerAttachMethodHandles(androidEnv);
    LOGE(stderr, "Attach method handles registered.");
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

// we need this and the start_logger since android eats fprintf
static void *thread_func()
{
    ssize_t rdsz;
    char buf[128];
    while ((rdsz = read(pfd[0], buf, sizeof buf - 1)) > 0)
    {
        if (buf[rdsz - 1] == '\n')
            --rdsz;
        buf[rdsz] = 0; /* add null-terminator */
        __android_log_write(ANDROID_LOG_DEBUG, tag, buf);
        // __android_log_print(ANDROID_LOG_DEBUG, tag, buf);
    }
    return 0;
}

int start_logger(const char *app_name)
{
    tag = app_name;

    /* make stdout line-buffered and stderr unbuffered */
    setvbuf(stdout, 0, _IOLBF, 0);
    setvbuf(stderr, 0, _IONBF, 0);

    /* create the pipe and redirect stdout and stderr */
    pipe(pfd);
    dup2(pfd[1], 1);
    dup2(pfd[1], 2);

    /* spawn the logging thread */
    if (pthread_create(&thr, 0, thread_func, 0) == -1)
        return -1;
    pthread_detach(thr);
    return 0;
}

void determineCPUFeatures()
{
    fprintf(stderr, "\n\n\ndetermineCpuFeaures\n");
}

JNIEXPORT jint JNICALL JNI_OnLoad_extnet(JavaVM *vm, void *reserved) {
    fprintf(stderr, "libextnet.a loaded\n");
    return JNI_VERSION_1_6;
}

