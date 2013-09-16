#define _GNU_SOURCE        /* or _BSD_SOURCE or _SVID_SOURCE */
#include <linux/futex.h>
#include <unistd.h>
#include <sys/syscall.h>   /* For SYS_xxx definitions */
#include <sys/time.h>
#include <jni.h>
#include "jocket_futex_Futex.h"

#ifdef __cplusplus
extern "C" {
#endif

  JNIEXPORT jlong JNICALL Java_jocket_futex_Futex_getAddress
    (JNIEnv *env, jclass cls, jobject buf)
    {
      return (jlong) (*env)->GetDirectBufferAddress(env, buf);
    }

  JNIEXPORT void JNICALL Java_jocket_futex_Futex_signal0
    (JNIEnv *env, jclass cls, jlong addr)
    {
      jint *ptr = (jint *)addr;
      // a value of -1 implies that a thread is waiting
      if (__sync_val_compare_and_swap(ptr, 0, 1) == -1) {
        *ptr = 0;
        syscall(SYS_futex, (jint *)addr, FUTEX_WAKE, 0, NULL, NULL, 0);
      }
    }

  JNIEXPORT void JNICALL Java_jocket_futex_Futex_await0
    (JNIEnv *env, jclass cls, jlong addr)
    {
      // TODO: add timeout parameter to avoid infinite wait
      jint *ptr = (jint *)addr;
      // a value other than 0 indicates that data became available => no wait
      if (__sync_val_compare_and_swap(ptr, 0, -1) == 0) {
        syscall(SYS_futex, (jint *)addr, FUTEX_WAIT, -1, NULL, NULL, 0);
      }
    }

#ifdef __cplusplus
}
#endif
