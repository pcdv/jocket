package jocket.impl;

import java.io.EOFException;
import java.nio.ByteBuffer;

public class JocketReader extends AbstractJocketBuffer {

  private int rseq;

  public JocketReader(ByteBuffer buf, int npackets) {
    super(buf, npackets);
  }

  @Override
  protected void close0() {
    buf.putInt(RSEQ, -1);
    writeMemoryBarrier();
  }

  public int read(byte[] data) throws EOFException {
    return read(data, 0, data.length);
  }

  public int read(byte[] data, int off, int len) throws EOFException {
    int wseq = buf.getInt(WSEQ);
    if (wseq < 0)
      close();

    if (isClosed())
      return -1;

    if (wseq <= rseq)
      return 0;

    int index = rseq & packetMask;
    int position = buf.getInt(PACKET_INFO + index * LEN_PACKET_INFO);
    int available = buf.getInt(PACKET_INFO + index * LEN_PACKET_INFO + 4);

    // if the whole packet can be read
    if (available <= len) {
      buf.position(dataOffset + (position & dataMask));
      buf.get(data, off, available);
      buf.putInt(RSEQ, ++rseq);
      writeMemoryBarrier();
      return available;
    }

    // if the packet can be read only partially
    else {
      // read data
      buf.position(dataOffset + (position & dataMask));
      buf.get(data, off, len);

      // update packet info to make space available for writer
      buf.putInt(PACKET_INFO + index * LEN_PACKET_INFO, position + len);
      buf.putInt(PACKET_INFO + index * LEN_PACKET_INFO + 4, available - len);
      writeMemoryBarrier();
      return len;
    }

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
}
