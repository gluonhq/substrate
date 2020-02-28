#include "grandroid.h"

extern int *run_main(int argc, char *argv[]);

jclass activityClass;
jobject activity;
jmethodID activity_showIME;
jmethodID activity_hideIME;

JavaVM *androidVM;
JNIEnv *androidEnv;
ANativeWindow *window;
jfloat density;
char *appDataDir;

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
    "-Djavafx.verbose=true",
    "-Dmonocle.input.traceEvents.verbose=true",
    "-Dprism.verbose=true"};

int argsize = 8;

char **createArgs()
{
    LOGE(stderr, "CREATE ARGS");
    int origSize = sizeof(origargs) / sizeof(char *);
    char **result = (char **)malloc((origSize + 2) * sizeof(char *));
    for (int i = 0; i < origSize; i++)
    {
        result[i] = (char *)origargs[i];
    }
    int tmpArgSize = 18 + strnlen(appDataDir, 512);
    char *tmpArgs = calloc(sizeof(char), tmpArgSize);
    strcpy(tmpArgs, "-Djava.io.tmpdir=");
    strcat(tmpArgs, appDataDir);
    result[origSize] = tmpArgs;
    argsize++;
    int userArgSize = 13 + strnlen(appDataDir, 512);
    char *userArgs = calloc(sizeof(char), userArgSize);
    strcpy(userArgs, "-Duser.home=");
    strcat(userArgs, appDataDir);
    result[origSize + 1] = userArgs;
    argsize++;
    LOGE(stderr, "CREATE ARGS done");
    return result;
}

void registerMethodHandles(JNIEnv *aenv)
{
    activityClass = (*aenv)->NewGlobalRef(aenv,
                                          (*aenv)->FindClass(aenv, "com/gluonhq/helloandroid/MainActivity"));
    activity_showIME = (*aenv)->GetStaticMethodID(aenv, activityClass, "showIME", "()V");
    activity_hideIME = (*aenv)->GetStaticMethodID(aenv, activityClass, "hideIME", "()V");
}

int JNI_OnLoad(JavaVM *vm, void *reserved)
{
    androidVM = vm;
    (*vm)->GetEnv(vm, (void **)&androidEnv, JNI_VERSION_1_6);
    registerMethodHandles(androidEnv);
    LOGE(stderr, "AndroidVM called into native, vm = %p, androidEnv = %p", androidVM, androidEnv);
    return JNI_VERSION_1_6;
}

// === called from DALVIK. Minize work/dependencies here === //

JNIEXPORT void JNICALL Java_com_gluonhq_helloandroid_MainActivity_startGraalApp(JNIEnv *env, jobject activityObj)
{
    activity = activityObj;
    LOGE(stderr, "Start GraalApp, DALVIK env at %p\n", env);
    LOGE(stderr, "PAGESIZE = %ld\n", sysconf(_SC_PAGE_SIZE));

    int ev = (*env)->GetVersion(env);

    LOGE(stderr, "EnvVersion = %d\n", ev);

    start_logger("GraalCompiled");
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