package jp.tkms.aist;

import java.io.*;
import java.util.HashMap;

public class CommonComponent implements Serializable {
    private static final long serialVersionUID = 1L;

    private PollingMonitor pollingMonitor;
    private HashMap<String, Work> workMap;
    private String hibernateWork;

    public CommonComponent(){
        pollingMonitor = null;
        workMap = new HashMap<>();
        hibernateWork = null;
    }

    public String getHibernateWork() {
        return hibernateWork;
    }

    public void setHibernateWork(String hibernateWork) {
        this.hibernateWork = hibernateWork;
    }

    public HashMap<String, Work> getWorkMap() {
        return workMap;
    }

    public HashMap<String, Work> addWork(Work work) {
        workMap.put(work.getName(), work);
        return workMap;
    }

    public Work getWork(String name) {
        Work work = workMap.get(name);
        if (work == null) {
            File workDir = new File(Config.LOCAL_WORKBASE_DIR + "/" + name);
            if (workDir.isDirectory()) {
                work = Work.load(name);
                addWork(work);
            }
        }
        return work;
    }

    public void unloadWork(Work work) {
        work.save();
        workMap.remove(work.getName());
    }

    public PollingMonitor getPollingMonitor() {
        if (pollingMonitor == null) {
            pollingMonitor = new PollingMonitor();
        }
        return pollingMonitor;
    }

    public void save(String fileName) throws IOException {
        FileOutputStream fileOutputStream = new FileOutputStream(fileName);
        ObjectOutputStream objectOutputStream = new ObjectOutputStream(fileOutputStream);
        objectOutputStream.writeObject(this);
        objectOutputStream.flush();
        objectOutputStream.close();
    }

    public static CommonComponent load(String fileName) {
        CommonComponent data = null;
        try {
            FileInputStream fileInputStream = new FileInputStream(fileName);
            ObjectInputStream objectInputStream = new ObjectInputStream(fileInputStream);
            data = (CommonComponent)objectInputStream.readObject();
            objectInputStream.close();
        } catch (Exception e) {
            data = new CommonComponent();
        }
        return data;
    }
}