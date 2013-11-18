package jocket.impl;

import java.nio.ByteBuffer;

public interface ByteBufferAccessor {

  void putInt(int pos, int val);

  int getInt(int pos);

  ByteBuffer getBuffer();

}
