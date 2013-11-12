package jocket.impl;

import java.nio.ByteBuffer;
import java.nio.MappedByteBuffer;

import jocket.futex.Futex;
import jocket.wait.BusyYieldSleep;
import jocket.wait.WaitStrategy;

public class JocketReader extends AbstractJocketBuffer {

  private int rseq;

  private int wseq;

  private WaitStrategy waiter = new BusyYieldSleep();

  private ByteBuffer currentPacket;

  public JocketReader(ByteBuffer buf, int npackets) {
    super(new DefaultAccessor(buf), npackets);
  }

  public JocketReader(ByteBufferAccessor buf, int npackets) {
    super(buf, npackets);
  }

  @Override
  protected void close0() {
    writeMemoryBarrier(); // not sure if really necessary
    buf.putInt(RSEQ, -1);
  }

  public int read(byte[] data) {
    return read(data, 0, data.length);
  }

  public int readBlocking(byte[] data, int off, int len) {
    for (;;) {
      int read = read(data, off, len);
      if (read != 0) {
        return read;
      }
      else {
        waiter.pauseWhile(wseq);
      }
    }
  }

  public int read(byte[] data, int off, int len) {
    if (wseq <= rseq) {
      readWseq();

      if (wseq <= rseq) {
        if (wseq < 0)
          close();

        if (isClosed())
          return -1;
        return 0;
      }
    }

    final int pktInfo = PACKET_INFO + (rseq & packetMask) * LEN_PACKET_INFO;
    final int available = buf.getInt(pktInfo + 4);

    buf.position(dataOffset + (buf.getInt(pktInfo) & dataMask));

    // if the whole packet can be read
    if (available <= len) {
      buf.get(data, off, available);
      buf.putInt(RSEQ, ++rseq);
      len = available;
    }

    // if the packet can be read only partially
    else {
      // read data
      buf.get(data, off, len);

      // update packet info to make space available for writer (the order of
      // the 2 writes should not be important as the writer does not look at
      // packet length)
      buf.putInt(pktInfo + 4, available - len);
      buf.putInt(pktInfo, buf.getInt(pktInfo) + len);
    }

    return len;
  }

  /**
   * EXPERIMENTAL.
   */
  public ByteBuffer nextPacket() {
    if (wseq <= rseq) {
      readWseq();

      if (wseq <= rseq) {
        if (wseq < 0)
          throw new ClosedException("Socket closed");
        return null;
      }
    }

    final int pktInfo = PACKET_INFO + (rseq & packetMask) * LEN_PACKET_INFO;
    final int available = buf.getInt(pktInfo + 4);
    int pos = dataOffset + (buf.getInt(pktInfo) & dataMask);

    ByteBuffer buf = this.buf.getBuffer();
    buf.position(pos);
    buf.limit(pos + available);

    return currentPacket = buf.slice();
  }

  public void release(ByteBuffer packet) {
    if (currentPacket == null || currentPacket != packet)
      throw new IllegalArgumentException("Invalid packet");

    currentPacket = null;

    if (packet.remaining() == 0) {
      buf.putInt(RSEQ, ++rseq);
    }
    else {
      final int pktInfo = PACKET_INFO + (rseq & packetMask) * LEN_PACKET_INFO;
      buf.putInt(pktInfo + 4, packet.remaining());
      buf.putInt(pktInfo, packet.position());
    }
  }

  private void readWseq() {
    wseq = buf.getInt(WSEQ);
  }

  public int available() {
    int wseq = buf.getInt(WSEQ);
    if (wseq <= rseq)
      return 0;

    int windex = (wseq - 1) & packetMask; // last packet written
    int rindex = rseq & packetMask; // first packet written

    int start = buf.getInt(PACKET_INFO + rindex * LEN_PACKET_INFO);
    int end = buf.getInt(PACKET_INFO + windex * LEN_PACKET_INFO)
        + buf.getInt(PACKET_INFO + windex * LEN_PACKET_INFO + 4);

    if (start <= end)
      return end - start;
    else
      return capacity - (start - end);
  }

  public void useFutex() {
    this.waiter = new Futex((MappedByteBuffer) buf.getBuffer(), FUTEX, WSEQ);
  }

  public WaitStrategy getWaitStrategy() {
    return waiter;
  }
}
