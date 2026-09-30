package jocket.impl;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;

/**
 * Identifies the processes at both ends of a buffer, so that each end can
 * detect the death of the other (e.g. killed with SIGKILL, which gives it no
 * chance to close anything).
 */
final class Processes {

  static final long PID = ProcessHandle.current().pid();

  /**
   * Identifier of the PID namespace (Linux) of the current process, 0 if
   * unknown. A process cannot check the pid of a process of another namespace
   * (e.g. another container sharing /dev/shm): the same number designates
   * another process, or none.
   */
  static final long NAMESPACE = namespace();

  private Processes() {
  }

  /**
   * Returns false if the process with specified pid is known to be dead.
   */
  static boolean isAlive(long pid, long namespace) {
    if (pid == 0 || namespace != NAMESPACE)
      return true; // cannot tell
    return ProcessHandle.of(pid).map(ProcessHandle::isAlive).orElse(false);
  }

  private static long namespace() {
    try {
      // e.g. "pid:[4026531836]"
      String link = Files.readSymbolicLink(Paths.get("/proc/self/ns/pid")).toString();
      return Long.parseLong(link.substring(link.indexOf('[') + 1, link.indexOf(']')));
    }
    catch (IOException | RuntimeException e) {
      return 0;
    }
  }
}
