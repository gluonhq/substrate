#include "grandroid.h"

JNIEXPORT void JNICALL
Java_javafx_scene_control_skin_TextFieldSkinAndroid_showSoftwareKeyboard();

JNIEXPORT void JNICALL Java_com_gluonhq_helloandroid_MainActivity_nativeGotTouchEvent(JNIEnv *env, jobject activity, jint jcount, jintArray jactions, jintArray jids, jintArray jxs, jintArray jys)
{
    LOGE(stderr, "Native Dalvik layer got touch event, pass to native Graal layer...");

    jlong jlongids[jcount];

    int *actions = (*env)->GetIntArrayElements(env, jactions, 0);
    int *ids = (*env)->GetIntArrayElements(env, jids, 0);
    int *xs = (*env)->GetIntArrayElements(env, jxs, 0);
    int *ys = (*env)->GetIntArrayElements(env, jys, 0);
    int primary = 0;
    for (int i = 0; i < jcount; i++)
    {
        actions[i] = to_jfx_touch_action(actions[i]);
        jlongids[i] = (jlong)ids[i];
        if (actions[i] != com_sun_glass_events_TouchEvent_TOUCH_STILL)
        {
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

JNIEXPORT void JNICALL Java_com_gluonhq_helloandroid_MainActivity_nativedispatchKeyEvent(JNIEnv *env, jobject activity, jint action, jint keyCode, jcharArray jchars, jint cc, jint modifiers)
{
    LOGE(stderr, "Native Dalvik layer has to dispatch key event, pass to native Graal layer with %d chars...", cc);
    jchar *kars = (*env)->GetCharArrayElements(env, jchars, 0);
    LOGE(stderr, "passed count = %d and realcount = %d\n", cc, (*env)->GetArrayLength(env, jchars));
    LOGE(stderr, "c0 = %c and c1 = %c\n", kars[0], kars[1]);
    LOGE(stderr, "c0 = %x and c1 = %x\n", kars[0], kars[1]);
    androidJfx_gotKeyEvent(action, keyCode, kars, cc, modifiers);
}

JNIEXPORT void JNICALL Java_com_gluonhq_helloandroid_MainActivity_nativeGotKeyEvent(JNIEnv *env, jobject activity, jint action, jint keyCode)
{
    LOGE(stderr, "Native Dalvik layer got key event, pass to native Graal layer...");
    // androidJfx_gotKeyEvent(action, keyCode);
    // Java_com_sun_glass_ui_android_DalvikInput_onKeyEventNative(NULL, NULL, action, keyCode);
    LOGE(stderr, "Native Dalvik layer got key event!!!");
}