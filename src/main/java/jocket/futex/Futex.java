package jocket.futex;

import java.nio.MappedByteBuffer;

import jocket.wait.WaitStrategy;

public class Futex implements WaitStrategy {

  private static boolean libAvailable;

  private final long futAddr;

  private final long seqAddr;

  private final MappedByteBuffer buf;

  private final int seqPos;

  public Futex(MappedByteBuffer b, int futexPos, int seqPos) {
    this.buf = b;
    this.seqPos = seqPos;
    this.futAddr = computeAddress(b, futexPos);
    this.seqAddr = computeAddress(b, seqPos);
  }

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
    }
    catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  // TEST
  public int getInt(int pos) {
    return getInt0(computeAddress(buf, pos));
  }

  public void setInt(int pos, int value) {
    setInt0(computeAddress(buf, pos), value);
  }

  private static native int getInt0(long addr);

  private static native void setInt0(long addr, int value);

  public void signal(int seq) {
    signal0(futAddr);
  }

  public void await() {
    await0(futAddr);
  }

  public static native long getAddress(MappedByteBuffer b);

  private static native void signal0(long addr);

  private static native void await0(long addr);

  public static native void x86pause();

  public static native long rdtsc();

  static {
    try {
      LibLoader.loadLibrary("JocketFutex");
      libAvailable = true;
    }
    catch (Throwable e) {
      libAvailable = false;
      System.err.println("WARN: JNI futex lib is NOT available: " + e);
      e.printStackTrace();
    }
  }

  public static boolean isAvailable() {
    return libAvailable;
  }
}
