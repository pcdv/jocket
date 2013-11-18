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
    super(buf, npackets);
  }

  @Override
  protected void close0() {
    writeMemoryBarrier(); // not sure if really necessary
    acc.putInt(RSEQ, -1);
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
    final int available = acc.getInt(pktInfo + 4);

    buf.position(dataOffset + (acc.getInt(pktInfo) & dataMask));

    // if the whole packet can be read
    if (available <= len) {
      buf.get(data, off, available);
      acc.putInt(RSEQ, ++rseq);
      len = available;
    }

    // if the packet can be read only partially
    else {
      // read data
      buf.get(data, off, len);

      // update packet info to make space available for writer (the order of
      // the 2 writes should not be important as the writer does not look at
      // packet length)
      acc.putInt(pktInfo + 4, available - len);
      acc.putInt(pktInfo, acc.getInt(pktInfo) + len);
    }

    return len;
  }

  /**
   * EXPERIMENTAL (part of the zero-copy API). If a packet is available, return
   * a ByteBuffer wrapping the packet. The ByteBuffer should not be modified as
   * it points directly into the exchange file (when using a MappedByteBuffer).
   * After use, {@link #release(ByteBuffer)} MUST be called in order to notify
   * the writer that the read area has become available for writing.
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
    final int packetSize = acc.getInt(pktInfo + 4);
    final int packetPos = dataOffset + (acc.getInt(pktInfo) & dataMask);

    buf.position(packetPos);
    buf.limit(packetPos + packetSize);

    // NB: I'm not sure calling slice is cheaper than the array-copy it avoids
    // (except for big packets). An alternative would be to directly return
    // the main buffer (more dangerous because it allows to bork the entire
    // exchange file). Surely a reusable/mutable slice would be better.
    return currentPacket = buf.slice();
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

    if (packet.remaining() == 0) {
      acc.putInt(RSEQ, ++rseq);
    }
    else {
      final int pktInfo = PACKET_INFO + (rseq & packetMask) * LEN_PACKET_INFO;
      acc.putInt(pktInfo + 4, packet.remaining());
      acc.putInt(pktInfo, packet.position());
    }
  }

  private void readWseq() {
    wseq = acc.getInt(WSEQ);
  }

  public int available() {
    int wseq = acc.getInt(WSEQ);
    if (wseq <= rseq)
      return 0;

    int windex = (wseq - 1) & packetMask; // last packet written
    int rindex = rseq & packetMask; // first packet written

    int start = acc.getInt(PACKET_INFO + rindex * LEN_PACKET_INFO);
    int end = acc.getInt(PACKET_INFO + windex * LEN_PACKET_INFO)
              + acc.getInt(PACKET_INFO + windex * LEN_PACKET_INFO + 4);

    if (start <= end)
      return end - start;
    else
      return capacity - (start - end);
  }

  public void useFutex() {
    this.waiter = new Futex((MappedByteBuffer) buf, FUTEX, WSEQ);
  }

  public WaitStrategy getWaitStrategy() {
    return waiter;
  }
}
