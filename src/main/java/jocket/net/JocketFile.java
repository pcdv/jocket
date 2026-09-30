package jocket.net;

import static java.nio.file.StandardOpenOption.READ;
import static java.nio.file.StandardOpenOption.WRITE;

import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.FileChannel.MapMode;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.Paths;

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

  private static final String PREFIX = "jocket-";

  /**
   * Directory of exchange files: /dev/shm (tmpfs, never written to disk) under
   * Linux, the temporary directory elsewhere. Can be set with -Djocket.dir, to
   * the same value for the server and its clients.
   */
  private static final Path DIR = directory();

  private final MappedByteBuffer buf;

  private final int maxPackets;

  private final Path path;

  /**
   * True if the file was created by this JocketFile: it is deleted when
   * unmapped.
   */
  private final boolean created;

  private JocketReader reader;

  private JocketWriter writer;

  /**
   * Number of users of the mapping: this JocketFile until closed, and its
   * reader and writer until closed.
   */
  private int refs = 1;

  private boolean closed;

  /**
   * True if deleteFile() failed, because the file is mapped (Windows): it is
   * deleted when unmapped.
   */
  private boolean deletePending;

  /**
   * Creates a new exchange file, accessible only to its owner.
   */
  public JocketFile(int maxPackets, int capacity) throws IOException {
    if (Integer.bitCount(maxPackets) != 1)
      throw new IllegalArgumentException("Max packets must be a power of 2");
    if (Integer.bitCount(capacity) != 1)
      throw new IllegalArgumentException("Capacity must be a power of 2");
    long size = fileSize(maxPackets, capacity);
    if (size > Integer.MAX_VALUE)
      throw new IllegalArgumentException("Exchange file too big: " + size + " bytes");

    // created atomically, with a random name, and rw------- on POSIX systems
    this.path = Files.createTempFile(DIR, PREFIX, null);
    this.created = true;
    this.maxPackets = maxPackets;

    try (FileChannel channel = FileChannel.open(path, READ, WRITE)) {
      // writes zeros rather than just setting the size, so that memory is
      // allocated now rather than when data is first written
      ByteBuffer zeros = ByteBuffer.allocate(64 * 1024);
      for (long pos = 0; pos < size; pos += zeros.limit()) {
        zeros.clear().limit((int) Math.min(zeros.capacity(), size - pos));
        while (zeros.hasRemaining())
          channel.write(zeros);
      }
      buf = map(channel, size);
    }
    catch (IOException | RuntimeException e) {
      Files.deleteIfExists(path);
      throw e;
    }

    buf.putInt(META_MAX_PACKETS, maxPackets);
    buf.putInt(META_CAPACITY, capacity);
  }

  /**
   * Opens and wrap specified exchange file with a reader and writer.
   */
  public JocketFile(File file) throws IOException {
    this(file.toPath());
  }

  private JocketFile(Path path) throws IOException {
    this.path = path;
    this.created = false;

    try (FileChannel channel = FileChannel.open(path, READ, WRITE, LinkOption.NOFOLLOW_LINKS)) {
      long size = channel.size();
      if (size < PACKET_INFO || size > Integer.MAX_VALUE)
        throw new IOException("Not an exchange file: " + path);
      buf = map(channel, size);
    }

    maxPackets = buf.getInt(META_MAX_PACKETS);
    int capacity = buf.getInt(META_CAPACITY);
    if (Integer.bitCount(maxPackets) != 1 || Integer.bitCount(capacity) != 1
        || fileSize(maxPackets, capacity) != buf.capacity()) {
      MappedBuffers.unmap(buf);
      throw new IOException("Not an exchange file: " + path);
    }
  }

  /**
   * Opens an exchange file whose path was received from the server during the
   * handshake, provided it is one: a regular file of the exchange directory,
   * named like the files servers create. Otherwise, any process listening on
   * the port could make the client write into (and delete) any file it can
   * access.
   */
  static JocketFile openReceived(String name) throws IOException {
    Path path;
    try {
      path = Paths.get(name);
    }
    catch (InvalidPathException e) {
      throw new IOException("Not an exchange file: " + name, e);
    }
    Path parent = path.getParent();
    if (!path.isAbsolute() || parent == null
        || !parent.toRealPath().equals(DIR.toRealPath())
        || !path.getFileName().toString().startsWith(PREFIX)
        || !Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS))
      throw new IOException("Not an exchange file: " + name);

    // NB: opened without following symbolic links, in case the file has been
    // replaced by one since checked
    return new JocketFile(path);
  }

  private static MappedByteBuffer map(FileChannel channel, long size) throws IOException {
    MappedByteBuffer buf = channel.map(MapMode.READ_WRITE, 0, size);
    buf.order(ByteOrder.nativeOrder());
    buf.load();
    return buf;
  }

  private static long fileSize(int maxPackets, int capacity) {
    return (long) capacity + PACKET_INFO + (long) maxPackets * LEN_PACKET_INFO;
  }

  private static Path directory() {
    String dir = System.getProperty("jocket.dir");
    if (dir != null)
      return Paths.get(dir);
    Path shm = Paths.get("/dev/shm");
    return Files.isDirectory(shm) ? shm : Paths.get(System.getProperty("java.io.tmpdir"));
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
    if (--refs == 0) {
      MappedBuffers.unmap(buf);
      if (created || deletePending)
        deleteFile();
    }
  }

  public String getPath() {
    return path.toAbsolutePath().toString();
  }

  /**
   * Deletes the file, which makes it harder to sniff the stream and frees its
   * memory once unmapped. Can be called as soon as both endpoints have opened
   * it. Where a mapped file cannot be deleted (Windows), it is deleted when
   * unmapped.
   */
  public synchronized void deleteFile() {
    try {
      Files.deleteIfExists(path);
      deletePending = false;
    }
    catch (IOException e) {
      deletePending = true;
    }
  }

  /**
   * For test purposes. Must not be accessed once the mapping is released.
   */
  public MappedByteBuffer getBuffer() {
    return buf;
  }
}
