/*
 * Copyright (c) 2020, Gluon
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
#include <string.h>
#include <sys/types.h>
#include <sys/stat.h>

int __xstat(int ver, const char * path, struct stat64 * stat_buf) 
{
    return stat64(path, stat_buf);
}

int __lxstat(int ver, const char * path, struct stat64 * stat_buf)
{
    return lstat64(path, stat_buf);
}

int __fxstat(int ver, int fildes, struct stat64 * stat_buf)
{
    return fstat64(fildes, stat_buf);
}

int __xstat64(int ver, const char * path, struct stat64 * stat_buf) 
{
    return stat64(path, stat_buf);
}

int __lxstat64(int ver, const char * path, struct stat64 * stat_buf)
{
    return lstat64(path, stat_buf);
}

int __fxstat64(int ver, int fildes, struct stat64 * stat_buf)
{
    return fstat64(fildes, stat_buf);
}

char * __xpg_strerror_r(int errnum, char * buf, size_t buflen)
{
    strerror_r(errnum, buf, buflen);
    return buf;
}

size_t __getdelim(char **lineptr, size_t *n, int delim, FILE * stream)
{
    return getdelim(lineptr, n, delim, stream);
}

int __xmknod(int ver, const char *path, mode_t mode, dev_t *dev)
{
    return mknod(path, mode, *dev);
}

int __xmknodat(int ver, int fd, const char *path, mode_t mode, dev_t *dev)
{
	return mknodat(fd, path, mode, *dev);
}
