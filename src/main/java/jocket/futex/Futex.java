package jocket.futex;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * A futex word in a direct (mapped) buffer, allowing interprocess wait/notify.
 * Only available under Linux, through a JNI library.
 * <p>
 * The word is 0 when idle, -1 when a thread is waiting (or about to), and 1
 * when a signal is pending. The waiter calls {@link #await(long)} when it has
 * nothing left to do, the other side calls {@link #signal()} when it has
 * produced something. Only the slow path (a thread actually waiting) involves
 * a JNI call and a system call.
 *
 * @author pcdv
 */
public final class Futex {

  private static final VarHandle INT =
    MethodHandles.byteBufferViewVarHandle(int[].class, ByteOrder.nativeOrder());

  private static final boolean libAvailable = loadLibrary();

  private final ByteBuffer buf;

  private final int pos;

  private final long addr;

  /**
   * @param buf a direct buffer, shared with the other process
   * @param pos position of the futex word in the buffer (4-byte aligned)
   */
  public Futex(ByteBuffer buf, int pos) {
    if (!buf.isDirect())
      throw new IllegalArgumentException("Not a direct buffer");
    this.buf = buf;
    this.pos = pos;
    this.addr = getAddress(buf) + pos;
  }

  /**
   * Waits until {@link #signal()} is called or the timeout expires. Returns at
   * once if a signal is pending. May also return spuriously: the caller must
   * check its condition again.
   */
  public void await(long timeoutNanos) {
    if ((int) INT.compareAndExchange(buf, pos, 0, -1) == 0)
      wait0(addr, -1, timeoutNanos);
    // consumes the signal, and resets the word after a timeout
    INT.setRelease(buf, pos, 0);
  }

  /**
   * Wakes up the waiting thread, if any. Otherwise, makes the next call to
   * {@link #await(long)} return at once.
   */
  public void signal() {
    if ((int) INT.compareAndExchange(buf, pos, 0, 1) == -1) {
      INT.setRelease(buf, pos, 0);
      wake0(addr);
    }
  }

  public static boolean isAvailable() {
    return libAvailable;
  }

  private static native long getAddress(ByteBuffer b);

  /**
   * FUTEX_WAIT: sleeps if the word at addr still equals expected.
   */
  private static native void wait0(long addr, int expected, long timeoutNanos);

  /**
   * FUTEX_WAKE: wakes up one thread waiting on the word at addr.
   */
  private static native void wake0(long addr);

  private static boolean loadLibrary() {
    try {
      LibLoader.loadLibrary("JocketFutex");
      return true;
    }
    catch (Throwable e) {
      System.err.println("WARN: JNI futex lib is NOT available: " + e);
      return false;
    }
  }
}
