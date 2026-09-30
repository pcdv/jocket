package jocket.test;

import static org.junit.jupiter.api.Assertions.*;

import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;

import jocket.impl.ClosedException;
import jocket.impl.JocketReader;
import jocket.impl.JocketWriter;
import jocket.net.JocketFile;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/**
 * Tests on exchange files, i.e. with mapped byte buffers.
 */
public class TestJocketFile {

  private final List<JocketFile> files = new ArrayList<>();

  /**
   * Closes the files, and their reader and writer: a created file is deleted
   * once unmapped.
   */
  @AfterEach
  public void tearDown() {
    for (JocketFile f : files) {
      close(f::reader);
      close(f::writer);
      f.close();
    }
  }

  private static void close(Supplier<? extends Closeable> role) {
    try {
      role.get().close();
    }
    catch (IllegalStateException | IOException e) {
      // the file was closed before this role was obtained: nothing to close
    }
  }

  private JocketFile create(int maxPackets, int capacity) throws IOException {
    JocketFile f = new JocketFile(maxPackets, capacity);
    files.add(f);
    return f;
  }

  private JocketFile open(JocketFile created) throws IOException {
    JocketFile f = new JocketFile(new File(created.getPath()));
    files.add(f);
    return f;
  }

  /**
   * Reading again after the end of stream used to read the unmapped buffer.
   */
  @Test
  public void testReadAfterEndOfStream() throws Exception {
    JocketFile srv = create(16, 1024);
    JocketFile cli = open(srv);
    srv.writer().close();
    assertEquals(-1, cli.reader().read(new byte[8]));
    assertEquals(-1, cli.reader().read(new byte[8]));
  }

  /**
   * Writing again after the peer has closed used to read the unmapped buffer.
   */
  @Test
  public void testWriteAfterPeerClosed() throws Exception {
    JocketFile srv = create(16, 1024);
    JocketFile cli = open(srv);
    cli.reader().close();
    assertThrows(ClosedException.class, () -> srv.writer().write(new byte[1], 0, 1));
    assertThrows(ClosedException.class, () -> srv.writer().write(new byte[1], 0, 1));
  }

  /**
   * The reader and the writer of a JocketFile, used by two threads of the same
   * process, used to share the position of a same ByteBuffer.
   */
  @Test
  public void testReaderAndWriterInTwoThreads() throws Exception {
    JocketFile jf = create(1024, 1 << 16);
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

  /**
   * The writer used to announce its closing by overwriting its sequence
   * number: the packets the reader had not seen yet were lost.
   */
  @Test
  public void testPacketsFlushedBeforeCloseAreRead() throws Exception {
    JocketFile srv = create(16, 1024);
    JocketFile cli = open(srv);
    JocketWriter w = srv.writer();
    w.write("abc".getBytes(), 0, 3);
    w.flush();
    w.write("def".getBytes(), 0, 3);
    w.flush();
    w.close();

    byte[] b = new byte[8];
    JocketReader r = cli.reader();
    assertEquals(3, r.read(b));
    assertEquals(3, r.read(b));
    assertEquals(-1, r.read(b));
  }

  /**
   * close() called while another thread is reading used to unmap the buffer
   * under that thread's feet.
   */
  @Test
  public void testCloseWhileReading() throws Exception {
    JocketFile srv = create(16, 1024);
    JocketFile cli = open(srv);
    srv.writer(); // keeps the writer open
    JocketReader r = cli.reader();
    cli.close(); // as JocketSocket does: closing the reader unmaps the buffer

    FutureTask<Integer> read = new FutureTask<>(() -> r.readBlocking(new byte[8], 0, 8));
    Thread reader = new Thread(read, "reader");
    reader.setDaemon(true);
    reader.start();
    Thread.sleep(100); // lets the reader wait for data

    r.close();
    assertEquals(-1, read.get(5, TimeUnit.SECONDS));
  }

  /**
   * Same as above, while data flows: the reader is then most of the time
   * copying data, which crashes the JVM if the buffer is unmapped meanwhile.
   */
  @Test
  public void testCloseWhileDataFlows() throws Exception {
    for (int iter = 0; iter < 50; iter++) {
      JocketFile srv = create(64, 1 << 16);
      JocketFile cli = open(srv);
      JocketWriter w = srv.writer();
      JocketReader r = cli.reader();
      // as JocketSocket does: closing the reader (or writer) unmaps its buffer
      srv.close();
      cli.close();

      Thread writer = new Thread(() -> {
        byte[] b = new byte[1000];
        try {
          while (true) {
            w.write(b, 0, b.length);
            w.flush();
          }
        }
        catch (ClosedException e) {
          // the reader is closed
        }
      }, "writer");
      writer.setDaemon(true);
      writer.start();

      FutureTask<Integer> read = new FutureTask<>(() -> {
        byte[] b = new byte[1000];
        while (r.read(b) >= 0)
          ;
        return -1;
      });
      Thread reader = new Thread(read, "reader");
      reader.setDaemon(true);
      reader.start();

      Thread.sleep(10);
      r.close();
      assertEquals(-1, read.get(5, TimeUnit.SECONDS));
      writer.join(5000);
      assertFalse(writer.isAlive(), "writer still running");
      w.close();
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
