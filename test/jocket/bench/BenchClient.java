package jocket.bench;

import java.io.BufferedOutputStream;
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
		fileName = "Jocket";
		JocketSocket s = new JocketSocket(port);
		in = new DataInputStream(s.getInputStream());
		out = new DataOutputStream(s.getOutputStream());
		buf = new byte[datasize];
	}

	private void initSocket() throws IOException {
		fileName = "Socket";
		@SuppressWarnings("resource")
		Socket s = new Socket("localhost", port);
		s.setTcpNoDelay(true);
		in = new DataInputStream(s.getInputStream());
		out = new DataOutputStream(new BufferedOutputStream(s.getOutputStream()));
		buf = new byte[datasize];
	}

	public void bench() throws IOException {
		out.writeInt(niter);
		out.flush();
		for (int i = 0; i < niter; i++) {
			iter(i);
		}
		dumpResults(new FileOutputStream("/tmp/" + fileName));
	}

	public void iter(int i) throws IOException {
		// if (i % 1000 == 0)
		// System.out.println(i);
		long start = System.nanoTime();
		out.writeInt(datasize);
		// out.write(buf, 0, datasize);
		out.flush();
		in.readFully(buf, 0, datasize);
		long stop = System.nanoTime();
		nanos[i] = stop - start;
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
		int iters = 50000, data = 1024;
		int port = Integer.parseInt(args[0]);
		boolean jock = args[1].equals("jocket");
		new BenchClient(iters, data, jock, port).bench();
	}

}
