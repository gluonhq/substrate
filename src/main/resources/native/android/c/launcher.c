#include <jni.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <unistd.h>
#include <pthread.h>


#include <errno.h>
#include <android/native_window_jni.h>
#include "grandroid.h"

#define  LOG_TAG "GraalGluon"

#define  LOGD(ignore, ...)  __android_log_print(ANDROID_LOG_DEBUG, LOG_TAG, __VA_ARGS__)
#define  LOGE(ignore, ...)  __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

extern int *run_main(int argc, char* argv[]);

void __attribute__((weak)) androidJfx_requestGlassToRedraw() {}
void __attribute__((weak)) androidJfx_setNativeWindow(ANativeWindow* nativeWindow) {}
void __attribute__((weak)) androidJfx_setDensity(float nativeDensity) {}
void __attribute__((weak)) androidJfx_gotTouchEvent (int count, int* actions, int* ids, int* xs, int* ys, int primary) {}
void __attribute__((weak)) androidJfx_gotKeyEvent (int action, int key, jchar* chars, int count, int mods) {}
int  __attribute__((weak)) to_jfx_touch_action(int state) { return 0; }

jclass activityClass;
jobject activity;
jmethodID activity_showIME;
jmethodID activity_hideIME;


JavaVM *androidVM;
JNIEnv* androidEnv;
ANativeWindow *window;
jfloat density;
char* appDataDir;

int start_logger(const char *app_name);
static int pfd[2];
static pthread_t thr;
static const char *tag = "myapp";
const char * origargs[] = {
        "myapp",
        "-Djavafx.platform=android",
        "-Dmonocle.platform=Android", // used in com.sun.glass.ui.monocle.NativePlatformFactory
        "-Dembedded=monocle",
        "-Dglass.platform=Monocle",
        "-Djavafx.verbose=true",
        "-Dmonocle.input.traceEvents.verbose=true",
        "-Dprism.verbose=true"};

int argsize = 8;

char** createArgs() {
LOGE(stderr, "CREATE ARGS");
    int origSize = sizeof(origargs)/sizeof(char*);
    char** result = malloc((origSize+1)* sizeof(char*));
    for (int i = 0; i < origSize; i++) {
        result[i] = origargs[i];
    }
    int tmpArgSize=18+strnlen(appDataDir, 512);
    char* tmpArgs = calloc(sizeof(char), tmpArgSize);
    strcpy(tmpArgs,"-Djava.io.tmpdir=");
    strcat(tmpArgs,appDataDir);
    result[origSize]=tmpArgs;
    argsize++;
LOGE(stderr, "CREATE ARGS done");
    return result;
}

void registerMethodHandles (JNIEnv *aenv) {
    activityClass = (*aenv)->NewGlobalRef(aenv, 
          (*aenv)->FindClass(aenv, "com/gluonhq/helloandroid/MainActivity"));
    activity_showIME = (*aenv)->GetStaticMethodID(aenv, activityClass, "showIME", "()V");
    activity_hideIME = (*aenv)->GetStaticMethodID(aenv, activityClass, "hideIME", "()V");
}

int JNI_OnLoad(JavaVM *vm, void *reserved) {
    androidVM = vm;
    (*vm)->GetEnv(vm, (void **) &androidEnv, JNI_VERSION_1_6);
    registerMethodHandles(androidEnv);
    LOGE(stderr, "AndroidVM called into native, vm = %p, androidEnv = %p",androidVM, androidEnv);
    return JNI_VERSION_1_6;
}

// === called from DALVIK. Minize work/dependencies here === // 

JNIEXPORT void JNICALL Java_com_gluonhq_helloandroid_MainActivity_startGraalApp
(JNIEnv *env, jobject activityObj) {
    activity = activityObj;
    LOGE(stderr, "Start GraalApp, DALVIK env at %p\n", env);
    LOGE(stderr, "PAGESIZE = %ld\n", sysconf(_SC_PAGE_SIZE));
    int ev = (*env)->GetVersion(env);
    LOGE(stderr, "EnvVersion = %d\n", ev);
    start_logger("GraalCompiled");
    char** graalArgs = createArgs();
    LOGE(stderr, "calling JavaMainWrapper_run with %d argsize\n", argsize);
    (*run_main)(argsize, graalArgs);
    // (*run_main)(7, origargs);
    LOGE(stderr, "called JavaMainWrapper_run\n");
}


JNIEXPORT void JNICALL Java_com_gluonhq_helloandroid_MainActivity_nativeSetSurface
(JNIEnv *env, jobject activity, jobject surface) {
    LOGE(stderr, "nativeSetSurface called, env at %p and size %ld, surface at %p\n", env, sizeof(JNIEnv), surface);
    window = ANativeWindow_fromSurface(env, surface);
    androidJfx_setNativeWindow(window);
    LOGE(stderr, "native setSurface Ready, native window at %p\n", window);
}

JNIEXPORT void JNICALL Java_com_gluonhq_helloandroid_MainActivity_nativeSetDataDir
(JNIEnv *env, jobject that, jstring jdir) {
    const char *cdir = (*env)->GetStringUTFChars(env, jdir, 0);
    int len = strnlen(cdir, 512);
    appDataDir = (char *)malloc(len + 1);
    strcpy(appDataDir, cdir);
    LOGE(stderr, "appDataDir: %s", appDataDir);
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

JNIEXPORT void JNICALL 
Java_javafx_scene_control_skin_TextFieldSkinAndroid_showSoftwareKeyboard();

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

JNIEXPORT void JNICALL Java_com_gluonhq_helloandroid_MainActivity_nativedispatchKeyEvent
(JNIEnv *env, jobject activity, jint action, jint keyCode, jcharArray jchars, jint cc, jint modifiers) {
    LOGE(stderr, "Native Dalvik layer has to dispatch key event, pass to native Graal layer with %d chars...", cc);
    jchar *kars = (*env)->GetCharArrayElements(env, jchars, 0);
int realcount = (*env)->GetArrayLength(env, jchars);
LOGE(stderr, "passed count = %d and realcount = %d\n", cc, realcount);
LOGE(stderr, "c0 = %c and c1 = %c\n", kars[0], kars[1]);
LOGE(stderr, "c0 = %x and c1 = %x\n", kars[0], kars[1]);
    androidJfx_gotKeyEvent(action, keyCode, kars, cc, modifiers);
}

JNIEXPORT void JNICALL Java_com_gluonhq_helloandroid_MainActivity_nativeGotKeyEvent
(JNIEnv *env, jobject activity, jint action, jint keyCode) {
    LOGE(stderr, "Native Dalvik layer got key event, pass to native Graal layer...");
    // androidJfx_gotKeyEvent(action, keyCode);
    // Java_com_sun_glass_ui_android_DalvikInput_onKeyEventNative(NULL, NULL, action, keyCode);
    LOGE(stderr, "Native Dalvik layer got key event!!!");
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

void showSoftwareKeyboard() {
    JNIEnv *menv;
    (*androidVM)->AttachCurrentThread(androidVM, (JNIEnv **) &menv, NULL);
    LOGE(stderr, "now I have to show keyboard, invoke method %p on env %p (old = %p)\n", activity_showIME, menv, androidEnv); 
    (*menv)->CallStaticVoidMethod(menv, activityClass, activity_showIME);
    (*androidVM)->DetachCurrentThread(androidVM);
    LOGE(stderr, "I did show keyboard\n"); 
}

JNIEXPORT void JNICALL 
Java_javafx_scene_control_skin_TextAreaSkinAndroid_showSoftwareKeyboard
(JNIEnv *env, jobject textareaskin) {
    showSoftwareKeyboard();
}

JNIEXPORT void JNICALL 
Java_javafx_scene_control_skin_TextFieldSkinAndroid_showSoftwareKeyboard
(JNIEnv *env, jobject textfieldskin) {
    showSoftwareKeyboard();
}

void hideSoftwareKeyboard() {
    JNIEnv *menv;
    (*androidVM)->AttachCurrentThread(androidVM, (JNIEnv **) &menv, NULL);
    LOGE(stderr, "now I have to hide keyboard, invoke method %p on env %p (old = %p)\n", activity_hideIME, menv, androidEnv); 
    (*menv)->CallStaticVoidMethod(menv, activityClass, activity_hideIME);
    (*androidVM)->DetachCurrentThread(androidVM);
    LOGE(stderr, "I did hide keyboard\n"); 
}

JNIEXPORT void JNICALL 
Java_javafx_scene_control_skin_TextFieldSkinAndroid_hideSoftwareKeyboard
(JNIEnv *env, jobject textfieldskin) {
    hideSoftwareKeyboard();
}

JNIEXPORT void JNICALL 
Java_javafx_scene_control_skin_TextAreaSkinAndroid_hideSoftwareKeyboard
(JNIEnv *env, jobject textareaskin) {
    hideSoftwareKeyboard();
}


