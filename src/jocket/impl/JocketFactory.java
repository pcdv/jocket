package jocket.impl;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel.MapMode;

public class JocketFactory {

	private final MappedByteBuffer buf;
	private final RandomAccessFile io;
	private JocketReader r;
	private JocketWriter w;
	private final int npackets;
	private final int size;
	private final File file;

	public JocketFactory() throws IOException {
		// this(File.createTempFile("jocket", ""));
		this(new File("/tmp/jocket"), true);
		file.deleteOnExit();
	}

	public JocketFactory(File file, boolean reset) throws IOException {
		this.file = file;
		this.io = new RandomAccessFile(file, "rw");
		npackets = 1024;
		size = 1024 * 1024 + Const.PACKET_INFO + npackets * Const.LEN_PACKET_INFO;
		if (reset) {
			io.setLength(0);
			io.setLength(size);
		}
		buf = io.getChannel().map(MapMode.READ_WRITE, 0, size);
	}

	public JocketReader reader() {
		if (r == null)
			r = new JocketReader(buf, npackets);
		return r;
	}

	public JocketWriter writer() {
		if (w == null)
			w = new JocketWriter(buf, npackets);
		return w;
	}

	public String getPath() {
		return file.getAbsolutePath();
	}

	public static void main(String[] args) throws IOException {

		if (args.length == 0) {
			JocketFactory jf = new JocketFactory();
			System.out.println("Listening on " + jf.getPath());
			new ReadLoop(jf.reader());
		} else {
			JocketFactory jf = new JocketFactory(new File(args[0]), false);
			new WriteLoop(jf.writer(), System.in);
		}
	}
}
