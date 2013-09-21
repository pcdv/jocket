package jocket.futex;

import java.nio.MappedByteBuffer;

import jocket.wait.WaitStrategy;

public class Futex implements WaitStrategy {

  private static boolean libAvailable;

  private final long futAddr;

  private final long seqAddr;

  public Futex(MappedByteBuffer b, int futexPos, int seqPos) {
    this.futAddr = computeAddress(b, futexPos);
    this.seqAddr = computeAddress(b, seqPos);
  }

  @Override
  public void pause(int seq) {
    pause(futAddr, seqAddr, seq);
  }

  private static native void pause(long futAddr2, long seqAddr2, int seq);

  @Override
  public void reset() {
  }

  private static long computeAddress(MappedByteBuffer b, int pos) {
    try {
      int addressSize = 1;// UnsafeUtil.unsafe().addressSize();
      return getAddress(b) + pos / addressSize;
    }
    catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  public void signal(int seq) {
    System.out.println("Signaling "+seq);
    signal0(futAddr);
  }

  public void await() {
    await0(futAddr);
  }

  private static native long getAddress(MappedByteBuffer b);

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
