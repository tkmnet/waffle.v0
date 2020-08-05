package jp.tkms.waffle.submitter;

import jp.tkms.waffle.data.Host;
import jp.tkms.waffle.data.Job;
import jp.tkms.waffle.data.SimulatorRun;
import jp.tkms.waffle.data.exception.FailedToControlRemoteException;
import jp.tkms.waffle.data.exception.FailedToTransferFileException;
import jp.tkms.waffle.data.exception.RunNotFoundException;
import jp.tkms.waffle.data.log.ErrorLogMessage;
import jp.tkms.waffle.data.log.InfoLogMessage;
import org.json.JSONObject;

import java.io.*;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class LocalSubmitter extends AbstractSubmitter {

  public LocalSubmitter(Host host) {
  }

  @Override
  public AbstractSubmitter connect(boolean retry) {
    return this;
  }

  @Override
  public boolean isConnected() {
    return true;
  }

  @Override
  public void close() {
  }

  @Override
  public Path parseHomePath(String pathString) throws FailedToControlRemoteException {
    if (pathString.indexOf('~') == 0) {
      pathString = pathString.replaceAll("^~", System.getProperty("user.home"));
    }
    return Paths.get(pathString);
  }

  @Override
  public void createDirectories(Path path) throws FailedToControlRemoteException {
    try {
      Files.createDirectories(path);
    } catch (IOException e) {
      throw new FailedToControlRemoteException(e);
    }
  }

  @Override
  public String exec(String command) {
    String result = "";
    ProcessBuilder p = new ProcessBuilder("sh", "-c", command);
    p.redirectErrorStream(true);

    try {
      Process process = p.start();

      try (BufferedReader r
             = new BufferedReader(new InputStreamReader(process.getInputStream(), Charset.defaultCharset()))) {
        String line;
        while ((line = r.readLine()) != null) {
          result += line + "\n";
        }
      }

    } catch (IOException e) {
      ErrorLogMessage.issue(e);
    }

    return result;
  }

  @Override
  boolean exists(Path path) {
    return Files.exists(path);
  }

  @Override
  public void putText(Job job, Path path, String text) throws FailedToTransferFileException, RunNotFoundException {
    try {
      PrintWriter pw = new PrintWriter(new BufferedWriter(
        new FileWriter(getRunDirectory(job.getRun()) + File.separator + path)
      ));
      pw.println(text);
      pw.close();
    } catch (IOException | FailedToControlRemoteException e) {
      throw new FailedToTransferFileException(e);
    }
  }

  @Override
  public String getFileContents(SimulatorRun run, Path path) throws FailedToTransferFileException {
    String result = null;
    try {
      result = exec("cat " + getContentsPath(run, path));
    } catch (FailedToControlRemoteException e) {
      throw new FailedToTransferFileException(e);
    }
    return result;
  }

  @Override
  public void transferFilesToRemote(Path localPath, Path remotePath) throws FailedToTransferFileException {
    try {
      Files.createDirectories(remotePath.getParent());
      if (Files.isDirectory(localPath)) {
        transferDirectory(localPath.toFile(), remotePath.toFile());
      } else {
        Files.copy(localPath, remotePath);
      }
    } catch (IOException e) {
      throw new FailedToTransferFileException(e);
    }
  }

  @Override
  public void transferFilesFromRemote(Path remotePath, Path localPath) throws FailedToTransferFileException {
    try {
      Files.createDirectories(localPath.getParent());
      if (Files.isDirectory(remotePath)) {
        transferDirectory(remotePath.toFile(), localPath.toFile());
      } else {
        Files.copy(remotePath, localPath);
      }
    } catch (IOException e) {
      throw new FailedToTransferFileException(e);
    }
  }

  @Override
  public JSONObject getDefaultParameters(Host host) {
    return new JSONObject();
  }

  void transferDirectory(File src, File dest) throws IOException {
    if (src.isDirectory()) {
      if (!dest.exists()) {
        dest.mkdir();
      }
      String files[] = src.list();
      for (String file : files) {
        File srcFile = new File(src, file);
        File destFile = new File(dest, file);
        transferDirectory(srcFile, destFile);
      }
    }else{
      Files.copy(src.toPath(), dest.toPath());
    }
  }

  public static void deleteDirectory(final String dirPath) throws Exception {
    File file = new File(dirPath);
    recursiveDeleteFile(file);
  }

  private static void recursiveDeleteFile(final File file) throws Exception {
    if (!file.exists()) {
      return;
    }
    if (file.isDirectory()) {
      for (File child : file.listFiles()) {
        recursiveDeleteFile(child);
      }
    }
    file.delete();
  }
}
