package jp.tkms.waffle.communicator.util;

import com.jcraft.jsch.Session;
import jp.tkms.utils.value.ObjectWrapper;
import org.apache.sshd.client.session.ClientSession;

import java.util.HashSet;

public class SessionWrapper extends ObjectWrapper<ClientSession> {
  private HashSet<SshSession> sessionSet = new HashSet<>();

  public void link(SshSession sshSession) {
    synchronized (sessionSet) {
      sessionSet.add(sshSession);
    }
  }

  public boolean unlink(SshSession sshSession) {
    synchronized (sessionSet) {
      sessionSet.remove(sshSession);
      if (sessionSet.isEmpty()) {
        set(null);
      }
      return sessionSet.isEmpty();
    }
  }

  public int size() {
    return sessionSet.size();
  }
}
