package jocket.impl;

import java.nio.ByteBuffer;
import java.nio.MappedByteBuffer;

import jocket.futex.Futex;
import jocket.futex.UnsafeUtil;
import sun.misc.Unsafe;

public final class UnsafeAccessor implements ByteBufferAccessor {

  private static final Unsafe unsafe = UnsafeUtil.unsafe();

  private final MappedByteBuffer buf;

  private final long addr;

  public UnsafeAccessor(MappedByteBuffer buf) {
    this.buf = buf;
    this.addr = Futex.getAddress(buf);
  }

  @Override
  public void putInt(int pos, int val) {
    unsafe.putInt(addr + pos, val);
  }

  @Override
  public int getInt(int pos) {
    return unsafe.getInt(addr + pos);
  }

  @Override
  public byte get(int pos) {
    return unsafe.getByte(addr + pos);
  }

  @Override
  public void put(int pos, byte val) {
    unsafe.putByte(addr + pos, val);
  }

  @Override
  public void position(int pos) {
    buf.position(pos);
  }

  @Override
  public void put(byte[] data, int off, int bytes) {
    buf.put(data, off, bytes);
  }

  @Override
  public void get(byte[] data, int off, int available) {
    buf.get(data, off, available);
  }

  @Override
  public ByteBuffer getBuffer() {
    return buf;
  }

  @Override
  public int capacity() {
    return buf.capacity();
  }

}
