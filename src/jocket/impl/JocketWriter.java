package jocket.impl;

import java.nio.ByteBuffer;

public class JocketWriter extends AbstractJocketBuffer {

  /**
   * The sequence number of the next packet to write. Accessed only by the
   * writer thread.
   */
  private int wseq;

  private int pstart, plen;

  private boolean dirty;

  public JocketWriter(ByteBuffer buf, int npackets) {
    super(buf, npackets);
  }

  @Override
  protected void close0() {
    buf.putInt(WSEQ, -1);
    writeMemoryBarrier();
  }

  public int write(byte[] data, int off, int len) {
    readMemoryBarrier();
    final int rseq = rseq();
    if (rseq < 0)
      close();

    if (isClosed())
      throw new ClosedException("Closed");

    // cannot write if all packets are written and the reader didn't read them
    if (wseq - rseq >= npackets)
      return 0;

    // TODO: implement anti-truncation mechanism (write at 0 if remaining
    // space is too small)
    int head = pstart + plen;
    final int bytes = Math.min(getAvailableSpace(rseq, head), len);
    if (bytes > 0) {
      plen += bytes;
      buf.position(dataOffset + (head & dataMask));
      buf.put(data, off, bytes);
      dirty = true;

      // TODO: optimize
      // always flush when reaching end of buffer
      if ((pstart & dataMask) + plen == capacity)
        flush();
    }
    return bytes;
  }

  public void flush() {
    if (dirty) {
      int pkt = PACKET_INFO + (wseq & packetMask) * LEN_PACKET_INFO;
      buf.putInt(pkt, pstart);
      buf.putInt(pkt + 4, plen);
      buf.putInt(WSEQ, ++wseq);
      pstart += plen;
      plen = 0;
      dirty = false;
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

    if (rseq < wseq) {
      int tail = start(rseq);

      if (head == tail)
        return capacity;

      // if head == capacity then head = 0 (circular buffer)
      head &= dataMask;
      tail &= dataMask;

      if (head > tail)
        return capacity - head;

      return tail - head;
    }

    else
      return capacity - (head & dataMask);
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
    return getAvailableSpace(rseq, head(rseq));
  }
}
