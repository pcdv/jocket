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
  public ByteBuffer getBuffer() {
    return buf;
  }

}
