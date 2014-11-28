package jocket.impl;

import java.nio.ByteBuffer;
import java.nio.MappedByteBuffer;

import jocket.futex.Futex;

public class JocketWriter extends AbstractJocketBuffer {

  /** The sequence number of the next packet to write. */
  private int wseq;

  /** Pending packet's absolute start and end positions. */
  private int pstart, pend;

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

  private ByteBuffer currentPacket;

  public JocketWriter(ByteBuffer buf, int npackets) {
    super(buf, npackets);
  }

  /**
   * Sets packet alignment.
   *
   * @param align must be either 0 or a power of 2
   */
  public void setAlign(int align) {
    if (align < 0 || align > 0 && Integer.bitCount(align) != 1)
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
   * @throws ClosedException if the socket is closed
   */
  public int write(final byte[] data, final int off, int len) {
    final int rseq = rseq();

    if (rseq == wseq) {
      // reset position in buffer when reader is up to date
      if (rseq > 0 && !dirty) {
        this.pstart = this.pend = 0;
      }
    }

    else if (rseq < 0)
      close();

    // cannot write if all packets are written and the reader didn't read them
    else if (wseq - rseq >= npackets)
      return 0;

    if (closed)
      throw new ClosedException("Closed");

    // TODO: implement anti-truncation mechanism (write at 0 if remaining
    // space is too small)
    len = Math.min(getAvailableSpace(rseq), len);
    if (len > 0) {
      dirty = true;
      buf.position(dataOffset + (pend & dataMask));
      buf.put(data, off, len);
      this.pend += len;

      // flush when reaching end of buffer, otherwise next write will
      // generate an inconsistent packet (overflowing the buffer)
      if ((this.pend & dataMask) == 0) {
        flush();
      }
    }
    return len;
  }

  /**
   * EXPERIMENTAL. Returns a view on a segment of shared data so that the user
   * can directly write into it. This allows to avoid the array copy that would
   * take place if {@link #write(byte[], int, int)} was called.
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
   * @param size
   * @return
   */
  public ByteBuffer newPacket(int size) {
    final int rseq = rseq();
    final int wseq = this.wseq;

    if (rseq == wseq) {
      // reset position in buffer when reader is up to date
      if (rseq > 0 && !dirty) {
        this.pstart = this.pend = 0;
      }
    }
    else if (rseq < 0)
      close();

    // cannot write if all packets are written and the reader didn't read them
    else if (wseq - rseq >= npackets)
      return null;

    if (isClosed())
      throw new ClosedException("Closed");

    int av = getAvailableSpace(rseq);
    if (av < size)
      return null;

    int pkt = PACKET_INFO + (wseq & packetMask) * LEN_PACKET_INFO;

    int mod = pend & alignMask;
    this.pend += align - mod;
    this.pstart = pend;

    acc.putInt(pkt, pend);
    buf.position(dataOffset + pstart);
    buf.limit(dataOffset + pstart + av);
    return currentPacket = buf.slice();
  }

  /**
   * EXPERIMENTAL. Sends the packet that was previously returned by
   * {@link #newPacket(int)}.
   *
   * @param packet
   */
  public void send(ByteBuffer packet) {
    if (packet == null || packet != currentPacket)
      throw new IllegalArgumentException("Invalid packet");

    currentPacket = null;

    int pkt = PACKET_INFO + (wseq & packetMask) * LEN_PACKET_INFO;
    int size = packet.position();
    acc.putInt(pkt + 4, size);
    pend += size;
    pstart = pend;
    writeMemoryBarrier();
    acc.putInt(WSEQ, ++wseq);
  }

  public void flush() {
    final int pkt = PACKET_INFO + (wseq & packetMask) * LEN_PACKET_INFO;
    final ByteBufferAccessor acc = this.acc;
    if (dirty) {
      acc.putInt(pkt, pstart);
      acc.putInt(pkt + 4, pend - pstart);
      acc.putInt(WSEQ, ++wseq);

      this.pend += align - (pend & alignMask);
      this.pstart = pend;

      dirty = false;
      if (futex != null)
        futex.signal(wseq);
    }
  }

  /**
   * Returns the absolute position of the last read byte.
   *
   * @param rseq reader sequence number.
   */
  private int head(int rseq) {
    // if all packets are read, the position has been or will be reset
    if (wseq == rseq)
      return 0;

    final int pkt = PACKET_INFO + ((wseq - 1) & packetMask) * LEN_PACKET_INFO;

    return acc.getInt(pkt) + acc.getInt(pkt + 4);
  }

  /**
   * Returns the absolute position of specified packet.
   *
   * @param seq a packet number
   */
  private int start(int seq) {
    return acc.getInt(PACKET_INFO + (seq & packetMask) * LEN_PACKET_INFO);
  }

  /**
   * Returns how many bytes can be written in one single chunk at current
   * position. We can be limited either by the bounds of the ByteBuffer or by
   * how many bytes must still be read.
   * <p>
   * Result is (X - head) where X is the smallest of:
   * <ul>
   * <li>head + capacity - (head - head % capacity)
   * <li>tail + capacity
   * </ul>
   * <p>
   * This method works only if pend/pstart are reset to 0 when the reader has
   * read everything (otherwise 0 can be returned instead of capacity).
   *
   * @param rseq sequence number of reader
   * @param head position of last written byte
   */
  private int getAvailableSpace(int rseq) {
    return Math.min(start(rseq), pend - (pend & dataMask)) + capacity - pend;

    // NB: this is the contracted form of:
    // -----------------------------------
    // int lim1 = pend + capacity - (pend & dataMask);
    // int lim2 = start(rseq) + capacity;
    // return Math.min(lim1, lim2) - pend;
  }

  /**
   * Returns the reader sequence number.
   */
  private final int rseq() {
    return acc.getInt(RSEQ);
  }

  /**
   * Returns how many bytes can be written (mainly for test purposes).
   */
  public int available() {
    // cannot write if all packets are written and the reader didn't read any
    int rseq = rseq();
    if (wseq - rseq >= npackets)
      return 0;
    return getAvailableSpace(rseq);
  }

  @Override
  protected void close0() {
    writeMemoryBarrier(); // not sure if really necessary
    acc.putInt(WSEQ, -1);
    if (futex != null)
      futex.signal(-1);
  }

  /**
   * For testing purposes.
   */
  public int getSeqNum() {
    return wseq;
  }

  /**
   * For testing purposes.
   */
  public int getPosition() {
    return head(rseq());
  }

  /**
   * For testing purposes.
   */
  public String debug() {
    return String
        .format("wseq=%d rseq=%d pstart=%d plen=%d tail=%d dirty=%b capacity=%d",
                wseq,
                rseq(),
                pstart,
                pend - pstart,
                head(rseq()),
                pend > pstart,
                capacity);
  }

  public void useFutex() {
    this.futex = new Futex((MappedByteBuffer) buf, FUTEX, -1);
  }
}
