package jocket.net;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.Socket;
import java.util.Vector;

import jocket.impl.JocketInputStream;
import jocket.impl.JocketOutputStream;
import jocket.impl.JocketReader;
import jocket.impl.JocketWriter;

public class JocketSocket {

	private static Thread hook;
	private static final Vector<JocketSocket> sockets = new Vector<JocketSocket>();
	private final JocketReader reader;
	private final JocketWriter writer;
	private final JocketOutputStream output;
	private final JocketInputStream input;

	JocketSocket(JocketReader reader, JocketWriter writer) {
		this.reader = reader;
		this.writer = writer;
		this.output = new JocketOutputStream(writer);
		this.input = new JocketInputStream(reader);
		addShutdownHook(this);
	}

	/**
	 * Connects to specified port on local host (loopback interface) and returns a
	 * pseudo-socket using shared memory to communicate.
	 * 
	 * @param port
	 * @throws IOException
	 */
	public JocketSocket(int port) throws IOException {
		Socket s = new Socket(InetAddress.getLoopbackAddress(), port);
		DataOutputStream out = new DataOutputStream(s.getOutputStream());
		out.writeInt(ServerJocket.MAGIC);

		DataInputStream in = new DataInputStream(s.getInputStream());
		File r = new File(in.readUTF());
		File w = new File(in.readUTF());

		JocketFile jfr = new JocketFile(r, false);
		JocketFile jfw = new JocketFile(w, false);

		jfr.deleteFile();
		jfw.deleteFile();

		out.writeInt(0);
		s.close();

		this.reader = jfr.reader();
		this.writer = jfw.writer();
		this.output = new JocketOutputStream(writer);
		this.input = new JocketInputStream(reader);

		addShutdownHook(this);
	}

	private synchronized static void addShutdownHook(JocketSocket s) {

		if (hook == null) {
			hook = new Thread("Jocket-shutdown") {
				@Override
				public void run() {
					System.out.println("Shutdown");
					for (JocketSocket s : sockets) {
						s.close();
					}
				}
			};
			Runtime.getRuntime().addShutdownHook(hook);
		}
		sockets.add(s);
	}

	public JocketReader getReader() {
		return reader;
	}

	public JocketWriter getWriter() {
		return writer;
	}

	public OutputStream getOutputStream() {
		return output;
	}

	public InputStream getInputStream() {
		return input;
	}

	public void close() {
		reader.close();
		writer.close();
	}

}
