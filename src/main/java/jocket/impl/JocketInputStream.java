package jocket.impl;

import java.io.IOException;
import java.io.InputStream;
import java.util.Objects;

public class JocketInputStream extends InputStream {

  private final JocketReader reader;

  public JocketInputStream(JocketReader reader) {
    this.reader = reader;
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
    Objects.checkFromIndexSize(off, len, b.length);
    return reader.readBlocking(b, off, len);
  }

  @Override
  public void close() throws IOException {
    reader.close();
  }
}
