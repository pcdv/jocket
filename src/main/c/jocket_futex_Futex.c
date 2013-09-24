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

  JNIEXPORT void JNICALL Java_jocket_futex_Futex_pause
    (JNIEnv *env, jclass cls, jlong futAddr, jlong seqAddr, jint oldseq)
    {
      unsigned int i = 0;
      jint *seqPtr = (jint *)seqAddr;
      for (i = 0; i < 510 && *seqPtr == oldseq; i++) {
        if (i >= 500) {
          Java_jocket_futex_Futex_await0(env, cls, futAddr);
          break;
        }
        asm("pause");
        __sync_synchronize();
      }
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
      const struct timespec timeout = { 0, 100000000 };

      // a value other than 0 indicates that data became available => no wait
      int val = __sync_val_compare_and_swap(ptr, 0, -1); 
      if (val == 0) {
        syscall(SYS_futex, (jint *)addr, FUTEX_WAIT, -1, timeout, NULL, 0);
      }
      else *ptr = 0;
    }

  JNIEXPORT void JNICALL Java_jocket_futex_Futex_x86pause
    (JNIEnv *env, jclass cls)
    {
      asm("pause");
    }



#if defined(__i386__)
static __inline__ unsigned long long rdtsc(void) {
  unsigned long long int x;
     __asm__ volatile (".byte 0x0f, 0x31" : "=A" (x));
     return x;
}

#elif defined(__x86_64__)
static __inline__ unsigned long long rdtsc(void) {
  unsigned hi, lo;
  __asm__ __volatile__ ("rdtsc" : "=a"(lo), "=d"(hi));
  return ( (unsigned long long)lo)|( ((unsigned long long)hi)<<32 );
}

#endif

JNIEXPORT jlong JNICALL Java_jocket_futex_Futex_rdtsc
  (JNIEnv *env, jclass c) {
  return (jlong) rdtsc();
}

#ifdef __cplusplus
}
#endif
