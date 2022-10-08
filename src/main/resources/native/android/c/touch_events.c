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
#include "grandroid.h"

JNIEXPORT void JNICALL
Java_javafx_scene_control_skin_TextFieldSkinAndroid_showSoftwareKeyboard();

JNIEXPORT void JNICALL Java_com_gluonhq_helloandroid_MainActivity_nativeGotTouchEvent(JNIEnv *env, jobject activity, jint jcount, jintArray jactions, jintArray jids, jintArray jxs, jintArray jys)
{
//    LOGE(stderr, "Native Dalvik layer got touch event, pass to native Graal layer...");

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

//    LOGE(stderr, "Native Dalvik layer got touch event, passed to native Graal layer...");
}

JNIEXPORT void JNICALL Java_com_gluonhq_helloandroid_MainActivity_nativeDispatchKeyEvent(JNIEnv *env, jobject activity, jint action, jint keyCode, jcharArray jchars, jint cc, jint modifiers)
{
//    LOGE(stderr, "Native Dalvik layer has to dispatch key event, pass to native Graal layer with %d chars...", cc);
    jchar *kars = (*env)->GetCharArrayElements(env, jchars, 0);
    androidJfx_gotKeyEvent(action, keyCode, kars, cc, modifiers);
}

JNIEXPORT void JNICALL Java_com_gluonhq_helloandroid_MainActivity_nativeNotifyMenu(JNIEnv *env, jobject activity, jint x, jint y, jint xAbs, jint yAbs, jboolean isKeyboardTrigger)
{
//    LOGE(stderr, "Native Dalvik layer got notify menu, pass to native Graal layer...");
    androidJfx_gotMenuEvent(x, y, xAbs, yAbs, isKeyboardTrigger);
}