package jocket.impl;

import java.nio.ByteBuffer;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Base class for JocketReader and JocketWriter.
 * 
 * @author pcdv
 */
public abstract class AbstractJocketBuffer implements Const {

  protected final ByteBuffer buf;

  /**
   * Number of data bytes that can be stored in buffer. Must be a power of 2.
   */
  protected final int capacity;

  /**
   * Equals (capacity - 1). Allows to perform quick modulo with binary AND.
   */
  protected final int dataMask;

  /**
   * Maximum number of packets that can be written but not read.
   */
  protected final int npackets;

  /**
   * Equals (npackets - 1). Allows to perform quick modulo with binary AND.
   */
  protected final int packetMask;

  private final AtomicBoolean barrier = new AtomicBoolean();

  protected final int dataOffset;

  private boolean closed;

  public AbstractJocketBuffer(ByteBuffer buf, int npackets) {
    if (Integer.bitCount(npackets) != 1)
      throw new IllegalArgumentException("npackets must be a power of 2");
    this.buf = buf;
    this.npackets = npackets;
    this.packetMask = npackets - 1;
    this.capacity = buf.capacity() - (PACKET_INFO + npackets * LEN_PACKET_INFO);
    if (Integer.bitCount(capacity) != 1)
      throw new IllegalArgumentException(
          "Buffer capacity for data must be a power of 2");
    this.dataMask = capacity - 1;
    this.dataOffset = Const.PACKET_INFO + npackets * LEN_PACKET_INFO;
  }

  protected void writeMemoryBarrier() {
    barrier.lazySet(true);
  }

  protected void readMemoryBarrier() {
    barrier.get();
  }

  public void close() {
    if (!closed) {
      closed = true;
      close0();
    }
  }

  protected abstract void close0();

  public final boolean isClosed() {
    return closed;
  }
}
