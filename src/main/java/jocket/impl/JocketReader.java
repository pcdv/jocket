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
        // waiter.reset();
        return read;
      } else {
        waiter.pauseWhile(wseq);
      }
    }
  }

  public int read(byte[] data, int off, int len) {
    readWseq();

    if (wseq <= rseq) {
      if (wseq < 0)
        close();

      if (isClosed())
        return -1;
      return 0;
    }

    final ByteBufferAccessor buf = this.buf;
    final int pktInfo = PACKET_INFO + (rseq & packetMask) * LEN_PACKET_INFO;
    final int available = buf.getInt(pktInfo + 4);

    int pos = dataOffset + (buf.getInt(pktInfo) & dataMask);
    // if the whole packet can be read
    if (available <= len) {
      len = available;
      buf.position(pos);
      buf.get(data, off, available);
      buf.putInt(RSEQ, ++rseq);
    }

    // if the packet can be read only partially
    else {
      // read data
      buf.position(pos);
      buf.get(data, off, len);

      // update packet info to make space available for writer (the order of
      // the 2 writes should not be important as the writer does not look that
      // packet length)
      buf.putInt(pktInfo, buf.getInt(pktInfo) + len);
      buf.putInt(pktInfo + 4, available - len);
    }

    return len;
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
