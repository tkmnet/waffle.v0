package jp.tkms.waffle.submitter;

import jp.tkms.waffle.Constants;
import jp.tkms.waffle.Main;
import jp.tkms.waffle.collector.RubyResultCollector;
import jp.tkms.waffle.data.computer.Computer;
import jp.tkms.waffle.data.job.Job;
import jp.tkms.waffle.data.project.workspace.run.SimulatorRun;
import jp.tkms.waffle.data.log.message.ErrorLogMessage;
import jp.tkms.waffle.data.log.message.InfoLogMessage;
import jp.tkms.waffle.data.log.message.LogMessage;
import jp.tkms.waffle.data.log.message.WarnLogMessage;
import jp.tkms.waffle.data.util.State;
import jp.tkms.waffle.extractor.RubyParameterExtractor;
import jp.tkms.waffle.exception.*;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.lang.reflect.Constructor;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

abstract public class AbstractSubmitter {
  protected static final String RUN_DIR = "run";
  protected static final String SIMULATOR_DIR = "simulator";
  protected static final String BATCH_FILE = "batch.sh";
  protected static final String ARGUMENTS_FILE = "arguments.txt";
  protected static final String EXIT_STATUS_FILE = "exit_status.log";

  protected static ExecutorService threadPool = Executors.newFixedThreadPool(4);
  private int pollingInterval = 5;
  private ArrayList<Job> createdJobList = new ArrayList<>();
  private ArrayList<Job> preparedJobList = new ArrayList<>();
  private ArrayList<Job> submittedJobList = new ArrayList<>();
  private ArrayList<Job> runningJobList = new ArrayList<>();
  private ArrayList<Job> cancelJobList = new ArrayList<>();

  public int getPollingInterval() {
    return pollingInterval;
  }

  public void skipPolling() {
    pollingInterval = 0;
  }

  abstract public AbstractSubmitter connect(boolean retry);
  abstract public boolean isConnected();
  abstract public void close();

  abstract public JSONObject getDefaultParameters(Computer computer);

  abstract public Path parseHomePath(String pathString) throws FailedToControlRemoteException;

  abstract public void createDirectories(Path path) throws FailedToControlRemoteException;
  abstract boolean exists(Path path) throws FailedToControlRemoteException;
  abstract public String exec(String command) throws FailedToControlRemoteException;
  abstract public void putText(Job job, Path path, String text) throws FailedToTransferFileException, RunNotFoundException;
  abstract public String getFileContents(SimulatorRun run, Path path) throws FailedToTransferFileException;
  abstract public void transferFilesToRemote(Path localPath, Path remotePath) throws FailedToTransferFileException;
  abstract public void transferFilesFromRemote(Path remotePath, Path localPath) throws FailedToTransferFileException;

  public AbstractSubmitter connect() {
    return connect(true);
  }

  public void putText(Job job, String pathString, String text) throws FailedToTransferFileException, RunNotFoundException {
    putText(job, Paths.get(pathString), text);
  }

  public static AbstractSubmitter getInstance(Computer computer) {
    AbstractSubmitter submitter = null;
    try {
      Class<?> clazz = Class.forName(computer.getSubmitterType());
      Constructor<?> constructor = clazz.getConstructor(Computer.class);
      submitter = (AbstractSubmitter) constructor.newInstance(new Object[]{computer});
    }catch(Exception e) {
      ErrorLogMessage.issue(e);
    }

    if (submitter == null) {
      submitter = new SshSubmitter(computer);
    }

    return submitter;
  }

  public void submit(Job job) throws RunNotFoundException {
    try {
      if (job.getState().equals(State.Created)) {
        prepareJob(job);
      }
      String execstr =  exec(xsubSubmitCommand(job));
      processXsubSubmit(job, execstr);
    } catch (Exception e) {
      WarnLogMessage.issue(e);
      job.setState(State.Excepted);
    }
  }

  public State update(Job job) throws RunNotFoundException {
    SimulatorRun run = job.getRun();
    try {
      processXstat(job, exec(xstatCommand(job)));
    } catch (FailedToControlRemoteException e) {
      ErrorLogMessage.issue(e);
    }
    return run.getState();
  }

  public void cancel(Job job) throws RunNotFoundException {
    job.setState(State.Canceled);
    if (! job.getJobId().equals("-1")) {
      try {
        processXdel(job, exec(xdelCommand(job)));
      } catch (FailedToControlRemoteException e) {
        ErrorLogMessage.issue(e);
        job.setState(State.Excepted);
      }
    }
  }

  protected void prepareJob(Job job) throws RunNotFoundException, FailedToControlRemoteException, FailedToTransferFileException {
    SimulatorRun run = job.getRun();
    run.setRemoteWorkingDirectoryLog(getRunDirectory(run).toString());

    run.getSimulator().updateVersionId();

    putText(job, BATCH_FILE, makeBatchFileText(job));
    putText(job, EXIT_STATUS_FILE, "-2");

    for (String extractorName : run.getSimulator().getExtractorNameList()) {
      new RubyParameterExtractor().extract(this, run, extractorName);
    }
    putText(job, ARGUMENTS_FILE, makeArgumentFileText(job));
    //putText(run, ENVIRONMENTS_FILE, makeEnvironmentFileText(run));

    if (! exists(getSimulatorBinDirectory(job).toAbsolutePath())) {
      Path binPath = run.getSimulator().getBinDirectory().toAbsolutePath();
      transferFilesToRemote(binPath, getSimulatorBinDirectory(job).toAbsolutePath());
    }

    Path work = run.getWorkPath();
    transferFilesToRemote(work, getRunDirectory(run).resolve(work.getFileName()));

    job.setState(State.Prepared);
    InfoLogMessage.issue(job.getRun(), "was prepared");
  }

  public Path getWorkDirectory(SimulatorRun run) throws FailedToControlRemoteException {
    return getRunDirectory(run).resolve(SimulatorRun.WORKING_DIR);
  }

  public Path getRunDirectory(SimulatorRun run) throws FailedToControlRemoteException {
    Computer computer = run.getActualHost();
    Path path = parseHomePath(computer.getWorkBaseDirectory()).resolve(RUN_DIR).resolve(run.getId());

    createDirectories(path);

    return path;
  }

  Path getSimulatorBinDirectory(Job job) throws FailedToControlRemoteException, RunNotFoundException {
    return parseHomePath(job.getHost().getWorkBaseDirectory()).resolve(SIMULATOR_DIR).resolve(job.getRun().getSimulator().getVersionId());
  }

  String makeBatchFileText(Job job) throws FailedToControlRemoteException, RunNotFoundException {
    SimulatorRun run = job.getRun();
    JSONArray localSharedList = run.getLocalSharedList();

    String text = "#!/bin/sh\n" +
      "\n" +
      "export WAFFLE_REMOTE='" + getSimulatorBinDirectory(job) + "'\n" +
      "export WAFFLE_BATCH_WORKING_DIR=`pwd`\n" +
      "mkdir -p " + getWorkDirectory(run) +"\n" +
      "cd " + getWorkDirectory(run) + "\n" +
      "export WAFFLE_WORKING_DIR=`pwd`\n" +
      "cd '" + getSimulatorBinDirectory(job) + "'\n" +
      "chmod a+x '" + run.getSimulator().getSimulationCommand() + "' >/dev/null 2>&1\n" +
      "find . -type d | xargs -n 1 -I{1} sh -c 'mkdir -p \"${WAFFLE_WORKING_DIR}/{1}\";find {1} -maxdepth 1 -type f | xargs -n 1 -I{2} ln -s \"`pwd`/{2}\" \"${WAFFLE_WORKING_DIR}/{1}/\"'\n" +
      "cd ${WAFFLE_BATCH_WORKING_DIR}\n" +
      "export WAFFLE_LOCAL_SHARED=\"" + job.getHost().getWorkBaseDirectory().replaceFirst("^~", "\\$\\{HOME\\}") + "/local_shared/" + run.getProject().getName() + "\"\n" +
      "mkdir -p \"$WAFFLE_LOCAL_SHARED\"\n" +
      "cd \"${WAFFLE_WORKING_DIR}\"\n";

    for (int i = 0; i < localSharedList.length(); i++) {
      JSONArray a = localSharedList.getJSONArray(i);
      text += makeLocalSharingPreCommandText(a.getString(0), a.getString(1));
    }

    text += makeEnvironmentCommandText(job);

    text += "\n" + run.getSimulator().getSimulationCommand() + " >${WAFFLE_BATCH_WORKING_DIR}/" + Constants.STDOUT_FILE + " 2>${WAFFLE_BATCH_WORKING_DIR}/" + Constants.STDERR_FILE + " `cat ${WAFFLE_BATCH_WORKING_DIR}/" + ARGUMENTS_FILE + "`\n" +
      "EXIT_STATUS=$?\n";

    for (int i = 0; i < localSharedList.length(); i++) {
      JSONArray a = localSharedList.getJSONArray(i);
      text += makeLocalSharingPostCommandText(a.getString(0), a.getString(1));
    }

    text += "\n" + "cd ${WAFFLE_BATCH_WORKING_DIR}\n" +
      "echo ${EXIT_STATUS} > " + EXIT_STATUS_FILE + "\n" +
      "\n";

    return text;
  }

  String makeLocalSharingPreCommandText(String key, String remote) {
    return "mkdir -p `dirname \"" + remote + "\"`;if [ -e \"${WAFFLE_LOCAL_SHARED}/" + key + "\" ]; then ln -fs \"${WAFFLE_LOCAL_SHARED}/" + key + "\" \"" + remote + "\"; else echo \"" + key + "\" >> \"${WAFFLE_BATCH_WORKING_DIR}/non_prepared_local_shared.txt\"; fi\n";
  }

  String makeLocalSharingPostCommandText(String key, String remote) {
    return "if grep \"^" + key + "$\" \"${WAFFLE_BATCH_WORKING_DIR}/non_prepared_local_shared.txt\"; then mv \"" + remote + "\" \"${WAFFLE_LOCAL_SHARED}/" + key + "\"; ln -fs \"${WAFFLE_LOCAL_SHARED}/"  + key + "\" \"" + remote + "\" ;fi\n";
  }

  String makeArgumentFileText(Job job) throws RunNotFoundException {
    String text = "";
    for (Object o : job.getRun().getArguments()) {
      text += o.toString() + "\n";
    }
    return text;
  }

  String makeEnvironmentCommandText(Job job) throws RunNotFoundException {
    String text = "";
    for (Map.Entry<String, Object> entry : job.getHost().getEnvironments().toMap().entrySet()) {
      text += "export " + entry.getKey().replace(' ', '_') + "=\"" + entry.getValue().toString().replace("\"", "\\\"") + "\"\n";
    }
    for (Map.Entry<String, Object> entry : job.getRun().getEnvironments().toMap().entrySet()) {
      text += "export " + entry.getKey().replace(' ', '_') + "=\"" + entry.getValue().toString().replace("\"", "\\\"") + "\"\n";
    }
    return text;
  }

  String xsubSubmitCommand(Job job) throws FailedToControlRemoteException, RunNotFoundException {
    return xsubCommand(job) + " " + BATCH_FILE;
  }

  String xsubCommand(Job job) throws FailedToControlRemoteException, RunNotFoundException {
    Computer computer = job.getHost();
    return "XSUB_COMMAND=`which " + getXsubBinDirectory(computer) + "xsub`; " +
      "if test ! $XSUB_TYPE; then XSUB_TYPE=None; fi; cd '" + getRunDirectory(job.getRun()).toString() + "'; " +
      "XSUB_TYPE=$XSUB_TYPE $XSUB_COMMAND -p '" + computer.getXsubParameters().toString().replaceAll("'", "\\\\'") + "' ";
  }

  String xstatCommand(Job job) {
    Computer computer = job.getHost();
    return "if test ! $XSUB_TYPE; then XSUB_TYPE=None; fi; XSUB_TYPE=$XSUB_TYPE "
      + getXsubBinDirectory(computer) + "xstat " + job.getJobId();
  }

  String xdelCommand(Job job) {
    Computer computer = job.getHost();
    return "if test ! $XSUB_TYPE; then XSUB_TYPE=None; fi; XSUB_TYPE=$XSUB_TYPE "
      + getXsubBinDirectory(computer) + "xdel " + job.getJobId();
  }

  void processXsubSubmit(Job job, String json) throws Exception {
    try {
      JSONObject object = new JSONObject(json);
      String jobId = object.getString("job_id");
      job.setJobId(jobId);
      job.setState(State.Submitted);
      InfoLogMessage.issue(job.getRun(), "was submitted");
    } catch (Exception e) {
      throw e;
    }
  }

  void processXstat(Job job, String json) throws RunNotFoundException {
    InfoLogMessage.issue(job.getRun(), "will be checked");
    JSONObject object = null;
    try {
      object = new JSONObject(json);
    } catch (JSONException e) {
      WarnLogMessage.issue(e.getMessage() + json);
      job.setState(State.Excepted);
      return;
    }
    try {
      String status = object.getString("status");
      switch (status) {
        case "running" :
          job.setState(State.Running);
          break;
        case "finished" :
          int exitStatus = -1;
          try {
            exitStatus = Integer.parseInt(getFileContents(job.getRun(), getRunDirectory(job.getRun()).resolve(EXIT_STATUS_FILE)).trim());
          } catch (Exception e) {
            job.getRun().appendErrorNote(LogMessage.getStackTrace(e));
            WarnLogMessage.issue(e);
          }
          job.getRun().setExitStatus(exitStatus);

          Path runDirectoryPath = getRunDirectory(job.getRun());

          try {
            transferFilesFromRemote(runDirectoryPath.resolve(Constants.STDOUT_FILE), job.getRun().getDirectoryPath().resolve(Constants.STDOUT_FILE));
          } catch (Exception | Error e) {
            job.getRun().appendErrorNote(LogMessage.getStackTrace(e));
            WarnLogMessage.issue(e);
          }

          try {
            transferFilesFromRemote(runDirectoryPath.resolve(Constants.STDERR_FILE), job.getRun().getDirectoryPath().resolve(Constants.STDERR_FILE));
          } catch (Exception | Error e) {
            job.getRun().appendErrorNote(LogMessage.getStackTrace(e));
            WarnLogMessage.issue(e);
          }

          if (exitStatus == 0) {
            InfoLogMessage.issue(job.getRun(), "results will be collected");

            boolean isNoException = true;
            try {
              for (String collectorName : job.getRun().getSimulator().getCollectorNameList()) {
                try {
                  new RubyResultCollector().collect(this, job.getRun(), collectorName);
                } catch (Exception | Error e) {
                  isNoException = false;
                  job.setState(State.Excepted);
                  job.getRun().appendErrorNote(LogMessage.getStackTrace(e));
                  WarnLogMessage.issue(e);
                }
              }
            } catch (Exception e) {
              isNoException = false;
              job.setState(State.Excepted);
              job.getRun().appendErrorNote(LogMessage.getStackTrace(e));
              WarnLogMessage.issue(e);
            }

            if (isNoException) {
              job.setState(State.Finished);
            }
          } else {
            job.setState(State.Failed);
          }
          job.remove();

          break;
      }
    } catch (Exception e) {
      job.getRun().appendErrorNote(LogMessage.getStackTrace(e));
      ErrorLogMessage.issue(e);
    }
  }

  void processXdel(Job job, String json) throws RunNotFoundException {
    // nothing to do
  }

  Path getContentsPath(SimulatorRun run, Path path) throws FailedToControlRemoteException {
    if (path.isAbsolute()) {
      return path;
    }
    return getWorkDirectory(run).resolve(path);
  }

  public static String getXsubBinDirectory(Computer computer) {
    String separator = (computer.isLocal() ? File.separator : "/");
    return (computer.getXsubDirectory().equals("") ? "": computer.getXsubDirectory() + separator + "bin" + separator);
  }

  public static JSONObject getXsubTemplate(Computer computer, boolean retry) throws RuntimeException, WaffleException {
    AbstractSubmitter submitter = getInstance(computer).connect(retry);
    JSONObject jsonObject = new JSONObject();
    String command = "if test ! $XSUB_TYPE; then XSUB_TYPE=None; fi; XSUB_TYPE=$XSUB_TYPE " +
      getXsubBinDirectory(computer) + "xsub -t";
    String json = submitter.exec(command);
    if (json != null) {
      try {
        jsonObject = new JSONObject(json);
      } catch (Exception e) {
        if (submitter.exec("which '" + getXsubBinDirectory(computer) + "xsub' 2>/dev/null; if test 0 -ne $?; then echo NotFound; fi;").startsWith("NotFound")) {
          throw new NotFoundXsubException(e);
        }
        throw new RuntimeException("Failed to parse JSON : " +
          submitter.exec("if test ! -e '" + getXsubBinDirectory(computer) + "xsub'; then echo NotFound; fi;")
          );
      }
    }
    return jsonObject;
  }

  public static JSONObject getParameters(Computer computer) {
    AbstractSubmitter submitter = getInstance(computer);
    JSONObject jsonObject = submitter.getDefaultParameters(computer);
    return jsonObject;
  }

  protected boolean isSubmittable(Computer computer, Job job) {
    return isSubmittable(computer, job, Job.getList(computer));
  }

  protected boolean isSubmittable(Computer computer, Job next, ArrayList<Job>... lists) {
    SimulatorRun nextRun = null;
    try {
      if (next != null) {
        nextRun = next.getRun();
      }
    } catch (RunNotFoundException e) {
    }
    double thread = (nextRun == null ? 0.0: nextRun.getSimulator().getRequiredThread());
    for (ArrayList<Job> list : lists) {
      thread += list.stream().mapToDouble(o->o.getRequiredThread()).sum();
    }
    double memory = (nextRun == null ? 0.0: nextRun.getSimulator().getRequiredMemory());
    for (ArrayList<Job> list : lists) {
      memory += list.stream().mapToDouble(o->o.getRequiredMemory()).sum();
    }

    return (thread <= getMaximumNumberOfThreads(computer) && memory <= getAllocableMemorySize(computer));
  }

  public void pollingTask(Computer computer) throws FailedToControlRemoteException {
    pollingInterval = computer.getPollingInterval();
    ArrayList<Job> jobList = Job.getList(computer);

    createdJobList.clear();
    preparedJobList.clear();
    submittedJobList.clear();
    runningJobList.clear();
    cancelJobList.clear();

    for (Job job : jobList) {
      try {
        switch (job.getState(true)) {
          case Created:
            if (isSubmittable(computer, null, createdJobList, preparedJobList)) {
              job.getRun(); // check exists
              createdJobList.add(job);
            }
            break;
          case Prepared:
            if (isSubmittable(computer, null, createdJobList, preparedJobList)) {
              job.getRun(); // check exists
              preparedJobList.add(job);
            }
            break;
          case Submitted:
            submittedJobList.add(job);
            break;
          case Running:
            runningJobList.add(job);
            break;
          case Cancel:
            cancelJobList.add(job);
            break;
          case Finished:
          case Failed:
          case Excepted:
          case Canceled:
            job.remove();
        }
      } catch (RunNotFoundException e) {
        try {
          cancel(job);
        } catch (RunNotFoundException ex) { }
        job.remove();
        WarnLogMessage.issue("SimulatorRun(" + job.getId() + ") is not found; The job was removed." );
      }

      if (Main.hibernateFlag) { break; }
    }

    processJobLists(computer, createdJobList, preparedJobList, submittedJobList, runningJobList, cancelJobList);
  }

  public double getMaximumNumberOfThreads(Computer computer) {
    return computer.getMaximumNumberOfThreads();
  }

  public double getAllocableMemorySize(Computer computer) {
    return computer.getAllocableMemorySize();
  }

  public void processJobLists(Computer computer, ArrayList<Job> createdJobList, ArrayList<Job> preparedJobList, ArrayList<Job> submittedJobList, ArrayList<Job> runningJobList, ArrayList<Job> cancelJobList) throws FailedToControlRemoteException {
    //int submittedCount = submittedJobList.size() + runningJobList.size();
    submittedJobList.addAll(runningJobList);
    ArrayList<Job> submittedJobListForAggregation = new ArrayList<>(submittedJobList);
    ArrayList<Job> queuedJobList = new ArrayList<>();
    queuedJobList.addAll(preparedJobList);
    queuedJobList.addAll(createdJobList);

    for (Job job : cancelJobList) {
      try {
        cancel(job);
      } catch (RunNotFoundException e) {
        job.remove();
      }
    }

    ArrayList<Future> futureList = new ArrayList<>();
    for (Job job : createdJobList) {
      futureList.add(threadPool.submit(() -> {
        try {
          prepareJob(job);
        } catch (WaffleException e) {
          WarnLogMessage.issue(e);
        }
      }));
    }

    for (Future future : futureList) {
      try {
        future.get();
      } catch (Exception e) {
        ErrorLogMessage.issue(e);
      }
    }

    for (Job job : submittedJobList) {
      if (Main.hibernateFlag) { break; }

      try {
        switch (update(job)) {
          case Finished:
          case Failed:
          case Excepted:
          case Canceled:
            submittedJobListForAggregation.remove(job);
            if (! queuedJobList.isEmpty()) {
              Job nextJob = queuedJobList.get(0);
              if (isSubmittable(computer, nextJob, submittedJobListForAggregation)) {
                submit(nextJob);
                queuedJobList.remove(nextJob);
              }
            }
        }
      } catch (WaffleException e) {
        WarnLogMessage.issue(e);
        try {
          job.setState(State.Excepted);
        } catch (RunNotFoundException ex) { }
        throw new FailedToControlRemoteException(e);
      }
    }

    for (Job job : queuedJobList) {
      if (Main.hibernateFlag) { break; }

      try {
        if (isSubmittable(computer, job, submittedJobListForAggregation)) {
          submit(job);
        }
      } catch (WaffleException e) {
        WarnLogMessage.issue(e);
        try {
          job.setState(State.Excepted);
        } catch (RunNotFoundException ex) { }
        throw new FailedToControlRemoteException(e);
      }
    }
  }

  public void hibernate() {

  }

  /*
  public boolean stageIn(SimulatorRun run, String name, String remote) {
    if (updated) {
      tranfar file to remote shared dir from local shared dir
    }
    soft copy to run dir from remote shared dir
  }

  public boolean stageOut(SimulatorRun run, String name, String remote) {

  }
   */
}
