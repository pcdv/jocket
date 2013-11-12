package jocket.impl;

import java.nio.ByteBuffer;

public interface ByteBufferAccessor {

  void putInt(int pos, int val);

  int getInt(int pos);

  int capacity();

  byte get(int pos);

  void put(int pos, byte val);

  void position(int pos);

  void put(byte[] data, int off, int bytes);

  void get(byte[] data, int off, int available);

  void limit(int limit);

  ByteBuffer getBuffer();

}
