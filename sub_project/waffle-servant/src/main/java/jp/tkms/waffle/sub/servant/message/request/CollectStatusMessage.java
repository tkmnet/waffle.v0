package jp.tkms.waffle.sub.servant.message.request;

public class CollectStatusMessage extends JobMessage {
  String jobId;

  public CollectStatusMessage() { }

  public CollectStatusMessage(byte type, String id, String jobId) {
    super(type, id);
    this.jobId = jobId;
  }

  public String getJobId() {
    return jobId;
  }
}
