package jocket.impl;

import java.io.OutputStream;

import jocket.wait.BusyYieldSleep;
import jocket.wait.WaitStrategy;

public class JocketOutputStream extends OutputStream {

  private final JocketWriter writer;
  private final WaitStrategy wait;

  public JocketOutputStream(JocketWriter writer, WaitStrategy wait) {
    this.writer = writer;
    this.wait = wait;
  }

  public JocketOutputStream(JocketWriter writer) {
    this(writer, new BusyYieldSleep());
  }

  @Override
  public void write(int b) {
    write(new byte[] { (byte) b }, 0, 1);
  }

  @Override
  public void write(byte[] b, int off, int len) {
    for (;;) {
      int written = writer.write(b, off, len);
      len -= written;
      off += written;
      if (len > 0)
        wait.pause();
      else
        break;
    }
    wait.reset();
  }

  @Override
  public void flush() {
    writer.flush();
  }

  @Override
  public void close() {
    writer.close();
  }
}
