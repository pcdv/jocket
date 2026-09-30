package jocket.impl;

import java.io.Closeable;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.concurrent.TimeUnit;

/**
 * Base class for JocketReader and JocketWriter.
 * <p>
 * A reader or a writer must be used by one thread at a time. close() can be
 * called from any thread: it waits for the operation in progress, if any,
 * before unmapping the buffer.
 *
 * @author pcdv
 */
public abstract class AbstractJocketBuffer implements Const, Closeable {

  /**
   * Access the header shared with the other side (possibly another process),
   * with the memory ordering guarantees it requires: data and packet info are
   * written before the sequence number that publishes them is released, and
   * read after it is acquired. Need aligned positions.
   */
  private static final VarHandle INT =
    MethodHandles.byteBufferViewVarHandle(int[].class, ByteOrder.nativeOrder());

  private static final VarHandle LONG =
    MethodHandles.byteBufferViewVarHandle(long[].class, ByteOrder.nativeOrder());

  /**
   * Minimum time between two checks that the peer process is alive.
   */
  private static final long PEER_CHECK_INTERVAL =
    TimeUnit.MILLISECONDS.toNanos(Long.getLong("jocket.peerCheckMillis", 1000));

  private static final VarHandle CLOSED;

  private static final VarHandle BUSY;

  static {
    try {
      MethodHandles.Lookup lookup = MethodHandles.lookup();
      CLOSED = lookup.findVarHandle(AbstractJocketBuffer.class, "closed", boolean.class);
      BUSY = lookup.findVarHandle(AbstractJocketBuffer.class, "busy", boolean.class);
    }
    catch (ReflectiveOperationException e) {
      throw new ExceptionInInitializerError(e);
    }
  }

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

  protected final int dataOffset;

  /**
   * Called once the buffer will not be accessed anymore.
   */
  private final Runnable release;

  private volatile boolean closed;

  /**
   * True while an operation accesses the buffer.
   */
  private volatile boolean busy;

  /**
   * Number of calls to peerDied(), which looks at the clock every 1024 calls.
   */
  private int idle;

  private long lastPeerCheck;

  /**
   * @param release called once the buffer is not accessed anymore, after
   *        close()
   */
  public AbstractJocketBuffer(ByteBuffer buf, int npackets, Runnable release) {
    if (Integer.bitCount(npackets) != 1)
      throw new IllegalArgumentException("npackets must be a power of 2");

    this.buf = buf;
    this.release = release;
    this.npackets = npackets;
    this.packetMask = npackets - 1;
    this.capacity = buf.capacity() - (PACKET_INFO + npackets * LEN_PACKET_INFO);
    if (Integer.bitCount(capacity) != 1)
      throw new IllegalArgumentException("Buffer capacity for data must be a power of 2");
    this.dataMask = capacity - 1;
    this.dataOffset = PACKET_INFO + npackets * LEN_PACKET_INFO;
  }

  protected final int getInt(int pos) {
    return (int) INT.get(buf, pos);
  }

  protected final int getIntAcquire(int pos) {
    return (int) INT.getAcquire(buf, pos);
  }

  protected final void setInt(int pos, int value) {
    INT.set(buf, pos, value);
  }

  protected final void setIntRelease(int pos, int value) {
    INT.setRelease(buf, pos, value);
  }

  protected final long getLong(int pos) {
    return (long) LONG.get(buf, pos);
  }

  protected final long getLongAcquire(int pos) {
    return (long) LONG.getAcquire(buf, pos);
  }

  protected final void setLong(int pos, long value) {
    LONG.set(buf, pos, value);
  }

  protected final void setLongRelease(int pos, long value) {
    LONG.setRelease(buf, pos, value);
  }

  /**
   * Writes the pid and PID namespace of the current process at specified
   * position, for the other side to check if it is alive.
   */
  protected final void publishPid(int pos) {
    setLong(pos + 8, Processes.NAMESPACE);
    setLongRelease(pos, Processes.PID);
  }

  /**
   * To be called when an operation cannot progress (no data to read, or no
   * space to write). Returns true if the peer process, whose pid is at
   * specified position, is dead. Cheap: it looks at the clock every 1024
   * calls, and checks the peer at most once per interval.
   */
  protected final boolean peerDied(int pidPos) {
    if ((++idle & 1023) != 0)
      return false;
    return checkPeer(pidPos);
  }

  /**
   * Same as {@link #peerDied(int)}, looking at the clock at each call: for
   * callers already calling it rarely (e.g. after sleeping on the futex).
   */
  protected final boolean checkPeer(int pidPos) {
    long now = System.nanoTime();
    if (now - lastPeerCheck < PEER_CHECK_INTERVAL)
      return false;
    lastPeerCheck = now;
    return !Processes.isAlive(getLongAcquire(pidPos), getLong(pidPos + 8));
  }

  /**
   * Returns the position of the info of specified packet.
   */
  protected final int packetInfo(long seq) {
    return PACKET_INFO + (int) (seq & packetMask) * LEN_PACKET_INFO;
  }

  /**
   * Returns the position in buffer of specified absolute data position.
   */
  protected final int dataPos(long pos) {
    return dataOffset + (int) (pos & dataMask);
  }

  /**
   * Starts an operation accessing the buffer, which must be followed by exit().
   * Returns false if closed: the buffer must not be accessed anymore.
   * <p>
   * An operation must never call close(), which would wait for the operation
   * to end: it calls it after exit().
   */
  protected final boolean enter() {
    // volatile write then volatile read, and the reverse in close(): either
    // close() sees the operation, or the operation sees closed
    busy = true;
    if (closed) {
      busy = false;
      return false;
    }
    return true;
  }

  protected final void exit() {
    BUSY.setRelease(this, false);
  }

  /**
   * Closes this reader or writer, notifies the other side, then unmaps the
   * buffer once the operation in progress (in another thread) has ended.
   */
  @Override
  public final void close() {
    if (!CLOSED.compareAndSet(this, false, true))
      return;

    // the buffer is still mapped: an operation in progress may be accessing it
    close0();

    // the thread using the buffer stops at its next check of closed
    for (int i = 0; busy; i++) {
      if (i < 1000)
        Thread.onSpinWait();
      else
        Thread.yield();
    }
    release.run();
  }

  /**
   * Notifies the other side that this reader or writer is closed.
   */
  protected abstract void close0();

  public final boolean isClosed() {
    return closed;
  }
}
