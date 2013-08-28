package jocket.impl;

import java.nio.ByteBuffer;

public final class JocketWriter extends AbstractJocketBuffer {

  /** The sequence number of the next packet to write. */
  private int wseq;

  /** Pending packet's absolute start and end positions. */
  private int pstart, pend;

  /**
   * Equivalent to (pend > pstart) but looks like it is faster to store this
   * information in a dedicated boolean.
   */
  private boolean dirty;

  public JocketWriter(ByteBuffer buf, int npackets) {
    super(buf, npackets);
  }

  public int write(final byte[] data, final int off, final int len) {
    readMemoryBarrier();
    final int rseq = rseq();
    final int wseq = this.wseq;

    if (rseq == wseq) {
      // reset position in buffer when reader is up to date
      if (rseq > 0 && !dirty) {
        this.pstart = this.pend = 0;
      }
    }

    // cannot write if all packets are written and the reader didn't read them
    else if (wseq - rseq >= npackets)
      return 0;

    else if (rseq < 0)
      close();

    if (isClosed())
      throw new ClosedException("Closed");

    // TODO: implement anti-truncation mechanism (write at 0 if remaining
    // space is too small)
    final int bytes = Math.min(getAvailableSpace(rseq, pend), len);
    if (bytes > 0) {
      dirty = true;
      buf.position(dataOffset + (pend & dataMask));
      buf.put(data, off, bytes);
      this.pend += bytes;

      // flush when reaching end of buffer, otherwise next write will
      // generate an inconsistent packet (overflowing the buffer)
      if ((this.pend & dataMask) == 0) {
        flush();
      }
    }
    return bytes;
  }

  public void flush() {
    final int pend = this.pend;
    final int pstart = this.pstart;
    final ByteBuffer buf = this.buf;
    if (dirty) {
      int pkt = PACKET_INFO + (wseq & packetMask) * LEN_PACKET_INFO;
      buf.putInt(pkt, pstart);
      buf.putInt(pkt + 4, pend - pstart);
      buf.putInt(WSEQ, ++wseq);
      this.pstart = pend;
      dirty = false;
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

    return buf.getInt(pkt) + buf.getInt(pkt + 4);
  }

  /**
   * Returns the absolute position of specified packet.
   * 
   * @param seq a packet number
   */
  private int start(int seq) {
    return buf.getInt(PACKET_INFO + (seq & packetMask) * LEN_PACKET_INFO);
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
   * 
   * @param rseq sequence number of reader
   * @param head position of last written byte
   */
  private int getAvailableSpace(int rseq, int head) {
    return Math.min(start(rseq), head - (head & dataMask)) + capacity - head;
  }

  /**
   * Returns the reader sequence number. Call must be preceded by a read memory
   * barrier.
   */
  private final int rseq() {
    return buf.getInt(RSEQ);
  }

  /**
   * Returns how many bytes can be written (mainly for test purposes).
   */
  public int available() {
    // cannot write if all packets are written and the reader didn't read any
    int rseq = rseq();
    if (wseq - rseq >= npackets)
      return 0;
    return getAvailableSpace(rseq, pend);
  }

  @Override
  protected void close0() {
    buf.putInt(WSEQ, -1);
    writeMemoryBarrier();
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
    return String.format(
        "wseq=%d rseq=%d pstart=%d plen=%d tail=%d dirty=%b capacity=%d", wseq,
        rseq(), pstart, pend - pstart, head(rseq()), pend > pstart, capacity);
  }
}
