package jocket.sample;

import java.io.IOException;

import jocket.net.JocketSocket;

public class TestClient {

	public static void main(String[] args) throws NumberFormatException,
	    IOException {
		TestServer.pipeToStdInOut(new JocketSocket(Integer.parseInt(args[0])));
	}

}
