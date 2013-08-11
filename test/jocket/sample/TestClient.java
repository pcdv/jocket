package jocket.sample;

import java.io.IOException;

import jocket.net.Jocket;

public class TestClient {

	public static void main(String[] args) throws NumberFormatException,
	    IOException {
		TestServer.pipeToStdInOut(new Jocket(Integer.parseInt(args[0])));
	}

}
