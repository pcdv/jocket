package jocket.test;

import java.io.OutputStream;

import jocket.net.JocketSocket;

/**
 * Peer run in another process by TestPeerDeath: connects to the port given as
 * argument, sends one byte, then waits to be killed without reading anything.
 */
public class PeerProcess {

  public static void main(String[] args) throws Exception {
    JocketSocket s = new JocketSocket(Integer.parseInt(args[0]));
    OutputStream out = s.getOutputStream();
    out.write('r');
    out.flush();
    Thread.sleep(Long.MAX_VALUE);
  }
}
