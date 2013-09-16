package jocket.sample;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

public class WriteLoop {

  public WriteLoop(OutputStream writer, InputStream in) throws IOException {
    byte[] buf = new byte[1024];
    while (true) {
      final int read = in.read(buf);
      if (read == -1) {
        System.out.println("Closing...");
        writer.close();
        return;
      }
      writer.write(buf, 0, read);
      writer.flush();
    }
  }
}
