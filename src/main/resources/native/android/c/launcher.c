#include <jni.h>
#include <stdio.h>
#include <stdlib.h>
#include <unistd.h>
#include <pthread.h>


#include <errno.h>
#include <android/native_window_jni.h>
#include "grandroid.h"

#define  LOG_TAG "GraalGluon"

#define  LOGD(ignore, ...)  __android_log_print(ANDROID_LOG_DEBUG, LOG_TAG, __VA_ARGS__)
#define  LOGE(ignore, ...)  __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

extern int *run_main(int argc, char* argv[]);

extern void requestGlassToRedraw();

ANativeWindow *window;
jfloat density;

int start_logger(const char *app_name);
static int pfd[2];
static pthread_t thr;
static const char *tag = "myapp";
const char * args[] = {
        "myapp",
        "-Djavafx.platform=android",
        "-Dembedded=monocle",
        "-Dglass.platform=Monocle",
        "-Djavafx.verbose=true",
        "-Djavafx.pulseLogger=true",
        "-Dprism.verbose=true",
        "test"
    };

// === called from DALVIK. Minize work/dependencies here === // 

JNIEXPORT void JNICALL Java_com_gluonhq_helloandroid_MainActivity_startGraalApp
(JNIEnv *env, jobject activity) {
    LOGE(stderr, "Start GraalApp, DALVIK env at %p\n", env);
    LOGE(stderr, "PAGESIZE = %ld\n", sysconf(_SC_PAGE_SIZE));
    int ev = (*env)->GetVersion(env);
    LOGE(stderr, "EnvVersion = %d\n", ev);
    start_logger("GraalCompiled");
    LOGE(stderr, "calling JavaMainWrapper_run\n");

    (*run_main)(1, args);
    LOGE(stderr, "called JavaMainWrapper_run\n");
}

JNIEXPORT void JNICALL Java_com_gluonhq_helloandroid_MainActivity_nativeSetSurface
(JNIEnv *env, jobject activity, jobject surface) {
    LOGE(stderr, "nativeSetSurface called, env at %p and size %ld, surface at %p\n", env, sizeof(JNIEnv), surface);
    window = ANativeWindow_fromSurface(env, surface);
    LOGE(stderr, "native setSurface Ready, native window at %p\n", window);
}

JNIEXPORT jlong JNICALL Java_com_gluonhq_helloandroid_MainActivity_surfaceReady
(JNIEnv *env, jobject activity, jobject surface, jfloat mydensity) {
    LOGE(stderr, "SurfaceReady, surface at %p\n", surface);
    window = ANativeWindow_fromSurface(env, surface);
    LOGE(stderr, "SurfaceReady, native window at %p\n", window);
    density = mydensity;
    return (jlong)window;
}

JNIEXPORT void JNICALL Java_com_gluonhq_helloandroid_MainActivity_nativeSurfaceRedrawNeeded
(JNIEnv *env, jobject activity) {
    LOGE(stderr, "launcher, nativeSurfaceRedrawNeeded called. Invoke method on glass_monocle\n");
    requestGlassToRedraw();
}
// == expose window functionality to JavaFX native code == //

ANativeWindow* _GLUON_getNativeWindow() {
    return window;
}

float _GLUON_getDensity() {
    return density;
}

ANativeWindow* getNativeWindow() {
    return window;
}


// ======== missing functions ==== //

int * __errno_location(void) {
    int *a = &errno;
    return a;
}

void getEnviron() {
    LOGE(stderr, "\n\ngetEnviron NYI\n\n");
}

// we need this and the start_logger since android eats fprintf
static void *thread_func()
{
    ssize_t rdsz;
    char buf[128];
    while((rdsz = read(pfd[0], buf, sizeof buf - 1)) > 0) {
        if(buf[rdsz - 1] == '\n') --rdsz;
        buf[rdsz] = 0;  /* add null-terminator */
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
    if(pthread_create(&thr, 0, thread_func, 0) == -1)
        return -1;
    pthread_detach(thr);
    return 0;
}

void determineCPUFeatures() {
fprintf(stderr, "\n\n\ndetermineCpuFeaures\n");
}

/*void Java_sun_nio_fs_LinuxNativeDispatcher_init () {
fprintf(stderr, "\n\n\nLINUXNATIVEDISPATCHER_INIT\n");
}

void  Java_sun_nio_fs_LinuxNativeDispatcher_fgetxattr0() {
fprintf(stderr, "\n\n\nLINUXNATIVEDISPATCHER_GETXATTR\n");
}*/

void Java_jdk_net_LinuxSocketOptions_keepAliveOptionsSupported0() {
fprintf(stderr, "\n\n\nLINUXSOCKETOPTIONS_KEEPALIVESUP0\n");
}

void Java_jdk_net_LinuxSocketOptions_quickAckSupported0() {
fprintf(stderr, "\n\n\nLINUXSOCKETOPTIONS_QUICK0\n");
}


JNIEXPORT jint JNICALL 
JNI_OnLoad_javafx_font(JavaVM *vm, void * reserved) {
fprintf(stderr, "In dummy JNI_OnLoad_javafx_font\n");
#ifdef JNI_VERSION_1_8
    //min. returned JNI_VERSION required by JDK8 for builtin libraries
    JNIEnv *env;
    if ((*vm)->GetEnv(vm, (void **)&env, JNI_VERSION_1_8) != JNI_OK) {
        return JNI_VERSION_1_4;
    }
    return JNI_VERSION_1_8;
#else
    return JNI_VERSION_1_4;
#endif
}

