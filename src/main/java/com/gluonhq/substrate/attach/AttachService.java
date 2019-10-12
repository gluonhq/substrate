/*
 * Copyright (c) 2019, Gluon
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
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
package com.gluonhq.substrate.attach;

import java.util.Locale;

/**
 * List of all available services in Attach
 * See https://github.com/gluonhq/attach
 *
 * Each service has an iOS implementation, and some of them
 * a desktop implementation as well
 */
public enum AttachService {
    ACCELEROMETER,
    AUDIO_RECORDING(true /* desktopSupported */),
    AUGMENTED_REALITY,
    BARCODE_SCAN,
    BATTERY,
    BLE,
    BROWSER,
    CACHE(true /* desktopSupported */),
    COMPASS,
    CONNECTIVITY,
    DEVICE,
    DIALER,
    DISPLAY(true /* desktopSupported */),
    IN_APP_BILLING,
    LIFECYCLE(true /* desktopSupported */),
    LOCAL_NOTIFICATIONS,
    MAGNETOMETER,
    ORIENTATION,
    PICTURES,
    POSITION,
    PUSH_NOTIFICATIONS,
    RUNTIME_ARGS(true /* desktopSupported */),
    SETTINGS(true /* desktopSupported */),
    SHARE,
    STATUSBAR,
    STORAGE(true /* desktopSupported */),
    VIBRATION,
    VIDEO;

    private boolean androidSupported = false;
    private boolean iosSupported = true;
    private boolean desktopSupported = false;

    AttachService() { }

    AttachService(boolean desktopSupported) {
        this.desktopSupported = desktopSupported;
    }

    public String getServiceName() {
        return name().replace('_', '-').toLowerCase(Locale.ROOT);
    }

    public boolean isAndroidSupported() {
        return androidSupported;
    }

    public boolean isIosSupported() {
        return iosSupported;
    }

    public boolean isDesktopSupported() {
        return desktopSupported;
    }
}
