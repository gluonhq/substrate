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

extern void androidJfx_requestGlassToRedraw();
extern void androidJfx_setNativeWindow(ANativeWindow* nativeWindow);
extern void androidJfx_setDensity(float nativeDensity);
extern void androidJfx_gotTouchEvent (int count, int* actions, int* ids, int* xs, int* ys, int primary);
extern int to_jfx_touch_action(int state);

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
        "-Dprism.verbose=true"};

// === called from DALVIK. Minize work/dependencies here === // 

JNIEXPORT void JNICALL Java_com_gluonhq_helloandroid_MainActivity_startGraalApp
(JNIEnv *env, jobject activity) {
    LOGE(stderr, "Start GraalApp, DALVIK env at %p\n", env);
    LOGE(stderr, "PAGESIZE = %ld\n", sysconf(_SC_PAGE_SIZE));
    int ev = (*env)->GetVersion(env);
    LOGE(stderr, "EnvVersion = %d\n", ev);
    start_logger("GraalCompiled");
    LOGE(stderr, "calling JavaMainWrapper_run\n");
    (*run_main)(7, args);
    LOGE(stderr, "called JavaMainWrapper_run\n");
}

JNIEXPORT void JNICALL Java_com_gluonhq_helloandroid_MainActivity_nativeSetSurface
(JNIEnv *env, jobject activity, jobject surface) {
    LOGE(stderr, "nativeSetSurface called, env at %p and size %ld, surface at %p\n", env, sizeof(JNIEnv), surface);
    window = ANativeWindow_fromSurface(env, surface);
    androidJfx_setNativeWindow(window);
    LOGE(stderr, "native setSurface Ready, native window at %p\n", window);
}

JNIEXPORT jlong JNICALL Java_com_gluonhq_helloandroid_MainActivity_surfaceReady
(JNIEnv *env, jobject activity, jobject surface, jfloat mydensity) {
    LOGE(stderr, "SurfaceReady, surface at %p\n", surface);
    window = ANativeWindow_fromSurface(env, surface);
    androidJfx_setNativeWindow(window);
    androidJfx_setDensity(mydensity);
    LOGE(stderr, "SurfaceReady, native window at %p\n", window);
    density = mydensity;
    return (jlong)window;
}

JNIEXPORT void JNICALL Java_com_gluonhq_helloandroid_MainActivity_nativeSurfaceRedrawNeeded
(JNIEnv *env, jobject activity) {
    LOGE(stderr, "launcher, nativeSurfaceRedrawNeeded called. Invoke method on glass_monocle\n");
    androidJfx_requestGlassToRedraw();
}


JNIEXPORT void JNICALL Java_com_gluonhq_helloandroid_MainActivity_nativeGotTouchEvent
(JNIEnv *env, jobject activity, jint jcount, jintArray jactions, jintArray jids, jintArray jxs, jintArray jys) {
    LOGE(stderr, "Native Dalvik layer got touch event, pass to native Graal layer...");

    jlong jlongids[jcount];

    int *actions = (*env)->GetIntArrayElements(env, jactions, 0);
    int *ids = (*env)->GetIntArrayElements(env, jids, 0);
    int *xs = (*env)->GetIntArrayElements(env, jxs, 0);
    int *ys = (*env)->GetIntArrayElements(env, jys, 0);
    int primary = 0;
    for(int i=0;i<jcount;i++) {
        actions[i] = to_jfx_touch_action(actions[i]);
        jlongids[i] = (jlong)ids[i];
        if (actions[i] != com_sun_glass_events_TouchEvent_TOUCH_STILL) {
            primary = actions[i] == com_sun_glass_events_TouchEvent_TOUCH_RELEASED && jcount == 1 ? -1 : i; 
        }
    }
    androidJfx_gotTouchEvent(jcount, actions, ids, xs, ys, primary);

    (*env)->ReleaseIntArrayElements(env, jactions, actions, 0);
    (*env)->ReleaseIntArrayElements(env, jids, ids, 0);
    (*env)->ReleaseIntArrayElements(env, jxs, xs, 0);
    (*env)->ReleaseIntArrayElements(env, jys, ys, 0);

    LOGE(stderr, "Native Dalvik layer got touch event, passed to native Graal layer...");
}

JNIEXPORT void JNICALL Java_com_gluonhq_helloandroid_MainActivity_nativeGotKeyEvent
(JNIEnv *env, jobject activity, jint action, jint keyCode) {
    LOGE(stderr, "Native Dalvik layer got key event, pass to native Graal layer...");
    // Java_com_sun_glass_ui_android_DalvikInput_onKeyEventNative(NULL, NULL, action, keyCode);
    LOGE(stderr, "Native Dalvik layer got key event, TODO!!!");
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

