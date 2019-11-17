#include <android/log.h>

#define  LOG_TAG "GraalGluon"

#define  LOGD(ignore, ...)  __android_log_print(ANDROID_LOG_DEBUG, LOG_TAG, __VA_ARGS__)
#define  LOGE(ignore, ...)  __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)
