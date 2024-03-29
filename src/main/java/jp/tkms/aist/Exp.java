package jp.tkms.aist;

import com.jcraft.jsch.JSchException;

import java.io.Serializable;
import java.util.UUID;

public class Exp implements Serializable {
    private static final long serialVersionUID = 1L;

    public static enum Status{
        CREATED,
        SUBMITTED,
        FINISHED,
        FAILED
    }

    private UUID uuid;
    private ExpSet expSet;
    private String args;
    private ExpPack expPack;

    private Status status;
    private String result;


    public Exp(String args) {
        uuid = UUID.randomUUID();
        this.args = args;
        status = Status.CREATED;
        result = "";
    }

    public Exp(ExpSet expSet, String args) {
        this(args);
        this.expSet = expSet;
    }

    public void setExpSet(ExpSet expSet) {
        this.expSet = expSet;
    }

    public void setExpPack(ExpPack expPack) {
        this.expPack = expPack;
    }

    public void setStatus(Status status) {
        this.status = status;
    }

    public void setResult(String result) {
        this.result = result;
    }

    public ExpSet getExpSet() {
        return expSet;
    }

    public String getArgs() {
        return args;
    }

    public ExpPack getExpPack() {
        return expPack;
    }

    public Status getStatus() {
        return status;
    }

    public String getResult() {
        return result;
    }

    public UUID getUuid() {
        return uuid;
    }

    public Work getWork() {
        return expSet.work;
    }

    public void updateResult(SshSession ssh) throws JSchException {
        String workDir = Config.REMOTE_WORKDIR + "/" + getExpPack().getUuid().toString() + "/";

        for (int c = 0; c <= Config.MAX_CAT_RECHECK; c++) {
            SshChannel ch = ssh.exec("cat " + getUuid().toString() + "/_output.json", workDir);
            if (ch.getExitStatus() == 0) {
                status = Status.FINISHED;
                setResult(ch.getStdout());
                resultSubmit();

                //System.out.println("Exp[" + getExpPack().getUuid().toString() + "/" + getUuid().toString() + "]");
                //System.out.println(ch.getStdout());
                System.out.print('-');
                break;
            } else {
                status = Status.FAILED;

                //System.out.println("Exp[" + getExpPack().getUuid().toString() + "/" + getUuid().toString() + "]");
                System.out.print('X');
            }
        }
    }

    public void resultSubmit() {
        if (status == Status.FINISHED) {
            ResultSubmitter.asyncPost(getWork().getName(), getResult());
        }
    }

    @Override
    public String toString() {
        return "@" + uuid.toString() + "\n" +
                "# " + args;
    }
}
