#include <jni.h>

#ifndef _Included_jocket_futex_Futex_critical
#define _Included_jocket_futex_Futex_critical
#ifdef __cplusplus
extern "C" {
#endif
/*
 * Class:     jocket_futex_Futex
 * Method:    pause
 * Signature: (JJI)V
 */
JNIEXPORT void JNICALL JavaCritical_jocket_futex_Futex_pause
  (jlong, jlong, jint);

/*
 * Class:     jocket_futex_Futex
 * Method:    signal0
 * Signature: (J)V
 */
JNIEXPORT void JNICALL JavaCritical_jocket_futex_Futex_signal0
  (jlong);

/*
 * Class:     jocket_futex_Futex
 * Method:    await0
 * Signature: (J)V
 */
JNIEXPORT void JNICALL JavaCritical_jocket_futex_Futex_await0
  (jlong);

/*
 * Class:     jocket_futex_Futex
 * Method:    rdtsc
 * Signature: ()J
 */
JNIEXPORT jlong JNICALL JavaCritical_jocket_futex_Futex_rdtsc
  ();

#ifdef __cplusplus
}
#endif
#endif
