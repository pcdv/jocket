/*
 * Native part of jocket.futex.Futex: only the system calls. The futex word is
 * read and updated (CAS) on the Java side.
 */
#define _GNU_SOURCE
#include <linux/futex.h>
#include <unistd.h>
#include <sys/syscall.h>
#include <time.h>
#include <jni.h>
#include "jocket_futex_Futex.h"

JNIEXPORT jlong JNICALL Java_jocket_futex_Futex_getAddress
  (JNIEnv *env, jclass cls, jobject buf)
{
  return (jlong) (*env)->GetDirectBufferAddress(env, buf);
}

/*
 * No FUTEX_PRIVATE_FLAG: the word is shared with another process.
 */
JNIEXPORT void JNICALL Java_jocket_futex_Futex_wait0
  (JNIEnv *env, jclass cls, jlong addr, jint expected, jlong timeoutNanos)
{
  struct timespec timeout = { timeoutNanos / 1000000000, timeoutNanos % 1000000000 };
  syscall(SYS_futex, (int *) addr, FUTEX_WAIT, expected, &timeout, NULL, 0);
}

JNIEXPORT void JNICALL Java_jocket_futex_Futex_wake0
  (JNIEnv *env, jclass cls, jlong addr)
{
  syscall(SYS_futex, (int *) addr, FUTEX_WAKE, 1, NULL, NULL, 0);
}
