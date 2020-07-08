package jp.tkms.waffle.data;

import jp.tkms.waffle.Constants;
import jp.tkms.waffle.collector.RubyResultCollector;
import jp.tkms.waffle.data.log.ErrorLogMessage;
import jp.tkms.waffle.data.util.ResourceFile;
import jp.tkms.waffle.extractor.RubyParameterExtractor;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.revwalk.RevCommit;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.*;

public class Simulator extends ProjectData implements DataDirectory {
  public static final String KEY_SIMULATOR = "simulator";
  public static final String KEY_EXTRACTOR = "extractor";
  public static final String KEY_COMMAND_ARGUMENTS = "command arguments";
  public static final String KEY_COLLECTOR = "collector";
  public static final String KEY_OUTPUT_JSON = "_output.json";
  private static final String KEY_DEFAULT_PARAMETERS = "default_parameters";
  public static final String KEY_TESTRUN = "testrun";

  public static final String KEY_MASTER = "master";
  public static final String KEY_REMOTE = "REMOTE";

  protected static final String TABLE_NAME = "simulator";
  private static final String KEY_SIMULATION_COMMAND = "simulation_command";

  private static final HashMap<String, Simulator> instanceMap = new HashMap<>();

  private String simulationCommand = null;
  private String defaultParameters = null;
  private String versionId = null;

  private static final Object gitObjectLocker = new Object();

  public Simulator(Project project, UUID id, String name) {
    super(project, id, name);
  }

  public Simulator(Project project) {
    super(project);
  }

  public static Path getBaseDirectoryPath(Project project) {
    return project.getDirectoryPath().resolve(KEY_SIMULATOR);
  }

  @Override
  protected Path getPropertyStorePath() {
    return getDirectoryPath().resolve(KEY_SIMULATOR + Constants.EXT_JSON);
  }

  /*
  public static Simulator getInstance(Project project, String id) {
    DataId dataId = DataId.getInstance(id);
    return instanceMap.get(dataId.getId());
  }
   */

  public static Simulator getInstanceByName(Project project, String name) {
    Simulator simulator = null;

    simulator = instanceMap.get(name);
    if (simulator != null) {
      return simulator;
    }

    if (simulator == null && Files.exists(getBaseDirectoryPath(project).resolve(name))) {
      simulator = create(project, name);
    }

    return simulator;
  }

  public static Simulator find(Project project, String key) {
    /*
    if (key.matches("[0-9A-Fa-f]{8}-[0-9A-Fa-f]{4}-[0-9A-Fa-f]{4}-[0-9A-Fa-f]{4}-[0-9A-Fa-f]{12}")) {
      return getInstance(project, key);
    }

     */
    return getInstanceByName(project, key);
  }

  public static ArrayList<Simulator> getList(Project project) {
    ArrayList<Simulator> simulatorList = new ArrayList<>();

    try {
      Files.list(getBaseDirectoryPath(project)).forEach(path -> {
        if (Files.isDirectory(path)) {
          simulatorList.add(getInstanceByName(project, path.getFileName().toString()));
        }
      });
    } catch (IOException e) {
      e.printStackTrace();
    }

    /*
    handleDatabase(new Simulator(project), new Handler() {
      @Override
      void handling(Database db) throws SQLException {
        ResultSet resultSet = db.executeQuery("select id,name from " + TABLE_NAME + ";");
        while (resultSet.next()) {
          simulatorList.add(new Simulator(
            project,
            UUID.fromString(resultSet.getString("id")),
            resultSet.getString("name"))
          );
        }
      }
    });
    */

    return simulatorList;
  }

  public static Simulator create(Project project, String name) {
    /*
    DataId dataId = DataId.getInstance(Host.class, getBaseDirectoryPath(project).resolve(name));
    Simulator simulator = new Simulator(project, dataId.getUuid(), name);
     */
    Simulator simulator = new Simulator(project, UUID.randomUUID(), name);

    try {
      Files.createDirectories(simulator.getDirectoryPath());
      Files.createDirectories(simulator.getBinDirectory());
    } catch (IOException e) {
      e.printStackTrace();
    }

    if (simulator.getSimulationCommand() == null) {
      simulator.setSimulatorCommand("");
    }

    if (simulator.getExtractorNameList() == null) {
      simulator.createExtractor(KEY_COMMAND_ARGUMENTS);
      simulator.updateExtractorScript(KEY_COMMAND_ARGUMENTS, ResourceFile.getContents("/default_parameter_extractor.rb"));
    }

    if (simulator.getCollectorNameList() == null) {
      simulator.createCollector(KEY_OUTPUT_JSON);
      simulator.updateCollectorScript(KEY_OUTPUT_JSON, ResourceFile.getContents("/default_result_collector.rb"));
    }

    if (! Files.exists(simulator.getDirectoryPath().resolve(".git"))) {
      simulator.initializeGit();
    }

    instanceMap.put(name, simulator);

    return simulator;
  }

  private void initializeGit() {
      try {
        synchronized (gitObjectLocker) {
          Git git = Git.init().setDirectory(getDirectoryPath().toFile()).call();
          git.add().addFilepattern(".").call();
          git.commit().setMessage("Initial").setAuthor("waffle", "waffle@tkms.jp").call();
          git.branchCreate().setName(KEY_REMOTE).call();
          git.merge().include(git.getRepository().findRef(KEY_MASTER)).setMessage("Merge master").call();
          git.checkout().setName(KEY_MASTER).call();
        }
      } catch (Exception e) {
        e.printStackTrace();
      }
  }

  public synchronized void updateVersionId() {
    try{
      synchronized (gitObjectLocker) {
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
          }
        }
        git.log().setMaxCount(1).call().forEach(c -> c.getId());
      }
    } catch (GitAPIException | IOException e) {
      e.printStackTrace();
    }

    getVersionId();
  }

  public String getVersionId() {
    if (versionId == null) {
      try{
        synchronized (gitObjectLocker) {
          if (!Files.exists(getDirectoryPath().resolve(".git"))) {
            initializeGit();
          }

          Git git = Git.open(getDirectoryPath().toFile());
          git.checkout().setName(KEY_REMOTE).call();
          RevCommit commit = git.log().setMaxCount(1).call().iterator().next();
          versionId = commit.getId().getName();
          git.checkout().setName(KEY_MASTER).call();
        }
      } catch (Exception e) {
        ErrorLogMessage.issue(e);
      }
    }
    return versionId;
  }

  @Override
  public Path getDirectoryPath() {
    return getBaseDirectoryPath(getProject()).resolve(name);
  }

  public Path getBinDirectory() {
    return getDirectoryPath().resolve(KEY_REMOTE).toAbsolutePath();
  }

  public String getSimulationCommand() {
    try {
      if (simulationCommand == null) {
        simulationCommand = getStringFromProperty(KEY_SIMULATION_COMMAND);
      }
    } catch (Exception e) {}
    return simulationCommand;
  }

  public void setSimulatorCommand(String command) {
    simulationCommand = command;
    setToProperty(KEY_SIMULATION_COMMAND, simulationCommand);
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
      JSONObject object = new JSONObject(json);
      updateFileContents(KEY_DEFAULT_PARAMETERS + Constants.EXT_JSON, object.toString(2));
    } catch (Exception e) {
      e.printStackTrace();
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

  public SimulatorRun runTest(Host host, String parametersJsonText) {
    String baseRunName = "TESTRUN-" + name;
    RunNode runNode = Workspace.getInstanceByName(getProject(), baseRunName);
    Actor baseRun = Actor.create(runNode, null, null);
    SimulatorRun run = SimulatorRun.create(runNode.createSimulatorRunNode(LocalDateTime.now().toString()), baseRun, this, host);
    setToProperty(KEY_TESTRUN, run.getId());
    run.putParametersByJson(parametersJsonText);
    run.start();
    return run;
  }

  public SimulatorRun getLatestTestRun() {
    String baseRunName = "TESTRUN-" + name;
    Workspace workspace = Workspace.getInstanceByName(getProject(), baseRunName);
    return SimulatorRun.getInstance(workspace, getStringFromProperty(KEY_TESTRUN));
  }

}
