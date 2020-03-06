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
