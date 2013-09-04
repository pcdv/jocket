package jocket.bench;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.net.Socket;

import jocket.net.JocketSocket;

/**
 * A standalone benchmark running the both client and server.
 * 
 * @author pcdv
 */
@SuppressWarnings("resource")
public class BenchClient {

  private final int niter;
  private final int datasize;
  private final long[] nanos;
  private DataOutputStream out;
  private DataInputStream in;
  private byte[] buf;
  private String fileName;
  private final int port;

  public BenchClient(int niter, int datasize, boolean useJocket, int port)
      throws IOException {
    this.niter = niter;
    this.datasize = datasize;
    this.port = port;
    this.nanos = new long[niter];
    if (useJocket)
      initJocket();
    else
      initSocket();
  }

  private void initJocket() throws IOException {
    fileName = "/tmp/Jocket";
    JocketSocket s = new JocketSocket(port);
    in = new DataInputStream(s.getInputStream());
    out = new DataOutputStream(s.getOutputStream());
    buf = new byte[datasize];
  }

  private void initSocket() throws IOException {
    fileName = "/tmp/Socket";
    Socket s = new Socket("localhost", port);
    s.setTcpNoDelay(true);
    in = new DataInputStream(s.getInputStream());
    out = new DataOutputStream(s.getOutputStream());
    buf = new byte[datasize];
  }

  public void bench() throws IOException {
    long time = System.currentTimeMillis();
    System.out.println("Starting " + niter + " iterations");
    out.writeInt(niter);
    out.writeInt(datasize);
    out.flush();
    for (int i = 0; i < niter; i++) {
      iter(i);
    }
    time = System.currentTimeMillis() - time;
    System.out
        .println("Done in " + time + "ms. Dumping results in " + fileName);
    dumpResults(new FileOutputStream(fileName));
  }

  public void iter(int i) throws IOException {
    long start = System.nanoTime();

    // send request
    out.write(buf, 0, 4);
    out.flush();

    // read response
    in.readFully(buf, 0, datasize);

    nanos[i] = System.nanoTime() - start;
  }

  private void writeInt(OutputStream out, int v) throws IOException {
    byte[] buf = this.buf;
    buf[0] = (byte) (v >>> 24);
    buf[1] = (byte) (v >>> 16);
    buf[2] = (byte) (v >>> 8);
    buf[3] = (byte) (v >>> 0);
    out.write(buf, 0, 4);
  }

  private void dumpResults(OutputStream out) throws IOException {
    PrintWriter w = new PrintWriter(out);
    for (int i = 0; i < nanos.length; i++) {
      w.println(nanos[i] / 1000.0);
    }
    w.flush();
    w.close();
  }

  public static void main(String[] args) throws IOException {
    int iters = 100000, data = 1024;
    int port = Integer.parseInt(args[0]);
    boolean jock = args[1].equals("jocket");
    new BenchClient(iters, data, jock, port).bench();
  }

}
