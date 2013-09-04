package jocket.net;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.RandomAccessFile;
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
public class JocketFile {

  private final MappedByteBuffer buf;
  private final RandomAccessFile io;
  private final JocketReader reader;
  private final JocketWriter writer;
  private final int npackets;
  private final int size;
  private final File file;

  public JocketFile() throws IOException {
    this(createTempFile(), true);
    file.deleteOnExit();
  }

  private static File createTempFile() throws IOException {
    try {
      // under linux, try to use tmpfs
      File dir = new File("/dev/shm");
      return new File(dir, "jocket-" + new Random().nextInt(Integer.MAX_VALUE));
    } catch (Exception ex) {
      return File.createTempFile("jocket", "");
    }
  }

  public JocketFile(File file, boolean create) throws IOException {
    if (!create && !file.exists())
      throw new FileNotFoundException("File does not exist");

    this.file = file;
    this.io = new RandomAccessFile(file, "rw");

    // TODO: this information should be configurable (in create mode) or
    // present in file (in !create mode)
    this.npackets = 1024;
    this.size = 1024 * 1024 + Const.PACKET_INFO + npackets
        * Const.LEN_PACKET_INFO;

    if (create) {
      io.setLength(0);
      io.setLength(size);
    }

    FileChannel channel = io.getChannel();
    this.buf = channel.map(MapMode.READ_WRITE, 0, size);
    buf.load();
    channel.close();
    this.reader = new JocketReader(buf, npackets);
    this.writer = new JocketWriter(buf, npackets);
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
   * Deletes the file to make it harder to sniff stream. Can be called (at least
   * under linux) after both endpoints have opened the file.
   */
  public void deleteFile() {
    file.delete();
  }
}
