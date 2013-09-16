package jocket.net;

import java.io.File;
import java.io.FileNotFoundException;
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

/**
 * Creates or opens an exchange file wrapped in a mapped byte buffer and makes
 * it easy to obtain a JocketReader or JocketWriter instance.
 *
 * @author pcdv
 */
public class JocketFile implements Const {

  private final MappedByteBuffer buf;

  private final RandomAccessFile io;

  private final JocketReader reader;

  private final JocketWriter writer;

  private final File file;

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

    reader = new JocketReader(buf, maxPackets);
    writer = new JocketWriter(buf, maxPackets);
    file.deleteOnExit();
  }

  public JocketReader reader() {
    return reader;
  }

  public JocketWriter writer() {
    return writer;
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
      return new File(dir, "jocket-" + new Random().nextInt(Integer.MAX_VALUE));
    }
    catch (Exception ex) {
      return File.createTempFile("jocket", "");
    }
  }
}
