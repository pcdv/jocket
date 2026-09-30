package jocket.impl;

import java.nio.ByteBuffer;

import jocket.futex.Futex;

public class JocketWriter extends AbstractJocketBuffer {

  /** The sequence number of the next packet to write. */
  private long wseq;

  /** Pending packet's absolute start and end positions. */
  private long pstart, pend;

  /**
   * Equivalent to (pend > pstart) but looks like it is faster to store this
   * information in a dedicated boolean.
   */
  private boolean dirty;

  /**
   * Optional packet alignment. By default we align on cache lines to avoid
   * having several packets in the same cache line, which would cause false
   * sharing (reader and writer threads would access the same line
   * concurrently).
   */
  private int align = _CACHELINE;

  /**
   * Used to modulo on align size.
   */
  private int alignMask = _CACHELINE - 1;

  private Futex futex;

  /**
   * True once the reader's process is known to be dead: it is handled as if
   * the reader was closed.
   */
  private boolean readerDied;

  private ByteBuffer currentPacket;

  /**
   * The buffer is unmapped (if it is a mapped one) when closed.
   */
  public JocketWriter(ByteBuffer buf, int npackets) {
    this(buf, npackets, () -> MappedBuffers.unmap(buf));
  }

  /**
   * @param release called once the buffer is not accessed anymore, after
   *        close()
   */
  public JocketWriter(ByteBuffer buf, int npackets, Runnable release) {
    super(buf, npackets, release);
    publishPid(WPID);
  }

  /**
   * Sets packet alignment.
   *
   * @param align must be either 0 or a power of 2, not above capacity
   */
  public void setAlign(int align) {
    if (align < 0 || align > 0 && Integer.bitCount(align) != 1 || align > capacity)
      throw new IllegalArgumentException("Invalid alignment: " + align);
    this.align = align;
    this.alignMask = align == 0 ? 0 : align - 1;
  }

  /**
   * Writes data into the exchange file and return the number of bytes written.
   * The call is non-blocking and returns 0 if data cannot be written.
   *
   * @param data a byte array
   * @param off the position in array of data that must be sent
   * @param len the number of bytes to write
   * @return the number of bytes written
   * @throws ClosedException if the socket is closed, or the reader's process
   *         is dead
   */
  public int write(final byte[] data, final int off, final int len) {
    if (!enter())
      throw new ClosedException("Closed");
    int res;
    try {
      res = write0(data, off, len);
    }
    finally {
      exit();
    }
    if (res < 0) {
      close();
      throw new ClosedException(readerDied ? "Reader is dead" : "Closed by reader");
    }
    return res;
  }

  /**
   * Returns the number of bytes written, or -1 if the reader is closed (or
   * dead).
   */
  private int write0(byte[] data, int off, int len) {
    if (getIntAcquire(RCLOSED) != 0)
      return -1;

    final long rseq = getLongAcquire(RSEQ);

    // TODO: implement anti-truncation mechanism (write at 0 if remaining
    // space is too small)
    len = canWrite(rseq) ? Math.min(getAvailableSpace(rseq), len) : 0;
    if (len <= 0) {
      if (peerDied(RPID)) {
        readerDied = true;
        return -1;
      }
      return 0;
    }

    dirty = true;
    buf.put(dataPos(pend), data, off, len);
    this.pend += len;

    // flush when reaching end of buffer, otherwise next write will
    // generate an inconsistent packet (overflowing the buffer)
    if ((this.pend & dataMask) == 0) {
      flush0();
    }
    return len;
  }

  /**
   * Returns false if no packet can be written until the reader reads one.
   */
  private boolean canWrite(long rseq) {
    if (rseq == wseq) {
      // reset position in buffer when reader is up to date
      if (!dirty)
        this.pstart = this.pend = 0;
      return true;
    }
    // cannot write if all packets are written and the reader didn't read them
    return wseq - rseq < npackets;
  }

  /**
   * EXPERIMENTAL. Returns a view on a segment of shared data so that the user
   * can directly write into it. This allows to avoid the array copy that would
   * take place if {@link #write(byte[], int, int)} was called. The view must
   * not be used after close().
   * <p>
   * Example usage:
   *
   * <pre>
   * // get byte buffer
   * ByteBuffer b = writer.newPacket(16);
   * // serialize data in it
   * b.put(data.getBytes());
   * // flush packet
   * w.send(b);
   * </pre>
   *
   * @param size the minimum size of the packet
   * @return a buffer of at least the requested size, or null if there is not
   *         enough space
   * @throws ClosedException if the socket is closed
   */
  public ByteBuffer newPacket(int size) {
    if (!enter())
      throw new ClosedException("Closed");
    ByteBuffer packet = null;
    boolean closedByReader = false;
    try {
      // data written with write() goes in its own packet
      flush0();

      if (getIntAcquire(RCLOSED) != 0)
        closedByReader = true;
      else {
        final long rseq = getLongAcquire(RSEQ);
        if (canWrite(rseq))
          packet = newPacket0(rseq, size);
        if (packet == null && peerDied(RPID))
          closedByReader = readerDied = true;
      }
    }
    finally {
      exit();
    }
    if (closedByReader) {
      close();
      throw new ClosedException(readerDied ? "Reader is dead" : "Closed by reader");
    }
    return packet;
  }

  private ByteBuffer newPacket0(long rseq, int size) {
    this.pstart = this.pend = align(pend);

    int av = getAvailableSpace(rseq);
    if (av < size)
      return null;

    setLong(packetInfo(wseq), pend);
    return currentPacket = buf.slice(dataPos(pend), av).order(buf.order());
  }

  /**
   * EXPERIMENTAL. Sends the packet that was previously returned by
   * {@link #newPacket(int)}.
   *
   * @param packet
   * @throws ClosedException if the socket is closed
   */
  public void send(ByteBuffer packet) {
    if (packet == null || packet != currentPacket)
      throw new IllegalArgumentException("Invalid packet");

    currentPacket = null;

    if (!enter())
      throw new ClosedException("Closed");
    try {
      int size = packet.position();
      setInt(packetInfo(wseq) + 8, size);
      pend += size;
      pstart = pend;
      publish();
    }
    finally {
      exit();
    }
  }

  /**
   * Makes the data written since the last flush available to the reader.
   *
   * @throws ClosedException if the socket is closed while data is pending
   */
  public void flush() {
    if (!enter()) {
      if (dirty)
        throw new ClosedException("Closed");
      return;
    }
    try {
      flush0();
    }
    finally {
      exit();
    }
  }

  private void flush0() {
    if (dirty) {
      final int pkt = packetInfo(wseq);
      setLong(pkt, pstart);
      setInt(pkt + 8, (int) (pend - pstart));

      this.pstart = this.pend = align(pend);
      dirty = false;

      publish();
    }
  }

  /**
   * Publishes the packet whose info has just been written.
   */
  private void publish() {
    setLongRelease(WSEQ, ++wseq);
    if (futex != null)
      futex.signal();
  }

  /**
   * Returns specified position, rounded up to the packet alignment.
   */
  private long align(long pos) {
    return (pos + alignMask) & ~alignMask;
  }

  /**
   * Returns the absolute position of the last read byte.
   *
   * @param rseq reader sequence number.
   */
  private long head(long rseq) {
    // if all packets are read, the position has been or will be reset
    if (wseq == rseq)
      return 0;

    final int pkt = packetInfo(wseq - 1);
    return getLong(pkt) + getInt(pkt + 8);
  }

  /**
   * Returns how many bytes can be written in one single chunk at current
   * position (head). We can be limited either by the end of the buffer or by
   * the tail, i.e. the start of the oldest packet not read yet: the one of the
   * reader or, if the reader has read all flushed packets, the pending one.
   * <p>
   * This method works only if pend/pstart are reset to 0 when the reader has
   * read everything (otherwise 0 can be returned instead of capacity).
   *
   * @param rseq sequence number of reader
   */
  private int getAvailableSpace(long rseq) {
    // the slot of the pending packet holds a stale position until flush(). The
    // reader moves the start of its packet forward after a partial read
    final long tail = rseq == wseq ? pstart : getLongAcquire(packetInfo(rseq));

    final long toEnd = capacity - (pend & dataMask);
    final long toTail = tail + capacity - pend;

    // toTail is negative when alignment padding has moved pend past the tail
    return (int) Math.max(0, Math.min(toEnd, toTail));
  }

  /**
   * Returns how many bytes can be written (mainly for test purposes).
   */
  public int available() {
    if (!enter())
      return 0;
    try {
      // cannot write if all packets are written and the reader didn't read any
      long rseq = getLongAcquire(RSEQ);
      if (wseq - rseq >= npackets)
        return 0;
      return getAvailableSpace(rseq);
    }
    finally {
      exit();
    }
  }

  @Override
  protected void close0() {
    setIntRelease(WCLOSED, 1);
    if (futex != null)
      futex.signal();
  }

  /**
   * For testing purposes.
   */
  public long getSeqNum() {
    return wseq;
  }

  /**
   * For testing purposes.
   */
  public long getPosition() {
    if (!enter())
      throw new ClosedException("Closed");
    try {
      return head(getLongAcquire(RSEQ));
    }
    finally {
      exit();
    }
  }

  /**
   * For testing purposes.
   */
  public String debug() {
    if (!enter())
      return "closed";
    try {
      long rseq = getLongAcquire(RSEQ);
      return String.format("wseq=%d rseq=%d pstart=%d plen=%d tail=%d dirty=%b capacity=%d",
                           wseq,
                           rseq,
                           pstart,
                           pend - pstart,
                           head(rseq),
                           pend > pstart,
                           capacity);
    }
    finally {
      exit();
    }
  }

  /**
   * Signals new packets on the futex, for a reader which has called
   * {@link JocketReader#useFutex()}. The buffer must be a direct one.
   */
  public void useFutex() {
    this.futex = new Futex(buf, FUTEX);
  }
}
