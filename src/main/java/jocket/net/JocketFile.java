package jocket.net;

import java.io.Closeable;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteOrder;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.FileChannel.MapMode;
import java.util.Random;

import jocket.impl.Const;
import jocket.impl.JocketReader;
import jocket.impl.JocketWriter;
import jocket.impl.MappedBuffers;

/**
 * Creates or opens an exchange file wrapped in a mapped byte buffer and makes
 * it easy to obtain a JocketReader or JocketWriter instance.
 * <p>
 * The reader and the writer share the mapping, which is unmapped once this
 * JocketFile, and the reader and writer obtained from it, are all closed.
 *
 * @author pcdv
 */
public class JocketFile implements Const, Closeable {

  private final MappedByteBuffer buf;

  private final RandomAccessFile io;

  private final int maxPackets;

  private final File file;

  private JocketReader reader;

  private JocketWriter writer;

  /**
   * Number of users of the mapping: this JocketFile until closed, and its
   * reader and writer until closed.
   */
  private int refs = 1;

  private boolean closed;

  /**
   * Creates a new exchange file and associated reader and writer.
   */
  public JocketFile(int maxPackets, int capacity) throws IOException {
    this(createTempFile(), true, maxPackets, capacity);
  }

  /**
   * Opens and wrap specified exchange file with a reader and writer.
   */
  public JocketFile(File file) throws IOException {
    this(file, false, -1, -1);
  }

  private JocketFile(File file, boolean create, int maxPackets, int capacity) throws IOException {
    if (!create && !file.exists())
      throw new FileNotFoundException("File does not exist");

    this.file = file;
    this.io = new RandomAccessFile(file, "rw");

    if (create) {
      int size = capacity + PACKET_INFO + maxPackets * LEN_PACKET_INFO;
      io.setLength(0);

      // append data instead of just setting the size: in case we are using
      // a real filesystem, this could avoid getting a fragmented file
      // (or not)
      for (int i = 0; i < size; i += 1024) {
        io.write(new byte[1024]);
      }
      io.setLength(size);
    }

    FileChannel channel = io.getChannel();
    buf = channel.map(MapMode.READ_WRITE, 0, io.length());
    buf.order(ByteOrder.nativeOrder());
    buf.load();
    channel.close();

    if (create) {
      buf.putInt(META_MAX_PACKETS, maxPackets);
      buf.putInt(META_CAPACITY, capacity);
      buf.force();
    }
    else {
      maxPackets = buf.getInt(META_MAX_PACKETS);
    }

    this.maxPackets = maxPackets;

    file.deleteOnExit();
  }

  /**
   * Returns the reader of this file, created on first call.
   */
  public synchronized JocketReader reader() {
    if (reader == null) {
      checkOpen();
      refs++;
      reader = new JocketReader(buf, maxPackets, this::release);
    }
    return reader;
  }

  /**
   * Returns the writer of this file, created on first call.
   */
  public synchronized JocketWriter writer() {
    if (writer == null) {
      checkOpen();
      refs++;
      writer = new JocketWriter(buf, maxPackets, this::release);
    }
    return writer;
  }

  private void checkOpen() {
    if (closed)
      throw new IllegalStateException("Closed");
  }

  /**
   * Releases this JocketFile's hold on the mapping. The reader and writer
   * already obtained remain usable until they are closed.
   */
  @Override
  public synchronized void close() {
    if (!closed) {
      closed = true;
      release();
    }
  }

  private synchronized void release() {
    if (--refs == 0)
      MappedBuffers.unmap(buf);
  }

  public String getPath() {
    return file.getAbsolutePath();
  }

  /**
   * Deletes the file to make it harder to sniff stream. Can be called (at
   * least under linux) after both endpoints have opened the file.
   */
  public void deleteFile() {
    file.delete();
  }

  private static File createTempFile() throws IOException {
    try {
      // under linux, try to use tmpfs
      File dir = new File("/dev/shm");
      if (dir.exists()) {
        File file =
          new File(dir, "jocket-" + new Random().nextInt(Integer.MAX_VALUE));
        new FileOutputStream(file).close();
        return file;
      }
    }
    catch (Exception ex) {
    }
    System.err.println("Could not create file in /dev/shm");
    return File.createTempFile("jocket", "");
  }

  /**
   * For test purposes. Must not be accessed once the mapping is released.
   */
  public MappedByteBuffer getBuffer() {
    return buf;
  }
}
