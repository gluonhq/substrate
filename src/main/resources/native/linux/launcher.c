/*
 * Copyright (c) 2019, 2021, Gluon
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
#include <stdio.h>
#include <math.h>

double pow_old(double x, double y) {
#ifdef __amd64__
    __asm__(".symver pow_old,pow@GLIBC_2.2.5");
#elif defined(__aarch64__)
    __asm__(".symver pow_old,pow@GLIBC_2.17");
#endif
}

double __wrap_pow(double x, double y) {
    return pow_old(x, y);
}

extern int *run_main(int argc, const char* argv[]);

int main(int argc, const char* argv[]) {
    #ifdef GVM_VERBOSE
      fprintf(stderr, "Main\n");
    #endif
    (*run_main)(argc, argv);
}

// the following functions are used in Java 11 but not in 14
// we use the native libs from 14.
// in case they are still able to be called, we need to implement them

void Java_java_io_ObjectOutputStream_floatsToBytes( ) {
    fprintf(stderr, "FloatsToBytesmismatch\n");
}

void Java_java_io_ObjectOutputStream_doublesToBytes() {
    fprintf(stderr, "DoublesToBytesmismatch\n");
}

#ifdef AARCH64

typedef struct {
  char fFP;
  char fASIMD;
  char fEVTSTRM;
  char fAES;
  char fPMULL;
  char fSHA1;
  char fSHA2;
  char fCRC32;
  char fLSE;
  char fSTXRPREFETCH;
  char fA53MAC;
  char fDMBATOMICS;
} CPUFeatures;

void determineCPUFeatures(CPUFeatures* features) {
    fprintf(stderr, "\n\n\ndetermineCpuFeaures\n");
    features->fFP = 1;
    features->fASIMD = 1;
}

void JVM_NativePath() {
    fprintf(stderr, "We should never reach here (JVM_nativePath)\n");
}

void JVM_RawMonitorCreate() {
    fprintf(stderr, "We should never reach here (JVM_RawMonitorCreate)\n");
}

void JVM_RawMonitorDestroy() {
    fprintf(stderr, "We should never reach here (JVM_RawMonitorDestroy)\n");
}

void JVM_RawMonitorEnter() {
    fprintf(stderr, "We should never reach here (JVM_RawMonitorEnter)\n");
}

void JVM_RawMonitorExit() {
    fprintf(stderr, "We should never reach here (JVM_RawMonitorExit)\n");
}

// These functions come from unused glass-monocle code that should be removed from OpenJFX
void getNativeWindowType() {
fprintf(stderr, "NOT IMPLEMENTED\n");
}

void getNativeDisplayType() {
fprintf(stderr, "NOT IMPLEMENTED\n");
}

void getLibGLEShandle() {
fprintf(stderr, "NOT IMPLEMENTED\n");
}

#ifndef GVM_17
// dummy symbols only for JDK11
void Java_java_net_PlainDatagramSocketImpl_send0() {}
#endif

#endif // AARCH64

