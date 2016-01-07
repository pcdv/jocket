package jocket.bench;

import java.io.IOException;

import jocket.net.JocketSocket;
import jocket.net.ServerJocket;

/**
 * https://github.com/pcdv/jocket/issues/5
 */
public class TestX {
  public static void main(String[] args) {
    try {
      final ServerJocket ssocket = new ServerJocket(2000, 1024, 134217728);
      new Thread(new Runnable() {
        @Override
        public void run() {
          while (true) {
            try {
              final JocketSocket accept = ssocket.accept();
              if (accept == null) {
                return;
              }
              else {
                accept.close();
              }
            }
            catch (IOException e) {
              e.printStackTrace();
            }
            catch (Exception e) {
              e.printStackTrace();
            }
          }
        }
      }).start();
      try {
        Thread.sleep(1000);
      }
      catch (InterruptedException e) {
        e.printStackTrace();
      }
      for (int i = 0; i < 10000; i++) {
        JocketSocket sock = new JocketSocket(2000);
        sock.close();
      }
    }
    catch (IOException e) {
      e.printStackTrace();
    }
  }
}