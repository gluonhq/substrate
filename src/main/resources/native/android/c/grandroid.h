#include <stdio.h>
#include <stdlib.h>
#include <string.h>

#include <errno.h>
#include <unistd.h>
#include <pthread.h>

#include <jni.h>

#include <android/log.h>
#include <android/native_window_jni.h>

#define  ENABLE_DEBUG_LOG 0
#define  LOG_TAG "GraalGluon"

#if ENABLE_DEBUG_LOG == 1
    #define  LOGD(ignore, ...)  __android_log_print(ANDROID_LOG_DEBUG, LOG_TAG, __VA_ARGS__) 
    #define  LOGE(ignore, ...)  __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)
#else
    #define  LOGD(ignore, ...)
    #define  LOGE(ignore, ...)
#endif

#undef com_sun_glass_events_TouchEvent_TOUCH_PRESSED
#define com_sun_glass_events_TouchEvent_TOUCH_PRESSED 811L
#undef com_sun_glass_events_TouchEvent_TOUCH_MOVED
#define com_sun_glass_events_TouchEvent_TOUCH_MOVED 812L
#undef com_sun_glass_events_TouchEvent_TOUCH_RELEASED
#define com_sun_glass_events_TouchEvent_TOUCH_RELEASED 813L
#undef com_sun_glass_events_TouchEvent_TOUCH_STILL
#define com_sun_glass_events_TouchEvent_TOUCH_STILL 814L

extern jclass activityClass;
extern jobject activity;
extern jmethodID activity_showIME;
extern jmethodID activity_hideIME;

extern JavaVM *androidVM;
extern JNIEnv *androidEnv;
extern ANativeWindow *window;
extern jfloat density;
extern char *appDataDir;

void __attribute__((weak)) androidJfx_requestGlassToRedraw() {}
void __attribute__((weak)) androidJfx_setNativeWindow(ANativeWindow *nativeWindow) {}
void __attribute__((weak)) androidJfx_setDensity(float nativeDensity) {}
void __attribute__((weak)) androidJfx_gotTouchEvent(int count, int *actions, int *ids, int *xs, int *ys, int primary) {}
void __attribute__((weak)) androidJfx_gotKeyEvent(int action, int key, jchar *chars, int count, int mods) {}
int  __attribute__((weak)) to_jfx_touch_action(int state) { return 0; }