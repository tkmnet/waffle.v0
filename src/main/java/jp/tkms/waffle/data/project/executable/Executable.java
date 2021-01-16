package jp.tkms.waffle.data.project.executable;

import jp.tkms.waffle.Constants;
import jp.tkms.waffle.collector.RubyResultCollector;
import jp.tkms.waffle.data.*;
import jp.tkms.waffle.data.computer.Computer;
import jp.tkms.waffle.data.project.Project;
import jp.tkms.waffle.data.project.ProjectData;
import jp.tkms.waffle.data.project.workspace.run.ExecutableRun;
import jp.tkms.waffle.data.util.ChildElementsArrayList;
import jp.tkms.waffle.exception.RunNotFoundException;
import jp.tkms.waffle.data.log.message.ErrorLogMessage;
import jp.tkms.waffle.data.util.FileName;
import jp.tkms.waffle.data.util.ResourceFile;
import jp.tkms.waffle.extractor.RubyParameterExtractor;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

public class Executable extends ProjectData implements DataDirectory, PropertyFile {
  public static final String EXECUTABLE = "EXECUTABLE";
  public static final String KEY_EXTRACTOR = "EXTRACTOR";
  public static final String KEY_COMMAND_ARGUMENTS = "command arguments";
  public static final String KEY_COLLECTOR = "COLLECTOR";
  public static final String KEY_OUTPUT_JSON = "_output.json";
  private static final String KEY_DEFAULT_PARAMETERS = "DEFAULT_PARAMETERS";
  public static final String KEY_TESTRUN = "testrun";
  private static final String KEY_REQUIRED_THREAD = "required_thread";
  private static final String KEY_REQUIRED_MEMORY = "required_memory";

  public static final String BASE = "BASE";

  private static final String KEY_COMMAND = "command";

  private String name = null;
  private String command = null;
  private String defaultParameters = null;
  private String versionId = null;
  private Double requiredThread = null;
  private Double requiredMemory = null;
  private long lastGitCheckTimestamp = 0;

  public Executable(Project project, String name) {
    super(project);
    this.name = name;
    initialise();
  }

  public String getName() {
    return name;
  }

  public static Path getBaseDirectoryPath(Project project) {
    return project.getDirectoryPath().resolve(EXECUTABLE);
  }

  @Override
  public Path getPropertyStorePath() {
    return getDirectoryPath().resolve(EXECUTABLE + Constants.EXT_JSON);
  }

  public static Executable getInstance(Project project, String name) {
    if (name != null && !name.equals("") && Files.exists(getBaseDirectoryPath(project).resolve(name))) {
      return new Executable(project, name);
    }
    return null;
  }

  public static Executable find(Project project, String key) {
    return getInstance(project, key);
  }

  public static ArrayList<Executable> getList(Project project) {
    return new ChildElementsArrayList().getList(getBaseDirectoryPath(project), name -> {
      return getInstance(project, name.toString());
    });
  }

  public static Executable create(Project project, String name) {
    name = FileName.removeRestrictedCharacters(name);

    Executable executable = getInstance(project, name);
    if (executable == null) {
      executable = new Executable(project, name);
    }

    return executable;
  }

  private void initialise() {
    try {
      Files.createDirectories(getDirectoryPath());
      Files.createDirectories(getBaseDirectory());
    } catch (IOException e) {
      e.printStackTrace();
    }

    if (getCommand() == null) {
      setSimulatorCommand("");
    }

    if (getExtractorNameList() == null) {
      createExtractor(KEY_COMMAND_ARGUMENTS);
      updateExtractorScript(KEY_COMMAND_ARGUMENTS, ResourceFile.getContents("/default_parameter_extractor.rb"));
    }

    if (getCollectorNameList() == null) {
      createCollector(KEY_OUTPUT_JSON);
      updateCollectorScript(KEY_OUTPUT_JSON, ResourceFile.getContents("/default_result_collector.rb"));
    }

    /*
    if (! Files.exists(getDirectoryPath().resolve(".git"))) {
      initializeGit();
    }
     */
  }

  /*
  private String initializeGit() {
    synchronized (this) {
      try {
        Path gitPath = getDirectoryPath().resolve(".git");
        if (Files.exists(gitPath)) {
          deleteDirectory(gitPath.toFile());
        }
        Git git = Git.init().setDirectory(getDirectoryPath().toFile()).call();
        git.add().addFilepattern(".").call();
        git.commit().setMessage("Initial").setAuthor("waffle", "waffle@tkms.jp").call();
        git.branchCreate().setName(KEY_REMOTE).call();
        git.merge().include(git.getRepository().findRef(KEY_MASTER)).setMessage("Merge master").call();
        RevCommit commit = git.log().setMaxCount(1).call().iterator().next();
        versionId = commit.getId().getName();
        git.checkout().setName(KEY_MASTER).call();
        git.close();
      } catch (Exception e) {
        ErrorLogMessage.issue(e);
      }
      return versionId;
    }
  }
   */

  private void deleteDirectory(File file) {
    File[] contents = file.listFiles();
    if (contents != null) {
      for (File f : contents) {
        deleteDirectory(f);
      }
    }
    file.delete();
  }

  public synchronized void updateVersionId() {
    /*
    if (lastGitCheckTimestamp + 2000 > System.currentTimeMillis()) {
      lastGitCheckTimestamp = System.currentTimeMillis();
      return;
    }
    synchronized (this) {
      try{
        Path gitIndexPath = getDirectoryPath().resolve(".git").resolve("index.lock");
        if (Files.exists(gitIndexPath)) {
          Files.delete(gitIndexPath);
          WarnLogMessage.issue("Remove git index lock file in " + getName() + " " + this);
        }

        Git git = Git.open(getDirectoryPath().toFile());
        git.add().addFilepattern(".").call();

        for (String missing : git.status().call().getMissing()) {
          git.rm().addFilepattern(missing).call();
        }

        if (!git.status().call().isClean()) {
          Set<String> changed = new HashSet<>();
          changed.addAll(git.status().addPath(KEY_REMOTE).call().getAdded());
          changed.addAll(git.status().addPath(KEY_REMOTE).call().getModified());
          changed.addAll(git.status().addPath(KEY_REMOTE).call().getRemoved());
          changed.addAll(git.status().addPath(KEY_REMOTE).call().getChanged());

          git.commit().setMessage((changed.isEmpty() ? "" : "R ") + LocalDateTime.now()).setAuthor("waffle", "waffle@tkms.jp").call();

          if (!changed.isEmpty()) {
            git.checkout().setName(KEY_REMOTE).call();
            git.merge().include(git.getRepository().findRef(KEY_MASTER)).setMessage("Merge master").call();
            git.checkout().setName(KEY_MASTER).call();

            versionId = null;
          }
          git.log().setMaxCount(1).call().forEach(c -> c.getId());
        }
        git.close();
      } catch (GitAPIException | IOException e) {
        ErrorLogMessage.issue(e);

        initializeGit();
      }
      getVersionId();
      lastGitCheckTimestamp = System.currentTimeMillis();
    }
     */
  }

  /*
  public String getVersionId() {
    synchronized (this) {
      if (versionId == null) {
        try{
          if (!Files.exists(getDirectoryPath().resolve(".git"))) {
            initializeGit();
          }

          Git git = Git.open(getDirectoryPath().toFile());
          git.checkout().setName(KEY_REMOTE).call();
          RevCommit commit = git.log().setMaxCount(1).call().iterator().next();
          versionId = commit.getId().getName();
          git.checkout().setName(KEY_MASTER).call();
          git.close();
        } catch (Exception e) {
          ErrorLogMessage.issue(e);

          versionId = initializeGit();
        }
      }
      return versionId;
    }
  }
   */

  @Override
  public Path getDirectoryPath() {
    return getBaseDirectoryPath(getProject()).resolve(name);
  }

  public Path getBaseDirectory() {
    return getDirectoryPath().resolve(BASE).toAbsolutePath();
  }

  public String getCommand() {
    try {
      if (command == null) {
        command = getStringFromProperty(KEY_COMMAND);
      }
    } catch (Exception e) {}
    return command;
  }

  public void setSimulatorCommand(String command) {
    this.command = command;
    setToProperty(KEY_COMMAND, this.command);
  }

  public Double getRequiredThread() {
    try {
      if (requiredThread == null) {
        requiredThread = getDoubleFromProperty(KEY_REQUIRED_THREAD, 1.0);
      }
    } catch (Exception e) {}
    return requiredThread;
  }

  public void setRequiredThread(double num) {
    requiredThread = num;
    setToProperty(KEY_REQUIRED_THREAD, requiredThread);
  }

  public Double getRequiredMemory() {
    try {
      if (requiredMemory == null) {
        requiredMemory = getDoubleFromProperty(KEY_REQUIRED_MEMORY, 1.0);
      }
    } catch (Exception e) {}
    return requiredMemory;
  }

  public void setRequiredMemory(double num) {
    requiredMemory = num;
    setToProperty(KEY_REQUIRED_MEMORY, requiredMemory);
  }

  public JSONObject getDefaultParameters() {
    if (defaultParameters == null) {
      defaultParameters = getFileContents(KEY_DEFAULT_PARAMETERS + Constants.EXT_JSON);
      if (defaultParameters.equals("")) {
        defaultParameters = "{}";
        createNewFile(KEY_DEFAULT_PARAMETERS + Constants.EXT_JSON);
        updateFileContents(KEY_DEFAULT_PARAMETERS + Constants.EXT_JSON, defaultParameters);
      }
    }
    return new JSONObject(defaultParameters);
  }

  public void setDefaultParameters(String json) {
    try {
      defaultParameters = new JSONObject(json).toString(2);
      updateFileContents(KEY_DEFAULT_PARAMETERS + Constants.EXT_JSON, defaultParameters);
    } catch (Exception e) {
      ErrorLogMessage.issue(e);
    }
  }

  public Path getExtractorScriptPath(String name) {
    return getDirectoryPath().resolve(KEY_EXTRACTOR).resolve(name + Constants.EXT_RUBY).toAbsolutePath();
  }

  public void createExtractor(String name) {
    Path path = getExtractorScriptPath(name);
    Path dirPath = path.getParent();
    if (! Files.exists(dirPath)) {
      try {
        Files.createDirectories(dirPath);
      } catch (IOException e) {
        e.printStackTrace();
      }
    }

    if (! Files.exists(path)) {
      try {
        FileWriter filewriter = new FileWriter(path.toFile());
        filewriter.write(new RubyParameterExtractor().contentsTemplate());
        filewriter.close();
      } catch (IOException e) {
        e.printStackTrace();
      }
    }

    putToArrayOfProperty(KEY_EXTRACTOR, name);
  }

  public void removeExtractor(String name) {
    removeFromArrayOfProperty(KEY_EXTRACTOR, name);
  }

  public void updateExtractorScript(String name, String script) {
    Path path = getExtractorScriptPath(name);
    if (Files.exists(path)) {
      try {
        FileWriter filewriter = new FileWriter(path.toFile());
        filewriter.write(script);
        filewriter.close();
      } catch (IOException e) {
        e.printStackTrace();
      }
    }
  }

  public String getExtractorScript(String name) {
    String script = "";

    Path path = getExtractorScriptPath(name);
    if (Files.exists(path)) {
      try {
        script = new String(Files.readAllBytes(path));
      } catch (IOException e) {
      }
    }

    return script;
  }

  public List<String> getExtractorNameList() {
    List<String> list = null;
    try {
      JSONArray array = getArrayFromProperty(KEY_EXTRACTOR);
      list = Arrays.asList(array.toList().toArray(new String[array.toList().size()]));
      for (String name : list) {
        if (! Files.exists(getExtractorScriptPath(name))) {
          removeFromArrayOfProperty(KEY_EXTRACTOR, name);
        }
      }
    } catch (JSONException e) {
    }
    return list;
  }

  public List<String> getCollectorNameList() {
    List<String> list = null;
    try {
      JSONArray array = getArrayFromProperty(KEY_COLLECTOR);
      list = Arrays.asList(array.toList().toArray(new String[array.toList().size()]));
      for (String name : list) {
        if (! Files.exists(getCollectorScriptPath(name))) {
          removeFromArrayOfProperty(KEY_COLLECTOR, name);
        }
      }
    } catch (JSONException e) {
    }
    return list;
  }

  public Path getCollectorScriptPath(String name) {
    return getDirectoryPath().resolve(KEY_COLLECTOR).resolve(name + Constants.EXT_RUBY).toAbsolutePath();
  }

  public void createCollector(String name) {
    Path path = getCollectorScriptPath(name);
    Path dirPath = getCollectorScriptPath(name).getParent();
    if (! Files.exists(dirPath)) {
      try {
        Files.createDirectories(dirPath);
      } catch (IOException e) {
        e.printStackTrace();
      }
    }

    if (! Files.exists(path)) {
      try {
        FileWriter filewriter = new FileWriter(path.toFile());
        filewriter.write(new RubyResultCollector().contentsTemplate());
        filewriter.close();
      } catch (IOException e) {
        e.printStackTrace();
      }
    }

    putToArrayOfProperty(KEY_COLLECTOR, name);
  }

  public void removeCollector(String name) {
    removeFromArrayOfProperty(KEY_COLLECTOR, name);
  }

  public void updateCollectorScript(String name, String script) {
    Path path = getCollectorScriptPath(name);
    if (Files.exists(path)) {
      try {
        FileWriter filewriter = new FileWriter(path.toFile());
        filewriter.write(script);
        filewriter.close();
      } catch (IOException e) {
        e.printStackTrace();
      }
    }
  }

  public String getCollectorScript(String name) {
    String script = "";

    Path path = getCollectorScriptPath(name);
    if (Files.exists(path)) {
      try {
        script = new String(Files.readAllBytes(path));
      } catch (IOException e) {
      }
    }

    return script;
  }

  public ExecutableRun postTestRun(Computer computer, String parametersJsonText) {
    ExecutableRun executableRun = ExecutableRun.createTestRun(this, computer);
    executableRun.putParametersByJson(parametersJsonText);
    setToProperty(KEY_TESTRUN, executableRun.getLocalDirectoryPath().toString());
    executableRun.start();
    return executableRun;
  }

  public ExecutableRun getLatestTestRun() throws RunNotFoundException {
    return ExecutableRun.getInstance(getStringFromProperty(KEY_TESTRUN));
  }

  JSONObject propertyStoreCache = null;
  @Override
  public JSONObject getPropertyStoreCache() {
    return propertyStoreCache;
  }
  @Override
  public void setPropertyStoreCache(JSONObject cache) {
    propertyStoreCache = cache;
  }
}