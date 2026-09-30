package jocket.futex;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Locale;

/**
 * A tool that extracts a shared library from a jar and loads it so that the
 * user does not have to mess with LD_LIBRARY_PATH.
 *
 * @author pcdv
 */
public class LibLoader {

  /**
   * Finds specified lib in classpath, in the directory of the current platform,
   * and loads it. E.g. under Linux on x86-64, library "foo" is searched as
   * "native/linux-x86_64/libfoo.so".
   *
   * @param lib the name of the library, without "lib" prefix or .so/.dll
   *        extension
   * @throws IOException
   */
  public static void loadLibrary(String lib) throws IOException {
    String path = "native/" + platform() + "/" + System.mapLibraryName(lib);
    try (InputStream in = LibLoader.class.getClassLoader().getResourceAsStream(path)) {
      if (in == null)
        throw new FileNotFoundException(path);
      Path file = Files.createTempFile("jni" + lib, System.mapLibraryName(""));
      try {
        Files.copy(in, file, StandardCopyOption.REPLACE_EXISTING);
        System.load(file.toAbsolutePath().toString());
      }
      finally {
        // can be deleted once loaded, at least under Linux
        Files.deleteIfExists(file);
      }
    }
  }

  /**
   * Returns the name of the current platform, e.g. "linux-x86_64" or
   * "linux-aarch64". NB: the build names the directory of the library it
   * compiles the same way.
   */
  static String platform() {
    String os = System.getProperty("os.name").toLowerCase(Locale.ROOT);
    if (os.startsWith("linux"))
      os = "linux";
    String arch = switch (System.getProperty("os.arch")) {
      case "amd64", "x86_64" -> "x86_64";
      case "aarch64", "arm64" -> "aarch64";
      default -> System.getProperty("os.arch");
    };
    return os.replace(' ', '_') + "-" + arch;
  }
}
