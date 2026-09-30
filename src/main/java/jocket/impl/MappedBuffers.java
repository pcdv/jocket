package jocket.impl;

import java.lang.reflect.Field;
import java.nio.ByteBuffer;

import sun.misc.Unsafe;

/**
 * Unmaps mapped byte buffers without waiting for the garbage collector, which
 * may keep the memory of a deleted exchange file allocated for a long time.
 * <p>
 * There is no public API for this before Java 22 (Arena and MemorySegment):
 * Unsafe.invokeCleaner() is the only way, and it is deprecated for removal
 * since Java 23. It is confined to this class.
 */
public final class MappedBuffers {

  private static final Unsafe UNSAFE = initUnsafe();

  private MappedBuffers() {
  }

  /**
   * Unmaps specified buffer, if it is a direct one. It must not be accessed
   * afterwards: the JVM would crash.
   */
  public static void unmap(ByteBuffer buf) {
    if (buf.isDirect())
      UNSAFE.invokeCleaner(buf);
  }

  private static Unsafe initUnsafe() {
    try {
      Field f = Unsafe.class.getDeclaredField("theUnsafe");
      f.setAccessible(true);
      return (Unsafe) f.get(null);
    }
    catch (ReflectiveOperationException ex) {
      throw new ExceptionInInitializerError(ex);
    }
  }
}
