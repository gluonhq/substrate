/*
 * Copyright (c) 2021, 2024, Gluon
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

#ifndef BRIDGE_WEBVIEW_H
#define BRIDGE_WEBVIEW_H

#include <stdio.h>
#include <unistd.h>

JavaVM* getWebViewGraalVM();

#define ATTACH_GRAAL() \
    JNIEnv *graalEnv; \
    JavaVM* graalVM = getWebViewGraalVM(); \
    int tid = gettid(); \
    int attach_graal_det = ((*graalVM)->GetEnv(graalVM, (void **)&graalEnv, JNI_VERSION_1_6) == JNI_OK); \
    (*graalVM)->AttachCurrentThreadAsDaemon(graalVM, (void **) &graalEnv, NULL);

#define DETACH_GRAAL() \
    int tid_detach = gettid(); \
    if (attach_graal_det == 0) (*graalVM)->DetachCurrentThread(graalVM);

#define ATTACH_DALVIK() \
    JNIEnv *dalvikEnv; \
    JavaVM* dalvikVM = substrateGetAndroidVM(); \
    int dalviktid = gettid(); \
    int attach_dalvik_det = ((*dalvikVM)->GetEnv(dalvikVM, (void **)&dalvikEnv, JNI_VERSION_1_6) == JNI_OK); \
    (*dalvikVM)->AttachCurrentThreadAsDaemon(dalvikVM, (void **) &dalvikEnv, NULL);

#define DETACH_DALVIK() \
    int dalviktid_detach = gettid(); \
    if (attach_dalvik_det == 0) (*dalvikVM)->DetachCurrentThread(dalvikVM);

void androidJfx_startURL(const char *url);
void androidJfx_finishURL(const char *url, const char *html);
void androidJfx_failedURL(const char *url);
void androidJfx_javaCallURL(const char *url);

#endif  /* BRIDGE_WEBVIEW_H */
