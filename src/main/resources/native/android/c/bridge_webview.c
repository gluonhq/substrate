/*
 * Copyright (c) 2021, Gluon
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.

 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
 * ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL GLUON BE LIABLE FOR ANY
 * DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
 * (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
 * ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

#include "jni.h"
#include "bridge_webview.h"

extern void substrate_showWebView();
extern void substrate_loadUrl(char *c);
extern void substrate_loadContent(char *c);
extern void substrate_setWebViewX(double x);
extern void substrate_setWebViewY(double y);
extern void substrate_setWebViewWidth(double w);
extern void substrate_setWebViewHeight(double h);
extern void substrate_setWebViewVisible(jboolean visible);
extern void substrate_reloadWebView();
extern void substrate_removeWebView();
extern char* substrate_executeScript(char *c);

static jobject webViewObject;
static jmethodID jmidLoadStarted;
static jmethodID jmidLoadFinished;
static jmethodID jmidLoadFailed;
static jmethodID jmidJavaCall;

static JavaVM *jvm;

JavaVM* getWebViewGraalVM() {
    return jvm;
}

static void initializeWebViewHandles(JNIEnv *env, jobject object) {
    fprintf(stderr, "WebView, initializeWebViewHandles called\n");
    webViewObject = (*env)->NewGlobalRef(env, object);
    jclass webViewClass = (*env)->GetObjectClass(env, object);

    //webViewClass = (*env)->NewGlobalRef(env, (*env)->FindClass(env, "javafx/scene/web/WebView"));
    jmidLoadStarted = (*env)->GetMethodID(env, webViewClass, "notifyLoadStarted", "()V");
    jmidLoadFinished = (*env)->GetMethodID(env, webViewClass, "notifyLoadFinished", "(Ljava/lang/String;Ljava/lang/String;)V");
    jmidLoadFailed = (*env)->GetMethodID(env, webViewClass, "notifyLoadFailed", "()V");
    jmidJavaCall = (*env)->GetMethodID(env, webViewClass, "notifyJavaCall", "(Ljava/lang/String;)V");
    fprintf(stderr, "WebView, initializeWebViewHandles done\n");
}

jint JNI_OnLoad_webview(JavaVM *vm, void *reserved) {
    JNIEnv *env;
    if ((*vm)->GetEnv(vm, (void **) &env, JNI_VERSION_1_6)) {
        return JNI_ERR; /* JNI version not supported */
    }
    jvm = vm;
    return JNI_VERSION_1_6;
}

JNIEXPORT void JNICALL
    Java_javafx_scene_web_WebView__1initWebView(JNIEnv *env, jobject obj, jlongArray nativeHandle) {
    initializeWebViewHandles(env, obj);
    fprintf(stderr, "WebView, initWebView called\n");
    substrate_showWebView();
    fprintf(stderr, "WebView, initWebView done\n");
}

JNIEXPORT void JNICALL
    Java_javafx_scene_web_WebEngine__1loadUrl(JNIEnv *env, jobject cl, jlong handle, jstring str) {
    fprintf(stderr, "WebView, loadurl calling\n");
    char *curl = (char *)(*env)->GetStringUTFChars(env, str, JNI_FALSE);
    substrate_loadUrl(curl);
    (*env)->ReleaseStringUTFChars(env, str, curl);
    fprintf(stderr, "WebView, loadurl calling done\n");
}

JNIEXPORT jstring JNICALL
    Java_javafx_scene_web_WebEngine__1executeScript(JNIEnv *env, jobject cl, jlong handle, jstring script) {
    fprintf(stderr, "WebView, executeScript calling\n");
    char *cscript = (char *)(*env)->GetStringUTFChars(env, script, JNI_FALSE);
    char *result = substrate_executeScript(cscript);
    (*env)->ReleaseStringUTFChars(env, script, cscript);
    jstring jresult = (*env)->NewStringUTF(env, result);
    fprintf(stderr, "WebView, executeScript calling done with result %s\n", result);
    return jresult;
}

JNIEXPORT void JNICALL
    Java_javafx_scene_web_WebEngine__1loadContent(JNIEnv *env, jobject cl, jlong handle, jstring content) {
    fprintf(stderr, "WebView, loadContent calling\n");
    char *curl = (char *)(*env)->GetStringUTFChars(env, content, JNI_FALSE);
    substrate_loadContent(curl);
    (*env)->ReleaseStringUTFChars(env, content, curl);
    fprintf(stderr, "WebView, loadContentcalling done\n");
}

JNIEXPORT void JNICALL
    Java_javafx_scene_web_WebEngine__1reload(JNIEnv *env, jobject cl, jlong handle) {
    fprintf(stderr, "WebView, reload called\n");
    substrate_reloadWebView();
}

JNIEXPORT void JNICALL
    Java_javafx_scene_web_WebView__1setWidth(JNIEnv *env, jobject cl, jlong handle, jdouble w) {
    fprintf(stderr, "WebView, setwidth called\n");
    substrate_setWebViewWidth(w);
}

JNIEXPORT void JNICALL
    Java_javafx_scene_web_WebView__1setHeight(JNIEnv *env, jobject cl, jlong handle, jdouble h) {
    fprintf(stderr, "WebView, setheight called\n");
    substrate_setWebViewHeight(h);
}

JNIEXPORT void JNICALL
    Java_javafx_scene_web_WebView__1setVisible(JNIEnv *env, jobject cl, jlong handle, jboolean v) {
    fprintf(stderr, "WebView, setvisible called\n");
    substrate_setWebViewVisible(v);
}

JNIEXPORT void JNICALL
    Java_javafx_scene_web_WebView__1removeWebView(JNIEnv *env, jobject cl, jlong handle) {
    fprintf(stderr, "WebView, removeWebView called\n");
    substrate_removeWebView();
}

JNIEXPORT void JNICALL
    Java_javafx_scene_web_WebView__1setTransform(JNIEnv *env, jobject cl, jlong handle,
        jdouble mxx, jdouble mxy, jdouble mxz, jdouble mxt,
        jdouble myx, jdouble myy, jdouble myz, jdouble myt,
        jdouble mzx, jdouble mzy, jdouble mzz, jdouble mzt) {
    fprintf(stderr, "WebView, setTransform called %f %f %f %f\n", mxt, myt, mxx, myy);
    substrate_setWebViewX(mxt);
    substrate_setWebViewY(myt);
}

void androidJfx_startURL(const char *url) {
    ATTACH_GRAAL();
    fprintf(stderr, "WebView, androidJfx_startURL %s\n", url);
    (*graalEnv)->CallVoidMethod(graalEnv, webViewObject, jmidLoadStarted);
    DETACH_GRAAL();
}

void androidJfx_finishURL(const char *url, const char *html) {
    fprintf(stderr, "WebView, androidJfx_finishURL %s\n", url);
    ATTACH_GRAAL();
    jstring jurl = (*graalEnv)->NewStringUTF(graalEnv, url);
    jstring jhtml = (*graalEnv)->NewStringUTF(graalEnv, html);
    (*graalEnv)->CallVoidMethod(graalEnv, webViewObject, jmidLoadFinished, jurl, jhtml);
    (*graalEnv)->ReleaseStringUTFChars(graalEnv, url, jurl);
    (*graalEnv)->ReleaseStringUTFChars(graalEnv, html, jhtml);
    DETACH_GRAAL();
}

void androidJfx_failedURL(const char *url) {
    ATTACH_GRAAL();
    fprintf(stderr, "WebView, androidJfx_failedURL %s\n", url);
    (*graalEnv)->CallVoidMethod(graalEnv, webViewObject, jmidLoadFailed);
    DETACH_GRAAL();
}

void androidJfx_javaCallURL(const char *url) {
    ATTACH_GRAAL();
    jstring jurl = (*graalEnv)->NewStringUTF(graalEnv, url);
    fprintf(stderr, "WebView, androidJfx_javaCallURL %s\n", url);
    (*graalEnv)->CallVoidMethod(graalEnv, webViewObject, jmidJavaCall, jurl);
    (*graalEnv)->ReleaseStringUTFChars(graalEnv, url, jurl);
    DETACH_GRAAL();
}
