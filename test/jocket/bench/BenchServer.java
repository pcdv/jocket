package jocket.bench;

import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;

import jocket.net.JocketSocket;
import jocket.net.ServerJocket;

/**
 * A standalone benchmark running the both client and server.
 * 
 * @author pcdv
 */
public class BenchServer {

	private final int port;

	public BenchServer(int port, boolean useJocket) throws IOException {
		this.port = port;
		if (useJocket)
			initJocket();
		else
			initSocket();
	}

	private void initJocket() throws IOException {
		ServerJocket srv = new ServerJocket(port);
		System.out.println("Jocket listening on " + srv.getLocalPort());
		JocketSocket s = srv.accept();
		srv.close();
		answerLoop(s.getInputStream(), s.getOutputStream());
	}

	private void initSocket() throws IOException {
		ServerSocket srv = new ServerSocket(port);
		System.out.println("Java ServerSocket listening on " + srv.getLocalPort());
		Socket s = srv.accept();
		srv.close();
		s.setTcpNoDelay(true);
		answerLoop(s.getInputStream(),
		    new BufferedOutputStream(s.getOutputStream()));
	}

	protected void answerLoop(InputStream in, OutputStream out)
	    throws IOException {
		DataInputStream din = new DataInputStream(in);
		int niter = din.readInt();
		byte[] buf = new byte[1024 * 1024];
		for (int i = 0; i < niter; i++) {
			int size = din.readInt();
			out.write(buf, 0, size);
			out.flush();
		}
	}

	public static void main(String[] args) throws IOException {
		int port = Integer.parseInt(args[0]);
		boolean jock = args[1].equals("jocket");
		new BenchServer(port, jock);
	}
}
