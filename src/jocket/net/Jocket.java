package jocket.net;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.IOException;
import java.net.InetAddress;
import java.net.Socket;

import jocket.impl.JocketReader;
import jocket.impl.JocketWriter;

public class Jocket {

	private final JocketReader reader;
	private final JocketWriter writer;

	public Jocket(JocketReader reader, JocketWriter writer) {
		this.reader = reader;
		this.writer = writer;
	}

	public Jocket(int port) throws IOException {
		Socket s = new Socket(InetAddress.getLoopbackAddress(), port);
		DataOutputStream out = new DataOutputStream(s.getOutputStream());
		out.writeInt(ServerJocket.MAGIC);

		DataInputStream in = new DataInputStream(s.getInputStream());
		File r = new File(in.readUTF());
		File w = new File(in.readUTF());

		JocketFactory jfr = new JocketFactory(r, false);
		JocketFactory jfw = new JocketFactory(w, false);

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
		// TODO Auto-generated method stub

	}

}
