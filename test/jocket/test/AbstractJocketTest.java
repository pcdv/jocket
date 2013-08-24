package jocket.test;

import java.io.EOFException;
import java.nio.ByteBuffer;

import jocket.impl.Const;
import jocket.impl.JocketReader;
import jocket.impl.JocketWriter;

public class AbstractJocketTest {

  private final int readBufSize = 8192;

  protected JocketWriter w;
  protected JocketReader r;

  protected void init(int npackets, int capacity) {
    // allows to run tests several times without resetting the socket
    if (w == null) {
      ByteBuffer buf = ByteBuffer.allocate(Const.PACKET_INFO + npackets
          * Const.LEN_PACKET_INFO + capacity);
      w = new JocketWriter(buf, npackets);
      r = new JocketReader(buf, npackets);
    }
  }

  /**
   * Writes strings, flushing after each of them.
   * 
   * @return number of bytes successfully written
   */
  protected int write(String... strs) {
    return write(true, strs);
  }

  /**
   * Writes strings without flushing.
   * 
   * @return number of bytes successfully written
   */
  protected int write0(String... strs) {
    return write(false, strs);
  }

  protected int write(boolean flush, String... strs) {
    int total = 0;
    for (String s : strs) {
      total += w.write(s.getBytes(), 0, s.length());
      if (flush)
        w.flush();
    }
    return total;
  }

  protected String read() throws EOFException {
    byte[] buf = new byte[readBufSize];
    return read(buf, 0, buf.length);
  }

  protected String read(byte[] buf, int off, int len) throws EOFException {
    return new String(buf, off, r.read(buf, off, len));
  }
}
