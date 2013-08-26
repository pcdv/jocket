package jocket.impl;

import java.io.IOException;
import java.io.InputStream;

import jocket.wait.BusyYieldSleep;
import jocket.wait.WaitStrategy;

public class JocketInputStream extends InputStream {

  private final JocketReader reader;
  private final WaitStrategy wait;

  public JocketInputStream(JocketReader reader) {
    this(reader, new BusyYieldSleep());
  }

  public JocketInputStream(JocketReader reader, WaitStrategy wait) {
    this.reader = reader;
    this.wait = wait;
  }

  @Override
  public int read() {
    byte[] buf = new byte[1];
    int len;
    do {
      len = read(buf, 0, 1);
    } while (len == 0);
    return len < 0 ? len : buf[0] & 0xff;
  }

  @Override
  public int read(byte[] b, int off, int len) {
    int read = 0;
    for (;;) {
      read = reader.read(b, off, len);
      if (read != 0) {
        wait.reset();
        break;
      } else
        wait.pause();
    }
    return read;
  }

  @Override
  public void close() throws IOException {
    reader.close();
  }
}
