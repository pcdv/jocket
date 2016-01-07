package jocket.impl;

import java.nio.ByteBuffer;
import java.nio.MappedByteBuffer;

import jocket.net.JocketFile;

public class DefaultAccessor implements ByteBufferAccessor {

  private final ByteBuffer buf;

  public DefaultAccessor(ByteBuffer buf) {
    this.buf = buf;
  }

  public void putInt(int pos, int val) {
    buf.putInt(pos, val);
  }

  public int getInt(int pos) {
    return buf.getInt(pos);
  }

  public int capacity() {
    return buf.capacity();
  }

  public byte get(int pos) {
    return buf.get(pos);
  }

  public void put(int pos, byte val) {
    buf.put(pos, val);
  }

  public void position(int pos) {
    buf.position(pos);
  }

  public void put(byte[] data, int off, int bytes) {
    buf.put(data, off, bytes);
  }

  public void get(byte[] data, int off, int available) {
    buf.get(data, off, available);
  }

  public ByteBuffer getBuffer() {
    return buf;
  }

  public void limit(int limit) {
    buf.limit(limit);
  }

  @Override
  public void unmap() {
    if (buf instanceof MappedByteBuffer)
      JocketFile.unmap((MappedByteBuffer) buf);
  }
}
