package jp.tkms.waffle.sub.communicator;

import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

public class Envelope {
  private static final Charset UTF8 = Charset.forName("UTF-8");
  private static final String MESSAGE_BUNDLE = MessageBundle.class.getSimpleName();
  private static final Path FILES = Paths.get("FILES");
  MessageBundle messageBundle = new MessageBundle();
  ArrayList<Path> filePathList = new ArrayList<>();

  public Envelope() {
    messageBundle = new MessageBundle();
    filePathList = new ArrayList<>();
  }

  public void save(Path path) throws FileNotFoundException {
    try (ZipOutputStream zipOutputStream = new ZipOutputStream(new FileOutputStream(path.toFile()), UTF8)) {
      zipOutputStream.putNextEntry(new ZipEntry(MESSAGE_BUNDLE));
      messageBundle.serialize(zipOutputStream);
      zipOutputStream.closeEntry();



      ZipEntry entry = new ZipEntry(MESSAGE_BUNDLE);
      while ((entry = zipInputStream.getNextEntry()) != null) {
        Path entryPath = destPath.resolve(entry.getName());
        if (entry.isDirectory()) {
          Files.createDirectories(entryPath);
        } else {
          Files.createDirectories(entryPath.getParent());
          try (OutputStream out = new FileOutputStream(entryPath.toFile())){
            IOUtils.copy(zipInputStream, out);
          }
        }
      }
    } catch (Exception e) {
      ErrorLogMessage.issue(e);
    }
  }
}