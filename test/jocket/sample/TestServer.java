package jocket.sample;

import java.io.IOException;

import jocket.net.Jocket;
import jocket.net.ServerJocket;

public class TestServer {

	public static void main(String[] args) throws NumberFormatException,
	    IOException {

		@SuppressWarnings("resource")
		ServerJocket srv = new ServerJocket(Integer.parseInt(args[0]));

		while (true) {
			pipeToStdInOut(srv.accept());
		}
	}

	public static void pipeToStdInOut(final Jocket s) {
		new Thread("Reader") {
			@Override
			public void run() {
				try {
					new ReadLoop(s.getReader(), System.out);
				} catch (IOException e) {
					s.close();
				}
			}
		}.start();

		new Thread("Writer") {
			@Override
			public void run() {
				try {
					new WriteLoop(s.getWriter(), System.in);
				} catch (IOException e) {
					s.close();
				}
			}
		}.start();
	}
}
