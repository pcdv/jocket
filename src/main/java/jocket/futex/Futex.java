package jocket.futex;

import java.lang.reflect.Field;
import java.nio.MappedByteBuffer;

import sun.misc.Unsafe;

public class Futex {

  private static boolean libAvailable;

  private final long addr;

  private static final Unsafe UNSAFE = initUnsafe();

  static {
    // TODO: load from classpath
    try {
      System.loadLibrary("JocketFutex");
      libAvailable = true;
    }
    catch (UnsatisfiedLinkError e) {
      libAvailable = false;
    }
  }

  public static boolean isAvailable() {
    return libAvailable;
  }

  public Futex(MappedByteBuffer b, int pos) {
    this.addr = computeAddress(b, pos);
  }

  private static Unsafe initUnsafe() {
    try {
      Field f = Unsafe.class.getDeclaredField("theUnsafe");
      f.setAccessible(true);
      Unsafe unsafe = (Unsafe) f.get(null);
      return unsafe;
    }
    catch (Exception ex) {
      throw new RuntimeException(ex);
    }
  }

  private static long computeAddress(MappedByteBuffer b, int pos) {
    try {
      return getAddress(b) + pos / UNSAFE.addressSize();
    }
    catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  private static native long getAddress(MappedByteBuffer b);

  public void signal() {
    signal0(addr);
  }

  public void await() {
    await0(addr);
  }

  private static native void signal0(long addr);

  private static native void await0(long addr);

}
