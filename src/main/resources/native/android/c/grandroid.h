#include <android/log.h>

#define  LOG_TAG "GraalGluon"

#define  LOGD(ignore, ...)  __android_log_print(ANDROID_LOG_DEBUG, LOG_TAG, __VA_ARGS__)
#define  LOGE(ignore, ...)  __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)
#undef com_sun_glass_events_TouchEvent_TOUCH_PRESSED
#define com_sun_glass_events_TouchEvent_TOUCH_PRESSED 811L
#undef com_sun_glass_events_TouchEvent_TOUCH_MOVED
#define com_sun_glass_events_TouchEvent_TOUCH_MOVED 812L
#undef com_sun_glass_events_TouchEvent_TOUCH_RELEASED
#define com_sun_glass_events_TouchEvent_TOUCH_RELEASED 813L
#undef com_sun_glass_events_TouchEvent_TOUCH_STILL
#define com_sun_glass_events_TouchEvent_TOUCH_STILL 814L
