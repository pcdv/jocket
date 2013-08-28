package jocket.impl;

import java.nio.ByteBuffer;

public final class JocketWriter extends AbstractJocketBuffer {

  /**
   * The sequence number of the next packet to write. Accessed only by the
   * writer thread.
   */
  private int wseq;

  private int pstart, pend;

  public JocketWriter(ByteBuffer buf, int npackets) {
    super(buf, npackets);
  }

  public int write(final byte[] data, final int off, final int len) {
    readMemoryBarrier();
    final int rseq = rseq();
    final int wseq = this.wseq;

    if (rseq == wseq) {
      // reset position in buffer when reader is up to date
      if (rseq > 0 && pend == pstart) {
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
    if (pend > pstart) {
      int pkt = PACKET_INFO + (wseq & packetMask) * LEN_PACKET_INFO;
      buf.putInt(pkt, pstart);
      buf.putInt(pkt + 4, pend - pstart);
      buf.putInt(WSEQ, ++wseq);
      this.pstart = pend;
    }
  }

  /**
   * Returns the position immediately after the last written bytes. Warning: the
   * result can be equal to <code>capacity</code>, which is not equivalent to
   * <code>0</code> because it means that data was written at the end of buffer.
   * However, if there is space in the buffer, data must be written at position
   * <code>0</code> (as it is a circular buffer).
   * 
   * @param rseq the reader's sequence number. It is required because it allows
   *          to determine whether all data has been read, in which case the new
   *          head is 0 whatever the current state of packets
   */
  private int head(int rseq) {
    // if all packets are read, we can start at 0
    if (wseq == rseq)
      return 0;

    final int pkt = PACKET_INFO + ((wseq - 1) & packetMask) * LEN_PACKET_INFO;

    return (buf.getInt(pkt) + buf.getInt(pkt + 4));
  }

  /**
   * Returns the address at which specified packet starts.
   * 
   * @param seq a packet number
   */
  private int start(int seq) {
    return buf.getInt(PACKET_INFO + (seq & packetMask) * LEN_PACKET_INFO);
  }

  /**
   * Returns how many bytes can be written in one single chunk at current
   * position. There are two cases:
   * <ul>
   * <li><b>head &gt; tail</b>: result = capacity - (head - tail)
   * <li><b>head &lt; tail</b>: result = tail - head
   * </ul>
   * 
   * @param rseq current seqnum of the reader
   * @param head current data head
   */
  private int getAvailableSpace(int rseq, int head) {
    return Math.min(start(rseq), head - (head & dataMask)) + capacity - head;
  }

  private int rseq() {
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
