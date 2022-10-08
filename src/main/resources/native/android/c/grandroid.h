/*
 * Copyright (c) 2020, 2021, Gluon
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
#include "grandroid_ext.h"

#include <android/native_window_jni.h>
#include <stdbool.h>

#undef com_sun_glass_events_TouchEvent_TOUCH_PRESSED
#define com_sun_glass_events_TouchEvent_TOUCH_PRESSED 811L
#undef com_sun_glass_events_TouchEvent_TOUCH_MOVED
#define com_sun_glass_events_TouchEvent_TOUCH_MOVED 812L
#undef com_sun_glass_events_TouchEvent_TOUCH_RELEASED
#define com_sun_glass_events_TouchEvent_TOUCH_RELEASED 813L
#undef com_sun_glass_events_TouchEvent_TOUCH_STILL
#define com_sun_glass_events_TouchEvent_TOUCH_STILL 814L

extern jmethodID activity_showIME;
extern jmethodID activity_hideIME;

extern ANativeWindow *window;
extern jfloat density;

void __attribute__((weak)) androidJfx_requestGlassToRedraw() {}
void __attribute__((weak)) androidJfx_setNativeWindow(ANativeWindow *nativeWindow) {}
void __attribute__((weak)) androidJfx_setDensity(float nativeDensity) {}
void __attribute__((weak)) androidJfx_gotTouchEvent(int count, int *actions, int *ids, int *xs, int *ys, int primary) {}
void __attribute__((weak)) androidJfx_gotKeyEvent(int action, int key, jchar *chars, int count, int mods) {}
void __attribute__((weak)) androidJfx_gotMenuEvent(int x, int y, int xAbs, int yAbs, bool isKeyboardTrigger) {}
int  __attribute__((weak)) to_jfx_touch_action(int state) { return 0; }

void __attribute__((weak)) androidJfx_startURL(const char *url) {}
void __attribute__((weak)) androidJfx_finishURL(const char *url, const char *html) {}
void __attribute__((weak)) androidJfx_failedURL(const char *url) {}
void __attribute__((weak)) androidJfx_javaCallURL(const char *url) {}
