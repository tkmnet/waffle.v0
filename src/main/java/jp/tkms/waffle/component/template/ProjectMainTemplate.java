package jp.tkms.waffle.component.template;

import jp.tkms.waffle.component.*;
import jp.tkms.waffle.data.Project;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Map;
import java.util.TreeMap;

public abstract class ProjectMainTemplate extends MainTemplate {

  private Project project;

  public ProjectMainTemplate(Project project) {
    this.project = project;
  }

  @Override
  protected ArrayList<Map.Entry<String, String>> pageNavigation() {
    return new ArrayList<Map.Entry<String, String>>(Arrays.asList(
      Map.entry("Home", ProjectComponent.getUrl(project)),
      Map.entry(TrialsComponent.TITLE, TrialsComponent.getUrl(project)),
      Map.entry(SimulatorComponent.TITLE, SimulatorsComponent.getUrl(project))
    ));
  }
}