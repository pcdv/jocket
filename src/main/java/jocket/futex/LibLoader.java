package jocket.futex;

import java.io.EOFException;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * A tool that extracts a shared library from a jar and loads it so that the
 * user does not have to mess with LD_LIBRARY_PATH.
 *
 * @author pcdv
 */
public class LibLoader {

  private static final int BITS = guessBits();

  /**
   * Finds specified lib in classpath and loads it. Note that the lib's name
   * will be suffixed by 32 or 64 according to the current platform. Eg. under
   * linux32, library "foo" will be searched as "libfoo32.so".
   *
   * @param lib the name of the library, without "lib" prefix or .so/.dll
   *        extension
   * @throws IOException
   */
  public static void loadLibrary(String lib) throws IOException {
    File f = File.createTempFile("jni" + lib, ".so");
    try {
      String path = "lib" + lib + BITS + ".so";
      InputStream in = Futex.class.getClassLoader().getResourceAsStream(path);
      if (in == null)
        throw new FileNotFoundException(path);
      copy(in, new FileOutputStream(f));
      System.load(f.getAbsolutePath());
    }
    finally {
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
    }
    catch (EOFException eof) {
    }
    catch (IOException e) {
      throw e;
    }
    out.close();
  }

  private static int guessBits() {
    return System.getProperty("os.arch").contains("64") ? 64 : 32;
  }
}
