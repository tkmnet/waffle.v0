package jp.tkms.waffle.conductor;

import jp.tkms.waffle.data.AbstractRun;
import jp.tkms.waffle.data.Conductor;
import jp.tkms.waffle.data.ConductorRun;

public class EmptyConductor extends CycleConductor {
  @Override
  protected void preProcess(ConductorRun entity) {

  }

  @Override
  protected void eventHandler(ConductorRun entity, AbstractRun run) {

  }

  @Override
  protected void finalizeProcess(ConductorRun entity) {

  }

  @Override
  protected void suspendProcess(ConductorRun entity) {

  }

  @Override
  public String defaultScriptName() {
    return "";
  }

  @Override
  public void prepareConductor(Conductor conductor) {

  }
}