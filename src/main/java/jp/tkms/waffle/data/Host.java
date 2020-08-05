package jp.tkms.waffle.data;

import jp.tkms.waffle.Constants;
import jp.tkms.waffle.Main;
import jp.tkms.waffle.data.exception.FailedToControlRemoteException;
import jp.tkms.waffle.data.log.ErrorLogMessage;
import jp.tkms.waffle.data.log.WarnLogMessage;
import jp.tkms.waffle.data.util.FileName;
import jp.tkms.waffle.data.util.HostState;
import jp.tkms.waffle.submitter.AbciSubmitter2;
import jp.tkms.waffle.submitter.AbstractSubmitter;
import jp.tkms.waffle.submitter.LocalSubmitter;
import jp.tkms.waffle.submitter.SshSubmitter;
import org.json.JSONObject;

import javax.crypto.*;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.*;

public class Host implements DataDirectory, PropertyFile {
  private static final String TABLE_NAME = "host";
  private static final UUID LOCAL_UUID = UUID.fromString("00000000-0000-0000-0000-000000000000");
  private static final String KEY_LOCAL = "LOCAL";
  private static final String KEY_WORKBASE = "work_base_dir";
  private static final String KEY_XSUB = "xsub_dir";
  private static final String KEY_XSUB_TEMPLATE = "xsub_template";
  private static final String KEY_POLLING = "polling_interval";
  private static final String KEY_MAX_JOBS = "maximum_jobs";
  private static final String KEY_SUBMITTER = "submitter";
  private static final String KEY_ENCRYPT_KEY = "encrypt_key";
  private static final String KEY_PARAMETERS = "parameters";
  private static final String KEY_STATE = "state";
  private static final String KEY_ENVIRONMENTS = "environments";
  private static final String ENCRYPT_ALGORITHM = "AES/CBC/PKCS5Padding";
  private static final IvParameterSpec IV_PARAMETER_SPEC = new IvParameterSpec("0123456789ABCDEF".getBytes());

  private static final HashMap<String, Host> instanceMap = new HashMap<>();

  public static final ArrayList<Class<AbstractSubmitter>> submitterTypeList = new ArrayList(Arrays.asList(
    SshSubmitter.class, AbciSubmitter2.class, LocalSubmitter.class
  ));

  private String name;
  private String submitterType = null;
  private String workBaseDirectory = null;
  private String xsubDirectory = null;
  private SecretKeySpec encryptKey = null;
  private Integer pollingInterval = null;
  private Integer maximumNumberOfJobs = null;
  private JSONObject parameters = null;
  private JSONObject xsubTemplate = null;

  public Host(String name) {
    this.name = name;
    instanceMap.put(name, this);

    initialize();

    Main.registerFileChangeEventListener(getBaseDirectoryPath().resolve(name), () -> {
      synchronized (this) {
        submitterType = null;
        workBaseDirectory = null;
        xsubDirectory = null;
        pollingInterval = null;
        maximumNumberOfJobs = null;
        parameters = null;
        xsubTemplate = null;
        reloadPropertyStore();
      }
    });
  }

  public String getName() {
    return name;
  }

  public static Host getInstance(String name) {
    if (name != null && !name.equals("") && Files.exists(getBaseDirectoryPath().resolve(name))) {
      Host host = instanceMap.get(name);
      if (host == null) {
        host = new Host(name);
      }
      return host;
    }
    return null;
  }

  public static Host find(String key) {
    return getInstance(key);
  }

  public static ArrayList<Host> getList() {
    initializeWorkDirectory();

    ArrayList<Host> list = new ArrayList<>();

    for (File file : getBaseDirectoryPath().toFile().listFiles()) {
      if (file.isDirectory()) {
        list.add(getInstance(file.getName()));
      }
    }

    return list;
  }

  public void initialize() {
    if (! Files.exists(getDirectoryPath())) {
      try {
        Files.createDirectories(getDirectoryPath());
      } catch (IOException e) {
        ErrorLogMessage.issue(e);
      }
    }

    initializeWorkDirectory();

    if (getState() == null) { setState(HostState.Unviable); }
    if (getXsubDirectory() == null) { setXsubDirectory(""); }
    if (getWorkBaseDirectory() == null) { setWorkBaseDirectory("/tmp/waffle"); }
    if (getMaximumNumberOfJobs() == null) { setMaximumNumberOfJobs(1); }
    if (getPollingInterval() == null) { setPollingInterval(10); }
  }

  public static ArrayList<Host> getViableList() {
    ArrayList<Host> list = new ArrayList<>();

    for (Host host : getList()) {
      if (host.getState().equals(HostState.Viable)) {
        list.add(host);
      }
    }

    return list;
  }

  public static Host create(String name, String submitterClassName) {
    Data.initializeWorkDirectory();

    name = FileName.removeRestrictedCharacters(name);

    Host host = getInstance(name);
    if (host == null) {
      host = new Host(name);
    }
    host.setSubmitterType(submitterClassName);

    return host;
  }

  public static ArrayList<String> getSubmitterTypeNameList() {
    ArrayList<String> list = new ArrayList<>();
    for (Class c : submitterTypeList) {
      list.add(c.getCanonicalName());
    }
    return list;
  }

  public void update() {
    try {
      JSONObject jsonObject = AbstractSubmitter.getXsubTemplate(this, false);
      setXsubTemplate(jsonObject);
      setParameters(getParameters());
      setState(HostState.Viable);
    } catch (RuntimeException | FailedToControlRemoteException e) {
      setState(HostState.Unviable);
    }
  }

  private void setState(HostState state) {
    setToProperty(KEY_STATE, state.ordinal());
  }

  public HostState getState() {
    Integer state = getIntFromProperty(KEY_STATE, HostState.Unviable.ordinal());
    if (state == null) {
      return null;
    }
    return HostState.valueOf(state);
  }

  public boolean isLocal() {
    //return LOCAL_UUID.equals(id);
    return getName().equals(KEY_LOCAL);
  }

  public String getSubmitterType() {
    synchronized (this) {
      if (submitterType == null) {
        submitterType = getStringFromProperty(KEY_SUBMITTER, submitterTypeList.get(0).getCanonicalName());
      }
      return submitterType;
    }
  }

  public void setSubmitterType(String submitterClassName) {
    synchronized (this) {
      setToProperty(KEY_SUBMITTER, submitterClassName);
      submitterType = submitterClassName;
    }
  }

  public String getWorkBaseDirectory() {
    synchronized (this) {
      if (workBaseDirectory == null) {
        workBaseDirectory = getStringFromProperty(KEY_WORKBASE);
      }
      return workBaseDirectory;
    }
  }

  public void setWorkBaseDirectory(String workBaseDirectory) {
    synchronized (this) {
      setToProperty(KEY_WORKBASE, workBaseDirectory);
      this.workBaseDirectory = workBaseDirectory;
    }
  }

  public String getXsubDirectory() {
    synchronized (this) {
      if (xsubDirectory == null) {
        xsubDirectory = getStringFromProperty(KEY_XSUB);
      }
      return xsubDirectory;
    }
  }

  public void setXsubDirectory(String xsubDirectory) {
    synchronized (this) {
      setToProperty(KEY_XSUB, xsubDirectory);
      this.xsubDirectory = xsubDirectory;
    }
  }

  private SecretKeySpec getEncryptKey() {
    synchronized (this) {
      if (encryptKey == null) {
        String encryptKeyText = getStringFromProperty(KEY_ENCRYPT_KEY);
        if (encryptKeyText == null || encryptKeyText.length() != 16) {
          encryptKeyText = UUID.randomUUID().toString().replace("-", "").substring(16, 32);
          setToProperty(KEY_ENCRYPT_KEY, encryptKeyText);
        }
        encryptKey = new SecretKeySpec(encryptKeyText.getBytes(), "AES");
      }
      return encryptKey;
    }
  }

  public String encryptText(String text) {
    if (text != null) {
      try {
        Cipher encrypter = Cipher.getInstance(ENCRYPT_ALGORITHM);
        encrypter.init(Cipher.ENCRYPT_MODE, getEncryptKey(), IV_PARAMETER_SPEC);
        byte[] byteToken = encrypter.doFinal(text.getBytes());
        return new String(Base64.getEncoder().encode(byteToken));
      } catch (NoSuchAlgorithmException | NoSuchPaddingException | InvalidKeyException | InvalidAlgorithmParameterException | IllegalBlockSizeException | BadPaddingException e) {
      }
    }
    return "";
  }

  public String decryptText(String text) {
    if (text != null) {
      try {
        Cipher decrypter = Cipher.getInstance(ENCRYPT_ALGORITHM);
        decrypter.init(Cipher.DECRYPT_MODE, getEncryptKey(), IV_PARAMETER_SPEC);
        byte[] byteToken = Base64.getDecoder().decode(text);
        return new String(decrypter.doFinal(byteToken));
      } catch (NoSuchAlgorithmException | NoSuchPaddingException | InvalidKeyException | InvalidAlgorithmParameterException | IllegalBlockSizeException | BadPaddingException e) {
        e.printStackTrace();
      }
    }
    return "";
  }

  /*
  public String getOs() {
    if (os == null) {
      os = getStringFromDB(KEY_OS);
    }
    return os;
  }

  public String getDirectorySeparetor() {
    String directorySeparetor = "/";
    if (getOs().equals("U")) {
      directorySeparetor = "/";
    }
    return directorySeparetor;
  }
   */

  public Integer getPollingInterval() {
    synchronized (this) {
      if (pollingInterval == null) {
        pollingInterval = getIntFromProperty(KEY_POLLING);
      }
      return pollingInterval;
    }
  }

  public void setPollingInterval(Integer pollingInterval) {
    synchronized (this) {
      setToProperty(KEY_POLLING, pollingInterval);
      this.pollingInterval = pollingInterval;
    }
  }

  public Integer getMaximumNumberOfJobs() {
    synchronized (this) {
      if (maximumNumberOfJobs == null) {
        maximumNumberOfJobs = getIntFromProperty(KEY_MAX_JOBS);
      }
      return maximumNumberOfJobs;
    }
  }

  public void setMaximumNumberOfJobs(Integer maximumNumberOfJobs) {
    synchronized (this) {
      setToProperty(KEY_MAX_JOBS, maximumNumberOfJobs);
      this.maximumNumberOfJobs = maximumNumberOfJobs;
    }
  }

  public JSONObject getXsubParameters() {
    JSONObject jsonObject = new JSONObject();
    for (String key : getXsubParametersTemplate().keySet()) {
      jsonObject.put(key, getParameter(key));
    }
    return jsonObject;
  }

  public JSONObject getParametersWithDefaultParameters() {
    JSONObject parameters = AbstractSubmitter.getParameters(this);
    JSONObject jsonObject = getParameters();
    for (String key : jsonObject.keySet()) {
      parameters.put(key, jsonObject.get(key));
    }
    return parameters;
  }

  public JSONObject getParametersWithDefaultParametersFiltered() {
    JSONObject parameters = AbstractSubmitter.getParameters(this);
    JSONObject jsonObject = getParameters();
    for (String key : jsonObject.keySet()) {
      if (! key.startsWith(".")) {
        parameters.put(key, jsonObject.get(key));
      }
    }
    return parameters;
  }

  public JSONObject getFilteredParameters() {
    JSONObject jsonObject = new JSONObject();
    JSONObject parameters = getParameters();
    for (String key : parameters.keySet()) {
      if (key.startsWith(".")) {
        jsonObject.put(key, parameters.get(key));
      }
    }
    return jsonObject;
  }

  public JSONObject getParameters() {
    synchronized (this) {
      if (parameters == null) {
        String json = getFileContents(KEY_PARAMETERS + Constants.EXT_JSON);
        if (json.equals("")) {
          json = "{}";
          createNewFile(KEY_PARAMETERS + Constants.EXT_JSON);
          updateFileContents(KEY_PARAMETERS + Constants.EXT_JSON, json);
        }
        parameters = getXsubParametersTemplate();
        try {
          JSONObject jsonObject = AbstractSubmitter.getInstance(this).getDefaultParameters(this);
          for (String key : jsonObject.toMap().keySet()) {
            parameters.put(key, jsonObject.getJSONObject(key));
          }
        } catch (Exception e) {
        }
        JSONObject jsonObject = new JSONObject(json);
        for (String key : jsonObject.keySet()) {
          parameters.put(key, jsonObject.get(key));
        }
      }
      return new JSONObject(parameters.toString());
    }
  }

  public Object getParameter(String key) {
    return getParameters().get(key);
  }

  public void setParameters(JSONObject jsonObject) {
    synchronized (this) {
      JSONObject filteredParameters = getFilteredParameters();
      for (String key : filteredParameters.keySet()) {
        jsonObject.put(key, filteredParameters.get(key));
      }

      updateFileContents(KEY_PARAMETERS + Constants.EXT_JSON, jsonObject.toString(2));
      this.parameters = null;
    }
  }

  public void setParameters(String json) {
    try {
      setParameters(new JSONObject(json));
    } catch (Exception e) {
      WarnLogMessage.issue(e);
    }
  }

  public Object setParameter(String key, Object value) {
    synchronized (this) {
      JSONObject jsonObject = getParameters();
      jsonObject.put(key, value);
      setParameters(jsonObject);
      return value;
    }
  }

  public JSONObject getEnvironments() {
    synchronized (this) {
      return getJSONObjectFromProperty(KEY_ENVIRONMENTS, new JSONObject());
    }
  }

  public void setEnvironments(JSONObject jsonObject) {
    synchronized (this) {
      setToProperty(KEY_ENVIRONMENTS, jsonObject);
    }
  }

  public JSONObject getXsubTemplate() {
    synchronized (this) {
      if (xsubTemplate == null) {
        xsubTemplate = new JSONObject(getStringFromProperty(KEY_XSUB_TEMPLATE, "{}"));
      }
      return xsubTemplate;
    }
  }

  public void setXsubTemplate(JSONObject jsonObject) {
    synchronized (this) {
      this.xsubTemplate = jsonObject;
      setToProperty(KEY_XSUB_TEMPLATE, jsonObject.toString());
    }
  }

  public JSONObject getXsubParametersTemplate() {
    JSONObject jsonObject = new JSONObject();

    try {
      JSONObject object = getXsubTemplate().getJSONObject("parameters");
      for (String key : object.toMap().keySet()) {
        jsonObject.put(key, object.getJSONObject(key).get("default"));
      }
    } catch (Exception e) {
    }

    return jsonObject;
  }

  public static Path getBaseDirectoryPath() {
    return Data.getWaffleDirectoryPath().resolve(Constants.HOST);
  }

  @Override
  public Path getDirectoryPath() {
    return getBaseDirectoryPath().resolve(name);
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

  @Override
  public Path getPropertyStorePath() {
    return getDirectoryPath().resolve(Constants.HOST + Constants.EXT_JSON);
  }

  public static void initializeWorkDirectory() {
    Data.initializeWorkDirectory();
    if (! Files.exists(getBaseDirectoryPath().resolve(KEY_LOCAL))) {
      create(KEY_LOCAL, LocalSubmitter.class.getCanonicalName());
    }
  }
}
