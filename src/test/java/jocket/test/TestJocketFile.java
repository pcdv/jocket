package jocket.test;

import static org.junit.jupiter.api.Assertions.*;

import java.io.File;

import jocket.impl.ClosedException;
import jocket.impl.JocketReader;
import jocket.impl.JocketWriter;
import jocket.net.JocketFile;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

/**
 * Tests on exchange files, i.e. with mapped byte buffers.
 */
public class TestJocketFile {

  /**
   * Reading again after the end of stream used to read the unmapped buffer.
   */
  @Test
  @Disabled("crashes the JVM: read() accesses the buffer after unmapping it")
  public void testReadAfterEndOfStream() throws Exception {
    JocketFile srv = new JocketFile(16, 1024);
    JocketFile cli = new JocketFile(new File(srv.getPath()));
    srv.writer().close();
    assertEquals(-1, cli.reader().read(new byte[8]));
    assertEquals(-1, cli.reader().read(new byte[8]));
  }

  /**
   * Writing again after the peer has closed used to read the unmapped buffer.
   */
  @Test
  @Disabled("crashes the JVM: write() accesses the buffer after unmapping it")
  public void testWriteAfterPeerClosed() throws Exception {
    JocketFile srv = new JocketFile(16, 1024);
    JocketFile cli = new JocketFile(new File(srv.getPath()));
    cli.reader().close();
    assertThrows(ClosedException.class, () -> srv.writer().write(new byte[1], 0, 1));
    assertThrows(ClosedException.class, () -> srv.writer().write(new byte[1], 0, 1));
  }

  /**
   * The reader and the writer of a JocketFile, used by two threads of the same
   * process, used to share the position of a same ByteBuffer.
   */
  @Test
  @Disabled("the reader and the writer share the position of the same ByteBuffer")
  public void testReaderAndWriterInTwoThreads() throws Exception {
    JocketFile jf = new JocketFile(1024, 1 << 16);
    int count = 1_000_000;
    Thread writer = new Thread(() -> writeInts(jf.writer(), count), "writer");
    writer.setDaemon(true);
    writer.start();

    JocketReader r = jf.reader();
    byte[] b = new byte[4];
    for (int i = 0; i < count; i++) {
      int len = 0;
      while (len < 4)
        len += r.read(b, len, 4 - len);
      assertEquals(i, toInt(b), "message " + i);
    }
  }

  private static void writeInts(JocketWriter w, int count) {
    byte[] b = new byte[4];
    for (int i = 0; i < count; i++) {
      b[0] = (byte) i;
      b[1] = (byte) (i >> 8);
      b[2] = (byte) (i >> 16);
      b[3] = (byte) (i >> 24);
      int len = 0;
      while (len < 4)
        len += w.write(b, len, 4 - len);
      w.flush();
    }
  }

  private static int toInt(byte[] b) {
    return (b[0] & 0xff) | (b[1] & 0xff) << 8 | (b[2] & 0xff) << 16 | (b[3] & 0xff) << 24;
  }
}
