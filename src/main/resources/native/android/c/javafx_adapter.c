/*
 * Copyright (c) 2020, 2022, Gluon
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
#include <stdlib.h>
#include <string.h>
#include "grandroid.h"

#ifdef JAVAFX_WEB
jclass nativeWebViewClass;
jobject nativeWebViewObj;
jmethodID nativeWebView_init;
jmethodID nativeWebView_loadUrl;
jmethodID nativeWebView_loadContent;
jmethodID nativeWebView_x;
jmethodID nativeWebView_y;
jmethodID nativeWebView_width;
jmethodID nativeWebView_height;
jmethodID nativeWebView_visible;
jmethodID nativeWebView_executeScript;
jmethodID nativeWebView_reload;
jmethodID nativeWebView_remove;
int reg = -1;

void registerJavaFXMethodHandles(JNIEnv *aenv)
{
    if (reg < 0) {
        nativeWebViewClass = (*aenv)->NewGlobalRef(aenv, (*aenv)->FindClass(aenv, "com/gluonhq/helloandroid/NativeWebView"));
        nativeWebView_init = (*aenv)->GetMethodID(aenv, nativeWebViewClass, "<init>", "()V");
        nativeWebView_loadUrl = (*aenv)->GetMethodID(aenv, nativeWebViewClass, "loadUrl", "(Ljava/lang/String;)V");
        nativeWebView_loadContent = (*aenv)->GetMethodID(aenv, nativeWebViewClass, "loadContent", "(Ljava/lang/String;)V");
        nativeWebView_x = (*aenv)->GetMethodID(aenv, nativeWebViewClass, "setX", "(D)V");
        nativeWebView_y = (*aenv)->GetMethodID(aenv, nativeWebViewClass, "setY", "(D)V");
        nativeWebView_width = (*aenv)->GetMethodID(aenv, nativeWebViewClass, "setWidth", "(D)V");
        nativeWebView_height = (*aenv)->GetMethodID(aenv, nativeWebViewClass, "setHeight", "(D)V");
        nativeWebView_visible = (*aenv)->GetMethodID(aenv, nativeWebViewClass, "setVisible", "(Z)V");
        nativeWebView_executeScript = (*aenv)->GetMethodID(aenv, nativeWebViewClass, "executeScript", "(Ljava/lang/String;)Ljava/lang/String;");
        nativeWebView_reload = (*aenv)->GetMethodID(aenv, nativeWebViewClass, "reload", "()V");
        nativeWebView_remove = (*aenv)->GetMethodID(aenv, nativeWebViewClass, "remove", "()V");
        reg = 1;
    }
}
#else
void registerJavaFXMethodHandles(JNIEnv *aenv) {}
#endif

JNIEXPORT void JNICALL Java_com_gluonhq_helloandroid_MainActivity_nativeSetSurface(JNIEnv *env, jobject activity, jobject surface)
{
    LOGE(stderr, "nativeSetSurface called, env at %p and size %ld, surface at %p\n", env, sizeof(JNIEnv), surface);
    if (surface != NULL) {
        window = ANativeWindow_fromSurface(env, surface);
        androidJfx_setNativeWindow(window);
        LOGE(stderr, "native setSurface Ready, native window at %p\n", window);
    } else {
        androidJfx_setNativeWindow(NULL);
        LOGE(stderr, "native setSurface was null");
    }
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
    ATTACH_DALVIK();
    LOGE(stderr, "now I have to show keyboard, invoke method %p on env %p\n", activity_showIME, dalvikEnv);
    (*dalvikEnv)->CallStaticVoidMethod(dalvikEnv, activityClass, activity_showIME);
    LOGE(stderr, "I did show keyboard\n");
    DETACH_DALVIK();
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
    ATTACH_DALVIK();
    LOGE(stderr, "now I have to hide keyboard, invoke method %p on env %p\n", activity_hideIME, dalvikEnv);
    (*dalvikEnv)->CallStaticVoidMethod(dalvikEnv, activityClass, activity_hideIME);
    LOGE(stderr, "I did hide keyboard\n");
    DETACH_DALVIK();
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

#ifdef JAVAFX_WEB
void substrate_showWebView() {
    LOGE(stderr, "Substrate needs to show Webview\n");
    ATTACH_DALVIK();
    jobject tmpobj = (jobject)((*dalvikEnv)->NewObject(dalvikEnv, nativeWebViewClass, nativeWebView_init));
    nativeWebViewObj = (jobject)((*dalvikEnv)->NewGlobalRef(dalvikEnv, tmpobj));
    LOGE(stderr, "Substrate Created Android WebView\n");
    if ((*dalvikEnv)->ExceptionOccurred(dalvikEnv)) {
        LOGE(stderr, "EXCEPTION CREATING WEBVIEW\n");
    }
    DETACH_DALVIK();
}

void substrate_loadUrl(char* curl) {
    ATTACH_DALVIK();
    LOGE(stderr, "load curl: %s\n", curl);
    jstring jurl = (*dalvikEnv)->NewStringUTF(dalvikEnv, curl);
    LOGE(stderr, "call loadurl and wvo = %p\n", nativeWebViewObj);
    (*dalvikEnv)->CallVoidMethod(dalvikEnv, nativeWebViewObj, nativeWebView_loadUrl, jurl);
    // Release
    DETACH_DALVIK();
}

void substrate_loadContent(char* curl) {
    ATTACH_DALVIK();
    LOGE(stderr, "load content: %s\n", curl);
    jstring jurl = (*dalvikEnv)->NewStringUTF(dalvikEnv, curl);
    LOGE(stderr, "call loadContent and wvo = %p\n", nativeWebViewObj);
    (*dalvikEnv)->CallVoidMethod(dalvikEnv, nativeWebViewObj, nativeWebView_loadContent, jurl);
    // Release
    DETACH_DALVIK();
}

void substrate_setWebViewX(double x) {
    ATTACH_DALVIK();
    LOGE(stderr, "webView x %f\n", x);
    (*dalvikEnv)->CallVoidMethod(dalvikEnv, nativeWebViewObj, nativeWebView_x, x * density);
    DETACH_DALVIK();
}

void substrate_setWebViewY(double y) {
    ATTACH_DALVIK();
    LOGE(stderr, "webView y %f\n", y);
    (*dalvikEnv)->CallVoidMethod(dalvikEnv, nativeWebViewObj, nativeWebView_y, y * density);
    DETACH_DALVIK();
}

void substrate_setWebViewWidth(double width) {
    ATTACH_DALVIK();
    LOGE(stderr, "webView width %f\n", width);
    (*dalvikEnv)->CallVoidMethod(dalvikEnv, nativeWebViewObj, nativeWebView_width, width * density);
    DETACH_DALVIK();
}

void substrate_setWebViewHeight(double height) {
    ATTACH_DALVIK();
    LOGE(stderr, "webView height %f\n", height);
    (*dalvikEnv)->CallVoidMethod(dalvikEnv, nativeWebViewObj, nativeWebView_height, height * density);
    DETACH_DALVIK();
}

void substrate_setWebViewVisible(jboolean visible) {
    ATTACH_DALVIK();
    LOGE(stderr, "webView visible %d\n", (visible ? 1 : 0));
    (*dalvikEnv)->CallVoidMethod(dalvikEnv, nativeWebViewObj, nativeWebView_visible, visible);
    DETACH_DALVIK();
}

char* substrate_executeScript(char* script) {
    ATTACH_DALVIK();
    LOGE(stderr, "load script\n");
    jstring jscript = (*dalvikEnv)->NewStringUTF(dalvikEnv, script);
    jstring result = (*dalvikEnv)->CallObjectMethod(dalvikEnv, nativeWebViewObj, nativeWebView_executeScript, jscript);
    const char *resultChars = (*dalvikEnv)->GetStringUTFChars(dalvikEnv, result, 0);
    LOGE(stderr, "script result: %s\n", resultChars);
    // Release
    DETACH_DALVIK();
    return resultChars;
}

void substrate_reloadWebView() {
    ATTACH_DALVIK();
    LOGE(stderr, "reload webView\n");
    (*dalvikEnv)->CallVoidMethod(dalvikEnv, nativeWebViewObj, nativeWebView_reload);
    DETACH_DALVIK();
}

void substrate_removeWebView() {
    ATTACH_DALVIK();
    LOGE(stderr, "remove webView\n");
    (*dalvikEnv)->CallVoidMethod(dalvikEnv, nativeWebViewObj, nativeWebView_remove);
    DETACH_DALVIK();
}

// Callbacks

JNIEXPORT void JNICALL Java_com_gluonhq_helloandroid_NativeWebView_nativeStartURL(JNIEnv *env, jobject activity, jstring url)
{
    const char *curl = (*env)->GetStringUTFChars(env, url, NULL);
    LOGE(stderr, "nativeStartURL called. URL: %s\n", curl);
    androidJfx_startURL(curl);
    (*env)->ReleaseStringUTFChars(env, url, curl);
}

JNIEXPORT void JNICALL Java_com_gluonhq_helloandroid_NativeWebView_nativeFinishURL(JNIEnv *env, jobject activity, jstring url, jstring html)
{
    LOGE(stderr, "nativeFinishURL called. Invoke method on webView\n");
    const char *curl = (*env)->GetStringUTFChars(env, url, NULL);
    const char *chtml = (*env)->GetStringUTFChars(env, html, NULL);
    androidJfx_finishURL(curl, chtml);
    (*env)->ReleaseStringUTFChars(env, url, curl);
    (*env)->ReleaseStringUTFChars(env, html, chtml);
}

JNIEXPORT void JNICALL Java_com_gluonhq_helloandroid_NativeWebView_nativeFailedURL(JNIEnv *env, jobject activity, jstring url)
{
    LOGE(stderr, "nativeFailedURL called. Invoke method on webView\n");
    const char *curl = (*env)->GetStringUTFChars(env, url, NULL);
    androidJfx_failedURL(curl);
    (*env)->ReleaseStringUTFChars(env, url, curl);
}

JNIEXPORT void JNICALL Java_com_gluonhq_helloandroid_NativeWebView_nativeJavaCallURL(JNIEnv *env, jobject activity, jstring url)
{
    LOGE(stderr, "nativeJavaCallURL called. Invoke method on webView\n");
    const char *curl = (*env)->GetStringUTFChars(env, url, NULL);
    androidJfx_javaCallURL(curl);
    (*env)->ReleaseStringUTFChars(env, url, curl);
}

#endif