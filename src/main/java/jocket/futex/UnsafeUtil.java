package jocket.futex;

import java.lang.reflect.Field;

import sun.misc.Unsafe;

public class UnsafeUtil {

  private static final Unsafe UNSAFE = initUnsafe();

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

  public static Unsafe unsafe() {
    return UNSAFE;
  }
}
