/*
 * Copyright (c) 2019, Gluon
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
package com.gluonhq.substrate.util.ios;

import jnr.ffi.Pointer;
import jnr.ffi.annotations.Delegate;

/**
 *
 * @author johan
 */
public interface Device {

    public static interface InstproxyStatusCallback {
        @Delegate public void call(Pointer command, Pointer status, Pointer userData);
    }

    void idevice_set_debug_level(int l);

    int idevice_get_device_list(Pointer s, Pointer a);

    int idevice_new(Pointer devicePointer, String uDid);

    int idevice_connect(Pointer device, short port, Pointer deviceConnection);

    int idevice_connection_send(Pointer deviceConnection, byte[] data, int len, Pointer sentCountPointer);

    int idevice_connection_receive(Pointer connection, byte[] data, int len, Pointer receivedCountPointer, int timeout);

    int lockdownd_client_new_with_handshake(Pointer device, Pointer clientPointer, String label);

    int lockdownd_client_free(Pointer lockdownClient);

    int lockdownd_get_value(Pointer lockdownClient, String domain, String key, Pointer plistValue);

    int lockdownd_start_service(Pointer lockdownClient, String identifier, Pointer lockdownServiceDescriptorPointer);

    int afc_client_new(Pointer device, Pointer lockdownServiceDescriptor, Pointer afcClientPointer);

    int afc_client_free(Pointer afcClient);

    int afc_make_directory(Pointer afcClient, String path);

    int afc_file_open(Pointer afcClient, String filename, int fileMode, Pointer handlePointer);

    int afc_file_close(Pointer afcClient, long handle);

    int afc_file_write(Pointer afcClient, long handle, byte[] data, int length, Pointer bytesWrittenPointer);

    int afc_make_link(Pointer afcClient, int linktype, String target, String linkname);

    int instproxy_client_new(Pointer device, Pointer lockdownServiceDescriptor, Pointer client);

    int instproxy_client_free(Pointer instproxyClient);

    int instproxy_upgrade(Pointer instproxyClient, String pkgPath, Pointer clientOptions,
                           InstproxyStatusCallback statusCallback, Pointer userData);

    int instproxy_browse(Pointer instproxyClient, Pointer options, Pointer resultPlist);

    void plist_from_bin(byte[] b, long size, Pointer plist);

    void plist_to_bin(Pointer plist, Pointer content, Pointer length);
}
