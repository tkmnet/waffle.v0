package jp.tkms.waffle.submitter;

import jp.tkms.waffle.PollingThread;
import jp.tkms.waffle.data.ComputerTask;
import jp.tkms.waffle.data.computer.Computer;
import jp.tkms.waffle.data.job.AbstractJob;
import jp.tkms.waffle.data.log.message.WarnLogMessage;
import jp.tkms.waffle.data.util.ComputerState;
import jp.tkms.waffle.exception.FailedToControlRemoteException;
import jp.tkms.waffle.exception.FailedToTransferFileException;
import jp.tkms.waffle.exception.RunNotFoundException;
import org.json.JSONArray;
import org.json.JSONObject;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedList;

public class MultiComputerSubmitter extends AbstractSubmitter {
  public static final String KEY_TARGET_COMPUTERS = "target_computers";

  public MultiComputerSubmitter(Computer computer) {
    super(computer);
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
  public Path parseHomePath(String pathString) throws FailedToControlRemoteException {
    return null;
  }

  @Override
  public void createDirectories(Path path) throws FailedToControlRemoteException {

  }

  @Override
  boolean exists(Path path) throws FailedToControlRemoteException {
    return false;
  }

  @Override
  public String exec(String command) throws FailedToControlRemoteException {
    return null;
  }

  @Override
  public void putText(AbstractJob job, Path path, String text) throws FailedToTransferFileException, RunNotFoundException {

  }

  @Override
  public String getFileContents(ComputerTask run, Path path) throws FailedToTransferFileException {
    return null;
  }

  @Override
  public void transferFilesToRemote(Path localPath, Path remotePath) throws FailedToTransferFileException {

  }

  @Override
  public void transferFilesFromRemote(Path remotePath, Path localPath) throws FailedToTransferFileException {

  }

  /*
  public double getMaximumNumberOfThreads(Computer computer) {
    double num = 0.0;

    JSONArray targetComputers = computer.getParameters().getJSONArray(KEY_TARGET_COMPUTERS);
    if (targetComputers != null) {
      for (Object object : targetComputers.toList()) {
        Computer targetComputer = Computer.getInstance(object.toString());
        if (targetComputer != null && targetComputer.getState().equals(ComputerState.Viable)) {
          num += targetComputer.getMaximumNumberOfThreads();
        }
      }
    }

    return (num < computer.getMaximumNumberOfThreads() ? num : computer.getMaximumNumberOfThreads());
  }

  public double getAllocableMemorySize(Computer computer) {
    double size = 0.0;

    JSONArray targetComputers = computer.getParameters().getJSONArray(KEY_TARGET_COMPUTERS);
    if (targetComputers != null) {
      for (Object object : targetComputers.toList()) {
        Computer targetComputer = Computer.getInstance(object.toString());
        if (targetComputer != null && targetComputer.getState().equals(ComputerState.Viable)) {
          size += targetComputer.getAllocableMemorySize();
        }
      }
    }

    return (size < computer.getAllocableMemorySize() ? size : computer.getAllocableMemorySize());
  }
   */

  @Override
  public void processPrepared(ArrayList<AbstractJob> submittedJobList, ArrayList<AbstractJob> createdJobList, ArrayList<AbstractJob> preparedJobList) throws FailedToControlRemoteException {

    double globalFreeThread = computer.getMaximumNumberOfThreads();
    double globalFreeMemory = computer.getAllocableMemorySize();
    int globalFreeJobSlot = computer.getMaximumNumberOfJobs();

    /* Check global acceptability */
    ArrayList<Computer> passableComputerList = new ArrayList<>();
    JSONArray targetComputers = computer.getParameters().getJSONArray(KEY_TARGET_COMPUTERS);
    if (targetComputers != null) {
      for (Object object : targetComputers.toList()) {
        Computer targetComputer = Computer.getInstance(object.toString());
        if (targetComputer != null && targetComputer.getState().equals(ComputerState.Viable)) {
          passableComputerList.add(targetComputer);

          for (AbstractJob job : getJobList(PollingThread.Mode.Normal, targetComputer)) {
            try {
              ComputerTask run = job.getRun();
              if (run.getComputer().equals(computer)) {
                globalFreeThread -= run.getRequiredThread();
                globalFreeMemory -= run.getRequiredMemory();
                globalFreeJobSlot -= 1;
              }
            } catch (RunNotFoundException e) {
            }
          }
        }
      }
    }

    if (globalFreeThread > 0.0 && globalFreeMemory > 0.0 && globalFreeJobSlot > 0 && passableComputerList.size() > 0) {
      if (passableComputerList.size() > 0) {
        for (AbstractJob job : createdJobList) {
          int targetHostCursor = 0;
          Computer targetComputer = passableComputerList.get(targetHostCursor);
          AbstractSubmitter targetSubmitter = AbstractSubmitter.getInstance(PollingThread.Mode.Normal, targetComputer);

          if (!targetSubmitter.isSubmittable(targetComputer, job)) {
            targetHostCursor += 1;
            while (targetHostCursor < passableComputerList.size() && !targetSubmitter.isSubmittable(targetComputer, job)) {
              targetComputer = passableComputerList.get(targetHostCursor);
              targetSubmitter = AbstractSubmitter.getInstance(PollingThread.Mode.Normal, targetComputer);
            }
          }

          if (targetSubmitter.isSubmittable(targetComputer, job)) {
            try {
              job.replaceComputer(targetComputer);
            } catch (RunNotFoundException e) {
              WarnLogMessage.issue(e);
              job.remove();
            }
          }
        }
      }
    }

    PollingThread.startup();
  }

  @Override
  public JSONObject getDefaultParameters(Computer computer) {
    JSONObject jsonObject = new JSONObject();
    jsonObject.put(KEY_TARGET_COMPUTERS, new JSONArray());
    return jsonObject;
  }
}