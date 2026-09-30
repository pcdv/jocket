package jocket.test;

import static org.junit.jupiter.api.Assertions.*;

import java.io.EOFException;
import java.lang.reflect.Field;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

import jocket.impl.Const;
import jocket.impl.JocketReader;
import jocket.impl.JocketWriter;

public abstract class AbstractJocketTest {

  private final int readBufSize = 8192;

  protected JocketWriter w;
  protected JocketReader r;
  protected ByteBuffer shared;

  protected void init(int npackets, int capacity) {
    // allows to run tests several times without resetting the socket
    if (w == null) {
      // the header is accessed in native order, as in exchange files
      shared = ByteBuffer.allocate(Const.PACKET_INFO + npackets
          * Const.LEN_PACKET_INFO + capacity).order(ByteOrder.nativeOrder());
      w = new JocketWriter(shared, npackets);
      // same memory, but its own position and limit, as between two processes
      r = new JocketReader(shared.duplicate(), npackets);
      w.setAlign(0);
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

  protected void flush() {
    w.flush();
  }

  protected void write(int... bytes) {
    for (int len : bytes) {
      w.write(new byte[len], 0, len);
    }
  }

  protected void read(int... bytes) {
    for (int len : bytes) {
      assertEquals(len, r.read(new byte[len], 0, len));
    }
  }

  public void read(String... str) throws EOFException {
    for (String s : str) {
      assertEquals(s, read());
    }
  }

  protected String read() throws EOFException {
    byte[] buf = new byte[readBufSize];
    return read(buf, 0, buf.length);
  }

  protected void read(int bytes, String expect) throws EOFException {
    byte[] buf = new byte[bytes];
    assertEquals(expect, read(buf, 0, bytes));
  }

  protected String read(byte[] buf, int off, int len) throws EOFException {
    return new String(buf, off, r.read(buf, off, len));
  }

  /**
   * Moves the writer and the reader to specified sequence number, as if that
   * many packets had been exchanged.
   */
  protected void setSeqNums(long seq) throws ReflectiveOperationException {
    setField(w, "wseq", seq);
    setField(r, "rseq", seq);
    setField(r, "wseq", seq);
    shared.putLong(Const.WSEQ, seq);
    shared.putLong(Const.RSEQ, seq);
  }

  protected static void setField(Object o, String name, long value)
      throws ReflectiveOperationException {
    Field f = o.getClass().getDeclaredField(name);
    f.setAccessible(true);
    f.setLong(o, value);
  }
}
