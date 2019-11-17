/*
 * Copyright (c) 2018, 2019, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation. Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */

/* JVM_ functions imported from the hotspot sources */

#include <stdio.h>
#include <stdarg.h>
#include <stdint.h>
#include <unistd.h>
#include <sys/socket.h>
#include <sys/poll.h>
#include <netdb.h>
#include <errno.h>
#include <dlfcn.h>
#include <string.h>
#include <sys/ioctl.h>
#include <sys/time.h>
#include <stdlib.h>

#include <jni.h>

#define OS_OK 0
#define OS_ERR -1

/* macros for restartable system calls */

#define RESTARTABLE(_cmd, _result) do { \
    _result = _cmd; \
  } while(((int)_result == OS_ERR) && (errno == EINTR))

#define RESTARTABLE_RETURN_INT(_cmd) do { \
  int _result; \
  RESTARTABLE(_cmd, _result); \
  return _result; \
} while(0)

JNIEXPORT void initialize() {
}

/* Only called in java.lang.Runtime native methods. */
JNIEXPORT void JVM_FreeMemory() {
    printf("JVM_FreeMemory called:  Unimplemented\n");
}

JNIEXPORT jlong JVM_TotalMemory() {
    printf("JVM_TotalMemory called:  Unimplemented\n");
    return 0L;
}

JNIEXPORT jlong JVM_MaxMemory() {
    printf("JVM_MaxMemory called:  Unimplemented\n");
    return 0L;
}

JNIEXPORT void JVM_GC() {
    printf("JVM_GC called:  Unimplemented\n");
}

JNIEXPORT void JVM_TraceInstructions(int on) {
    printf("JVM_TraceInstructions called:  Unimplemented\n");
}

JNIEXPORT void JVM_TraceMethodCalls(int on) {
    printf("JVM_TraceMethods called:  Unimplemented\n");
}

JNIEXPORT int JVM_ActiveProcessorCount() {
    return sysconf(_SC_NPROCESSORS_ONLN);
}

JNIEXPORT int JVM_Connect(int fd, struct sockaddr* him, socklen_t len) {
    RESTARTABLE_RETURN_INT(connect(fd, him, len));
}

JNIEXPORT void * JVM_FindLibraryEntry(void* handle, const char* name) {
    return dlsym(handle, name);
}

JNIEXPORT int JVM_GetHostName(char* name, int namelen) {
    return gethostname(name, namelen);
}

JNIEXPORT int JVM_GetSockOpt(int fd, int level, int optname,
                            char *optval, socklen_t* optlen) {
    return getsockopt(fd, level, optname, optval, optlen);
}

JNIEXPORT int JVM_Socket(int domain, int type, int protocol) {
    return socket(domain, type, protocol);
}

JNIEXPORT int JVM_GetSockName(int fd, struct sockaddr* him, socklen_t* len) {
    return getsockname(fd, him, len);
}

JNIEXPORT int JVM_Listen(int fd, int count) {
    return listen(fd, count);
}

JNIEXPORT int JVM_Send(int fd, char* buf, size_t nBytes, uint flags) {
    RESTARTABLE_RETURN_INT(send(fd, buf, nBytes, flags));
}

JNIEXPORT int JVM_SetSockOpt(int fd, int level, int optname,
                            const char* optval, socklen_t optlen) {
    return setsockopt(fd, level, optname, optval, optlen);
}

JNIEXPORT int JVM_SocketAvailable(int fd, int *pbytes) {
    int ret;

    if (fd < 0)
        return OS_OK;

    RESTARTABLE(ioctl(fd, FIONREAD, pbytes), ret);

    return (ret == OS_ERR) ? 0 : 1;
}

JNIEXPORT int JVM_SocketClose(int fd) {
    return close(fd);
}

JNIEXPORT int JVM_SocketShutdown(int fd, int howto) {
    return shutdown(fd, howto);
}

/* Called directly from several native functions */
JNIEXPORT int JVM_InitializeSocketLibrary() {
    /* A noop, returns 0 in hotspot */
   return 0;
}

JNIEXPORT jlong Java_java_lang_System_currentTimeMillis(void *env, void * ignored) {
    struct timeval time;
    int status = gettimeofday(&time, NULL);
    return (jlong)(time.tv_sec * 1000)  +  (jlong)(time.tv_usec / 1000);
}

JNIEXPORT jlong Java_java_lang_System_nanoTime(void *env, void * ignored) {
    // get implementation from hotspot/os/bsd/os_bsd.cpp
    // for now, just return 1000 * microseconds
    struct timeval time;
    int status = gettimeofday(&time, NULL);
    return (jlong)(time.tv_sec * 1000000000)  +  (jlong)(time.tv_usec * 1000);
}

JNIEXPORT jlong JVM_CurrentTimeMillis(void *env, void * ignored) {
    return Java_java_lang_System_currentTimeMillis(env, ignored);
}

JNIEXPORT jlong JVM_NanoTime(void *env, void * ignored) {
    return Java_java_lang_System_nanoTime(env, ignored);
}

JNIEXPORT jlong JVM_GetNanoTimeAdjustment(void *env, void * ignored, jlong offset_secs) {
    long maxDiffSecs = 0x0100000000L;
    long minDiffSecs = -maxDiffSecs;
    struct timeval time;
    int status = gettimeofday(&time, NULL);

    long seconds = time.tv_sec;
    long nanos = time.tv_usec * 1000;

    long diff = seconds - offset_secs;
    if (diff >= maxDiffSecs || diff <= minDiffSecs) {
        return -1;
    }
    return diff * 1000000000 + nanos;
}

JNIEXPORT jlong Java_jdk_internal_misc_VM_getNanoTimeAdjustment(void *env, void * ignored, jlong offset_secs) {
    return JVM_GetNanoTimeAdjustment(env, ignored, offset_secs);
}

JNIEXPORT void JVM_Halt(int retcode) {
    exit(retcode);
}

JNIEXPORT void JVM_BeforeHalt() {
}

JNIEXPORT int JVM_GetLastErrorString(char *buf, int len) {
    const char *s;
    size_t n;

    if (errno == 0) {
        return 0;
    }

    s = strerror(errno);
    n = strlen(s);
    if (n >= len) {
        n = len - 1;
    }

    strncpy(buf, s, n);
    buf[n] = '\0';
    return n;
}

int jio_vfprintf(FILE* f, const char *fmt, va_list args) {
  return vfprintf(f, fmt, args);
}

JNIEXPORT jobject JNICALL
JVM_DoPrivileged(JNIEnv *env, jclass cls, jobject action, jobject context, jboolean wrapException) {
    jclass actionClass = (*env)->FindClass(env, "java/security/PrivilegedAction");
    if (actionClass != NULL && !(*env)->ExceptionCheck(env)) {
        jmethodID run = (*env)->GetMethodID(env, actionClass, "run", "()Ljava/lang/Object;");
        if (run != NULL && !(*env)->ExceptionCheck(env)) {
            return (*env)->CallObjectMethod(env, action, run);
        }
    }
    jclass errorClass = (*env)->FindClass(env, "java/lang/InternalError");
    if (errorClass != NULL && !(*env)->ExceptionCheck(env)) {
        (*env)->ThrowNew(env, errorClass, "Could not invoke PrivilegedAction");
    } else {
        (*env)->FatalError(env, "PrivilegedAction could not be invoked and the error could not be reported");
    }
    return NULL;
}

JNIEXPORT jobject JNICALL
JVM_GetInheritedAccessControlContext(JNIEnv *env, jclass cls) {
    fprintf(stderr, "JVM_GetInheritedAccessControlContext called:  Unimplemented\n");
    return NULL;
}

JNIEXPORT jobject JNICALL
JVM_GetStackAccessControlContext(JNIEnv *env, jclass cls) {
    fprintf(stderr, "JVM_GetStackAccessControlContext called:  Unimplemented\n");
    return NULL;
}

#ifdef JNI_VERSION_9
JNIEXPORT void JVM_AddModuleExports(JNIEnv *env, jobject from_module, const char* package, jobject to_module) {
    fprintf(stderr, "JVM_AddModuleExports called\n");
}

JNIEXPORT void JVM_AddModuleExportsToAllUnnamed(JNIEnv *env, jobject from_module, const char* package) {
    fprintf(stderr, "JVM_AddModuleExportsToAllUnnamed called\n");
}

JNIEXPORT void JVM_AddModuleExportsToAll(JNIEnv *env, jobject from_module, const char* package) {
    fprintf(stderr, "JVM_AddModuleExportsToAll called\n");
}

JNIEXPORT void JVM_AddReadsModule(JNIEnv *env, jobject from_module, jobject source_module) {
    fprintf(stderr, "JVM_AddReadsModule called\n");
}

JNIEXPORT void JVM_DefineModule(JNIEnv *env, jobject module, jboolean is_open, jstring version,
                 jstring location, const char* const* packages, jsize num_packages) {
    fprintf(stderr, "JVM_DefineModule called\n");
}

#endif

int jio_snprintf(char *str, size_t count, const char *fmt, ...) {
  va_list args;
  int len;
  va_start(args, fmt);
  len = jio_vsnprintf(str, count, fmt, args);
  va_end(args);
  return len;
}

int jio_vsnprintf(char *str, size_t count, const char *fmt, va_list args) {
  int result;

  if ((intptr_t)count <= 0) return -1;

  result = vsnprintf(str, count, fmt, args);
  if ((result > 0 && (size_t)result >= count) || result == -1) {
    str[count - 1] = '\0';
    result = -1;
  }

  return result;
}

