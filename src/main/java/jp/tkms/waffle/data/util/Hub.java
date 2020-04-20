package jp.tkms.waffle.data.util;

import jp.tkms.waffle.conductor.RubyConductor;
import jp.tkms.waffle.data.*;
import org.jruby.Ruby;
import org.jruby.embed.EvalFailedException;
import org.jruby.embed.LocalContextScope;
import org.jruby.embed.ScriptingContainer;

import java.util.HashMap;
import java.util.HashSet;

public class Hub {

  Project project;
  ConductorRun conductorRun;
  AbstractRun run;
  Registry registry;

  String parameterStoreName;

  public Hub(ConductorRun conductorRun, AbstractRun run) {
    this.project = conductorRun.getProject();
    this.conductorRun = conductorRun;
    this.run = run;
    this.registry = new Registry(conductorRun.getProject());
    switchParameterStore(null);
  }

  public Project getProject() {
    return project;
  }

  public ConductorRun getConductorRun() {
    return conductorRun;
  }

  public Registry getRegistry() {
    return registry;
  }

  public Registry registry() {
    return getRegistry();
  }

  public void switchParameterStore(String key) {
    if (key == null) {
      parameterStoreName = ".S:" + conductorRun.getId();
    } else {
      parameterStoreName = ".S:" + conductorRun.getId() + '_' + key;
    }
  }

  public ConductorRun createConductorRun(String name) {
    Conductor conductor = Conductor.find(project, name);
    return ConductorRun.create(this.conductorRun, conductor);
  }

  public SimulatorRun createSimulatorRun(String name, String hostName) {
    Simulator simulator = Simulator.find(project, name);
    Host host = Host.find(hostName);
    return SimulatorRun.create(conductorRun, simulator, host);
  }

  public void invokeListener(String name) {
    String fileName = conductorRun.getConductor().getListenerScriptFileName(name);
    String script = conductorRun.getConductor().getFileContents(fileName);
    ScriptingContainer container = new ScriptingContainer(LocalContextScope.THREADSAFE);
    try {
      container.runScriptlet(RubyConductor.getInitScript());
      container.runScriptlet(RubyConductor.getListenerTemplateScript());
      container.runScriptlet(script);
      container.callMethod(Ruby.newInstance().getCurrentContext(), "exec_listener_script", conductorRun, run);

      container.terminate();
    } catch (EvalFailedException e) {
      container.terminate();
      BrowserMessage.addMessage("toastr.error('invokeListener: " + e.getMessage().replaceAll("['\"\n]", "\"") + "');");
    }
  }

  public Object getVariable(String key) {
    return registry.get(key);
  }

  public void putVariable(String key, Object value) {
    registry.put(key, value);
  }

  private final HashMap<Object, Object> variableMapWrapper  = new HashMap<Object, Object>() {
    @Override
    public Object get(Object key) {
      return getVariable(key.toString());
    }

    @Override
    public Object put(Object key, Object value) {
      putVariable(key.toString(), value);
      return value;
    }
  };
  public HashMap variables() { return variableMapWrapper; }
  public HashMap v() { return variableMapWrapper; }

  private final HashMap<Object, Object> parameterMapWrapper  = new HashMap<Object, Object>() {
    @Override
    public Object get(Object key) {
      if (run instanceof SimulatorRun) {
        return run.getParent().getParameter(key.toString());
      }
      return run.getParameter(key.toString());
    }

    @Override
    public Object put(Object key, Object value) {
      if (run instanceof SimulatorRun) {
        run.getParent().putParameter(key.toString(), value);
      } else {
        run.putParameter(key.toString(), value);
      }
      return value;
    }
  };
  public HashMap parameters() { return parameterMapWrapper; }
  public HashMap p() { return parameterMapWrapper; }
}