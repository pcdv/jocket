package jocket.futex;

import java.nio.MappedByteBuffer;

import jocket.wait.WaitStrategy;

/**
 * When available (i.e. only under linux) allows to perform interprocess
 * wait/notify. The writer calls signal() to notify new packets, the reader
 * calls pauseWhile() when it has read all available packets and must wait for a
 * new one.
 * 
 * @author pcdv
 */
public class Futex implements WaitStrategy {

  private static boolean libAvailable;

  private final long futAddr;

  private final long seqAddr;

  private final MappedByteBuffer buf;

  private final int seqPos;

  /**
   * @param b a mapped byte buffer
   * @param futexPos position of the futex variable in buffer
   * @param seqPos position where the writer writes its latest sequence number
   */
  public Futex(MappedByteBuffer b, int futexPos, int seqPos) {
    this.buf = b;
    this.seqPos = seqPos;
    this.futAddr = computeAddress(b, futexPos);
    this.seqAddr = computeAddress(b, seqPos);
  }

  /**
   * Blocks until the writer posts a packet with a seqnum higher than specified.
   */
  @Override
  public void pauseWhile(int currentSeq) {
    pause(futAddr, seqAddr, currentSeq);
  }

  private static native void pause(long futAddr, long seqAddr, int seq);

  @Override
  public void reset() {
  }

  private static long computeAddress(MappedByteBuffer b, int pos) {
    try {
      return getAddress(b) + pos;
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  /**
   * For test purposes. Unsafe. Do not use.
   */
  public int getInt(int pos) {
    return getInt0(computeAddress(buf, pos));
  }

  /**
   * For test purposes. Unsafe. Do not use.
   */
  public void setInt(int pos, int value) {
    setInt0(computeAddress(buf, pos), value);
  }

  private static native int getInt0(long addr);

  private static native void setInt0(long addr, int value);

  /**
   * Signals the reader that a new packet is available. Happens mainly during
   * flush(). It includes the cost of a JNI call and a native CAS. A FUTEX_CALL
   * syscall is performed only if necessary (i.e. the reader is waiting for
   * data).
   * 
   * @param seq
   */
  public void signal(int seq) {
    signal0(futAddr);
  }

  @Deprecated
  public void await() {
    await0(futAddr);
  }

  public static native long getAddress(MappedByteBuffer b);

  private static native void signal0(long addr);

  @Deprecated
  private static native void await0(long addr);

  public static native long rdtsc();

  static {
    try {
      LibLoader.loadLibrary("JocketFutex");
      libAvailable = true;
    } catch (Throwable e) {
      libAvailable = false;
      System.err.println("WARN: JNI futex lib is NOT available: " + e);
    }
  }

  public static boolean isAvailable() {
    return libAvailable;
  }
}
