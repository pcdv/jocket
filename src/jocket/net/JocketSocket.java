package jocket.net;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.IOException;
import java.net.InetAddress;
import java.net.Socket;

import jocket.impl.JocketReader;
import jocket.impl.JocketWriter;

public class JocketSocket {

	private final JocketReader reader;
	private final JocketWriter writer;

	public JocketSocket(JocketReader reader, JocketWriter writer) {
		this.reader = reader;
		this.writer = writer;
		new CloseObserver(reader, writer);
	}

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
	}

	public JocketReader getReader() {
		return reader;
	}

	public JocketWriter getWriter() {
		return writer;
	}

	public void close() {
		reader.close();
	}

}
