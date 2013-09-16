package jocket.futex;

import java.io.EOFException;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.Field;
import java.nio.MappedByteBuffer;

import sun.misc.Unsafe;

public class Futex {

  private static boolean libAvailable;

  private final long addr;

  private static final Unsafe UNSAFE = initUnsafe();

  private static final int BITS = guessBits();

  static {
    // TODO: load from classpath
    try {
      loadLibrary("JocketFutex" + BITS);
      libAvailable = true;
    } catch (Throwable e) {
      libAvailable = false;
      System.err.println("WARN: JNI futex lib is NOT available: " + e);
      e.printStackTrace();
    }
  }

  public static boolean isAvailable() {
    return libAvailable;
  }

  private static void loadLibrary(String lib) throws IOException {
    File f = File.createTempFile("jni", ".so");
    try {
      String path = "lib" + lib + ".so";
      InputStream in = Futex.class.getClassLoader().getResourceAsStream(path);
      if (in == null)
        throw new FileNotFoundException(path);
      copy(in, new FileOutputStream(f));
      System.load(f.getAbsolutePath());
    } finally {
      f.delete();
    }
  }

  private static void copy(InputStream in, OutputStream out) throws IOException {
    byte[] buf = new byte[8192];
    try {
      while (true) {
        int len = in.read(buf);
        if (len < 0)
          break;
        out.write(buf, 0, len);
      }
    } catch (EOFException eof) {
    } catch (IOException e) {
      throw e;
    }
    out.close();
  }

  private static int guessBits() {
    String s = System.getProperty("os.arch");
    return s.contains("64") ? 64 : 32;
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
    } catch (Exception ex) {
      throw new RuntimeException(ex);
    }
  }

  private static long computeAddress(MappedByteBuffer b, int pos) {
    try {
      return getAddress(b) + pos / UNSAFE.addressSize();
    } catch (Exception e) {
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
