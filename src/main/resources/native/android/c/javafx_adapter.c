#include "grandroid.h"

JNIEXPORT void JNICALL Java_com_gluonhq_helloandroid_MainActivity_nativeSetSurface(JNIEnv *env, jobject activity, jobject surface)
{
    LOGE(stderr, "nativeSetSurface called, env at %p and size %ld, surface at %p\n", env, sizeof(JNIEnv), surface);
    window = ANativeWindow_fromSurface(env, surface);
    androidJfx_setNativeWindow(window);
    LOGE(stderr, "native setSurface Ready, native window at %p\n", window);
}

JNIEXPORT void JNICALL Java_com_gluonhq_helloandroid_MainActivity_nativeSetDataDir(JNIEnv *env, jobject that, jstring jdir)
{
    const char *cdir = (*env)->GetStringUTFChars(env, jdir, 0);
    int len = strnlen(cdir, 512);
    appDataDir = (char *)malloc(len + 1);
    strcpy(appDataDir, cdir);
    LOGE(stderr, "appDataDir: %s", appDataDir);
}

JNIEXPORT jlong JNICALL Java_com_gluonhq_helloandroid_MainActivity_surfaceReady(JNIEnv *env, jobject activity, jobject surface, jfloat mydensity)
{
    LOGE(stderr, "SurfaceReady, surface at %p\n", surface);
    window = ANativeWindow_fromSurface(env, surface);
    androidJfx_setNativeWindow(window);
    androidJfx_setDensity(mydensity);
    LOGE(stderr, "SurfaceReady, native window at %p\n", window);
    density = mydensity;
    return (jlong)window;
}

JNIEXPORT void JNICALL Java_com_gluonhq_helloandroid_MainActivity_nativeSurfaceRedrawNeeded(JNIEnv *env, jobject activity)
{
    LOGE(stderr, "launcher, nativeSurfaceRedrawNeeded called. Invoke method on glass_monocle\n");
    androidJfx_requestGlassToRedraw();
}

JNIEXPORT jint JNICALL
JNI_OnLoad_javafx_font(JavaVM *vm, void *reserved)
{
    LOGE(stderr, "In dummy JNI_OnLoad_javafx_font\n");
#ifdef JNI_VERSION_1_8
    //min. returned JNI_VERSION required by JDK8 for builtin libraries
    JNIEnv *env;
    if ((*vm)->GetEnv(vm, (void **)&env, JNI_VERSION_1_8) != JNI_OK)
    {
        return JNI_VERSION_1_4;
    }
    return JNI_VERSION_1_8;
#else
    return JNI_VERSION_1_4;
#endif
}

void showSoftwareKeyboard()
{
    JNIEnv *menv;
    (*androidVM)->AttachCurrentThread(androidVM, (JNIEnv **)&menv, NULL);
    LOGE(stderr, "now I have to show keyboard, invoke method %p on env %p (old = %p)\n", activity_showIME, menv, androidEnv);
    (*menv)->CallStaticVoidMethod(menv, activityClass, activity_showIME);
    (*androidVM)->DetachCurrentThread(androidVM);
    LOGE(stderr, "I did show keyboard\n");
}

JNIEXPORT void JNICALL
Java_javafx_scene_control_skin_TextAreaSkinAndroid_showSoftwareKeyboard(JNIEnv *env, jobject textareaskin)
{
    showSoftwareKeyboard();
}

JNIEXPORT void JNICALL
Java_javafx_scene_control_skin_TextFieldSkinAndroid_showSoftwareKeyboard(JNIEnv *env, jobject textfieldskin)
{
    showSoftwareKeyboard();
}

void hideSoftwareKeyboard()
{
    JNIEnv *menv;
    (*androidVM)->AttachCurrentThread(androidVM, (JNIEnv **)&menv, NULL);
    LOGE(stderr, "now I have to hide keyboard, invoke method %p on env %p (old = %p)\n", activity_hideIME, menv, androidEnv);
    (*menv)->CallStaticVoidMethod(menv, activityClass, activity_hideIME);
    (*androidVM)->DetachCurrentThread(androidVM);
    LOGE(stderr, "I did hide keyboard\n");
}

JNIEXPORT void JNICALL
Java_javafx_scene_control_skin_TextFieldSkinAndroid_hideSoftwareKeyboard(JNIEnv *env, jobject textfieldskin)
{
    hideSoftwareKeyboard();
}

JNIEXPORT void JNICALL
Java_javafx_scene_control_skin_TextAreaSkinAndroid_hideSoftwareKeyboard(JNIEnv *env, jobject textareaskin)
{
    hideSoftwareKeyboard();
}