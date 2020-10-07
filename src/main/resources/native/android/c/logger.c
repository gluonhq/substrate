#include <jni.h>
#include <pthread.h>
#include <unistd.h>
#include <stdlib.h>
#include <android/log.h>
#include <android/native_window_jni.h>

static int pfd[2];
static pthread_t thr;
static const char * tag;
FILE * andr_out;

// we need this and the start_logger since android eats fprintf
static void *thread_func()
{
    ssize_t rdsz;
    char buf[1024];
    while (fgets(buf, sizeof buf, andr_out)) {
        __android_log_write(ANDROID_LOG_DEBUG, tag, buf);
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

    andr_out = fdopen(pfd[0], "rb");
    /* spawn the logging thread */
    if (pthread_create(&thr, 0, thread_func, 0) == -1)
        return -1;
    pthread_detach(thr);
    return 0;
}