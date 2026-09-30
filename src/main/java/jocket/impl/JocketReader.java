package jocket.impl;

import java.nio.ByteBuffer;

import jocket.futex.Futex;
import jocket.wait.BusyYieldSleep;
import jocket.wait.WaitStrategy;

public class JocketReader extends AbstractJocketBuffer {

  /**
   * Number of checks for new data before sleeping on the futex.
   */
  private static final int SPIN = 1024;

  /**
   * Maximum time spent sleeping on the futex before checking again, in case
   * nobody signals it.
   */
  private static final long FUTEX_TIMEOUT = 100_000_000;

  private long rseq;

  private long wseq;

  private WaitStrategy waiter = new BusyYieldSleep();

  private Futex futex;

  /**
   * True once the writer's process is known to be dead: it is handled as if
   * the writer was closed.
   */
  private boolean writerDied;

  private ByteBuffer currentPacket;

  /**
   * The buffer is unmapped (if it is a mapped one) when closed.
   */
  public JocketReader(ByteBuffer buf, int npackets) {
    this(buf, npackets, () -> MappedBuffers.unmap(buf));
  }

  /**
   * @param release called once the buffer is not accessed anymore, after
   *        close()
   */
  public JocketReader(ByteBuffer buf, int npackets, Runnable release) {
    super(buf, npackets, release);
    publishPid(RPID);
  }

  @Override
  protected void close0() {
    setIntRelease(RCLOSED, 1);
    // wakes up the thread reading, if it waits on the futex
    if (futex != null)
      futex.signal();
  }

  public int read(byte[] data) {
    return read(data, 0, data.length);
  }

  /**
   * Reads data if available. Returns the number of bytes read (possibly 0), or
   * -1 if closed.
   */
  public int read(byte[] data, int off, int len) {
    if (!enter())
      return -1;
    int res;
    try {
      res = read0(data, off, len);
      if (res == 0 && peerDied(WPID)) {
        writerDied = true;
        res = read0(data, off, len);
      }
    }
    finally {
      exit();
    }
    if (res < 0)
      close();
    return res;
  }

  /**
   * Waits until data is available and reads it. Returns the number of bytes
   * read, or -1 if closed.
   */
  public int readBlocking(byte[] data, int off, int len) {
    if (len == 0)
      return 0;
    if (!enter())
      return -1;
    int res;
    try {
      while ((res = read0(data, off, len)) == 0 && !isClosed()) {
        if (pause())
          writerDied = true;
      }
    }
    finally {
      exit();
    }
    if (res > 0)
      waiter.reset();
    else {
      res = -1; // closed by another thread
      close();
    }
    return res;
  }

  /**
   * Returns the number of bytes read, 0 if no data is available, -1 if the
   * writer is closed and all its data has been read.
   */
  private int read0(byte[] data, int off, int len) {
    if (!hasPacket())
      return writerClosed() ? -1 : 0;

    final int pktInfo = packetInfo(rseq);
    final long start = getLong(pktInfo);
    final int available = getInt(pktInfo + 8);
    final int pos = dataPos(start);

    // if the whole packet can be read
    if (available <= len) {
      buf.get(pos, data, off, available);
      setLongRelease(RSEQ, ++rseq);
      return available;
    }

    // if the packet can be read only partially
    buf.get(pos, data, off, len);
    // the writer looks at the start of the packet, to compute its free space
    setInt(pktInfo + 8, available - len);
    setLongRelease(pktInfo, start + len);
    return len;
  }

  /**
   * Returns true if a packet can be read. Reads the writer's sequence number
   * only when all known packets have been read.
   */
  private boolean hasPacket() {
    if (wseq > rseq)
      return true;
    wseq = getLongAcquire(WSEQ);
    return wseq > rseq;
  }

  /**
   * Returns true if the writer is closed (or dead) and all its packets have
   * been read.
   */
  private boolean writerClosed() {
    // the writer sets its flag after publishing its last packet: once the
    // flag is seen, wseq has its final value
    return (getIntAcquire(WCLOSED) != 0 || writerDied) && !hasPacket();
  }

  /**
   * Waits a little for new data. Returns true if the writer's process is dead.
   */
  private boolean pause() {
    if (futex == null) {
      waiter.pause();
      return peerDied(WPID);
    }
    for (int i = 0; i < SPIN; i++) {
      if (getLongAcquire(WSEQ) != wseq || getIntAcquire(WCLOSED) != 0 || isClosed())
        return false;
      Thread.onSpinWait();
    }
    futex.await(FUTEX_TIMEOUT);
    return checkPeer(WPID);
  }

  /**
   * EXPERIMENTAL (part of the zero-copy API). If a packet is available, return
   * a ByteBuffer wrapping the packet. The ByteBuffer should not be modified as
   * it points directly into the exchange file (when using a MappedByteBuffer),
   * and must not be used after close(). After use, {@link #release(ByteBuffer)}
   * MUST be called in order to notify the writer that the read area has become
   * available for writing.
   *
   * @throws ClosedException if closed
   */
  public ByteBuffer nextPacket() {
    if (!enter())
      throw new ClosedException("Closed");
    ByteBuffer packet = null;
    boolean closedByWriter = false;
    try {
      if (hasPacket()) {
        final int pktInfo = packetInfo(rseq);
        final int packetSize = getInt(pktInfo + 8);
        final int packetPos = dataPos(getLong(pktInfo));

        // NB: I'm not sure calling slice is cheaper than the array-copy it
        // avoids (except for big packets). An alternative would be to directly
        // return the main buffer (more dangerous because it allows to bork the
        // entire exchange file). Surely a reusable/mutable slice would be
        // better.
        packet = currentPacket = buf.slice(packetPos, packetSize).order(buf.order());
      }
      else {
        if (peerDied(WPID))
          writerDied = true;
        closedByWriter = writerClosed();
      }
    }
    finally {
      exit();
    }
    if (closedByWriter) {
      close();
      throw new ClosedException("Closed by writer");
    }
    return packet;
  }

  /**
   * EXPERIMENTAL (part of the zero-copy API). Releases the packet returned by
   * {@link #nextPacket()} after reading.
   *
   * @param packet
   */
  public void release(ByteBuffer packet) {
    if (currentPacket == null || currentPacket != packet)
      throw new IllegalArgumentException("Invalid packet");

    currentPacket = null;

    if (!enter())
      return;
    try {
      if (packet.remaining() == 0) {
        setLongRelease(RSEQ, ++rseq);
      }
      else {
        // same as a partial read()
        final int pktInfo = packetInfo(rseq);
        setInt(pktInfo + 8, packet.remaining());
        setLongRelease(pktInfo, getLong(pktInfo) + packet.position());
      }
    }
    finally {
      exit();
    }
  }

  /**
   * Returns how many bytes can be read (mainly for test purposes).
   */
  public int available() {
    if (!enter())
      return 0;
    try {
      long wseq = getLongAcquire(WSEQ);
      if (wseq <= rseq)
        return 0;

      int last = packetInfo(wseq - 1); // last packet written
      long start = getLong(packetInfo(rseq)); // first packet not read
      long end = getLong(last) + getInt(last + 8);
      return (int) (end - start);
    }
    finally {
      exit();
    }
  }

  /**
   * Waits for data on the futex written by a writer which has called
   * {@link JocketWriter#useFutex()}. The buffer must be a direct one.
   */
  public void useFutex() {
    this.futex = new Futex(buf, FUTEX);
  }

  public WaitStrategy getWaitStrategy() {
    return waiter;
  }

  /**
   * Sets the strategy used by {@link #readBlocking(byte[], int, int)} to wait
   * for data, when not waiting on the futex. It must not be shared with other
   * readers or writers.
   */
  public void setWaitStrategy(WaitStrategy waiter) {
    this.waiter = waiter;
  }
}
