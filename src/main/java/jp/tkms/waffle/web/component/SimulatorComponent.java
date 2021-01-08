package jp.tkms.waffle.web.component;

import jp.tkms.waffle.Main;
import jp.tkms.waffle.data.project.executable.Executable;
import jp.tkms.waffle.web.template.Html;
import jp.tkms.waffle.web.template.Lte;
import jp.tkms.waffle.web.template.ProjectMainTemplate;
import jp.tkms.waffle.data.computer.Computer;
import jp.tkms.waffle.data.project.Project;
import jp.tkms.waffle.data.project.workspace.run.SimulatorRun;
import jp.tkms.waffle.exception.ProjectNotFoundException;
import jp.tkms.waffle.exception.RunNotFoundException;
import spark.Spark;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.Future;
import java.util.stream.Collectors;

public class SimulatorComponent extends AbstractAccessControlledComponent {
  public static final String TITLE = "Simulator";
  private static final String KEY_DEFAULT_PARAMETERS = "default_parameters";
  private static final String KEY_UPDATE_PARAMETERS = "update-parameters";
  private static final String KEY_PARAMETERS = "parameters";
  private static final String KEY_RUN = "run";
  private static final String KEY_COMPUTER = "computer";
  private static final String KEY_SIMULATOR = "simulator";

  private Mode mode;

  private Project project;
  private Executable executable;
  public SimulatorComponent(Mode mode) {
    super();
    this.mode = mode;
  }

  public SimulatorComponent() {
    this(Mode.Default);
  }

  static public void register() {
    Spark.get(getUrl(null), new SimulatorComponent());
    Spark.post(getUrl(null, "update"), new SimulatorComponent(Mode.Update));
    Spark.post(getUrl(null, KEY_UPDATE_PARAMETERS), new SimulatorComponent(Mode.UpdateParameters));
    Spark.get(getUrl(null, KEY_RUN), new SimulatorComponent(Mode.TestRun));
    Spark.post(getUrl(null, KEY_RUN), new SimulatorComponent(Mode.TestRun));

    ParameterExtractorComponent.register();
    ResultCollectorComponent.register();
  }

  public static String getUrl(Executable executable) {
    return SimulatorsComponent.getUrl(executable == null ? null : executable.getProject())
      + (executable == null ? "/:" + KEY_SIMULATOR : '/' + executable.getName());
  }

  public static String getUrl(Executable executable, String mode) {
    return getUrl(executable) + "/@" + mode;
  }

  @Override
  public void controller() throws ProjectNotFoundException {
    project = Project.getInstance(request.params("project"));
    executable = Executable.getInstance(project, request.params(KEY_SIMULATOR));

    switch (mode) {
      case Update:
        updateSimulator();
        break;
      case UpdateParameters:
        updateDefaultParameters();
        break;
      case TestRun:
        if (isPost()) {
          runSimulator();
        }
        renderTestRun();
        break;
      default:
        renderSimulator();
        break;
    }
  }

  private void renderSimulator() throws ProjectNotFoundException {
    new ProjectMainTemplate(project) {
      @Override
      protected String pageTitle() {
        return TITLE;
      }

      @Override
      protected String pageSubTitle() {
        return executable.getName();
      }

      @Override
      protected ArrayList<String> pageBreadcrumb() {
        return new ArrayList<String>(Arrays.asList(
          Html.a(ProjectsComponent.getUrl(), "Projects"),
          Html.a(ProjectComponent.getUrl(project), project.getName()),
          Html.a(SimulatorsComponent.getUrl(project), "Simulators")
        ));
      }

      @Override
      protected String pageContent() {
        String content = "";

        ArrayList<Lte.FormError> errors = new ArrayList<>();

        content +=
          Html.form(getUrl(executable, "update"), Html.Method.Post,
            Lte.card(Html.fasIcon("terminal") + "Properties",
              Html.a(getUrl(executable, KEY_RUN),
                Html.span("right badge badge-secondary", null, "test run")
              ),
              Html.div(null,
                Lte.readonlyTextInputWithCopyButton("Simulator Bin Directory", executable.getBinDirectory().toString()),
                Lte.readonlyTextInput("Version ID", executable.getVersionId()),
                Lte.formInputGroup("text", "sim_cmd", "Simulator command", "", executable.getSimulationCommand(), errors),
                Lte.formInputGroup("text", "req_t", "Required thread", "", executable.getRequiredThread().toString(), errors),
                Lte.formInputGroup("text", "req_m", "Required memory (GB)", "", executable.getRequiredMemory().toString(), errors)
              ),
              Lte.formSubmitButton("success", "Update")
            )
          );


        /*
        ParameterGroup rootGroup = ParameterGroup.getRootInstance(simulator);

        content += Lte.card(Html.faIcon("list-alt") + "Parameter Models",
          Html.a(ParameterGroupComponent.getUrl(ParameterGroup.getRootInstance(simulator), ParameterGroupComponent.MODE_ADD_PARAMETER_GROUP), Html.faIcon("plus-square") + "Group") +
            "/" + Html.a(ParameterGroupComponent.getUrl(rootGroup, ParameterGroupComponent.MODE_ADD_PARAMETER), Html.faIcon("plus") + "Parameter"),
          Lte.table(null, new Lte.Table() {
            @Override
            public ArrayList<Lte.TableValue> tableHeaders() {
              ArrayList<Lte.TableValue> list = new ArrayList<>();
              list.add(new Lte.TableValue("width:8em;", "ID"));
              list.add(new Lte.TableValue("", "Name"));
              return list;
            }

            @Override
            public ArrayList<Lte.TableRow> tableRows() {
              ArrayList<Lte.TableRow> list = new ArrayList<>();
              for (Parameter model : Parameter.getList(rootGroup)) {
                list.add(new Lte.TableRow(
                  Html.a(ParameterComponent.getUrl(model), null, null, model.getShortId()),
                  model.getName())
                );
              }
              for (ParameterGroup group : ParameterGroup.getList(rootGroup)) {
                list.add(new Lte.TableRow(
                  Html.a(ParameterGroupComponent.getUrl(group), null, null, "*" + group.getShortId()),
                  group.getName())
                );
              }
              return list;
            }
          })
          , null, null, "p-0");

         */

        String defaultParametersText = executable.getDefaultParameters().toString(2);

        content +=
          Html.form(getUrl(executable, KEY_UPDATE_PARAMETERS), Html.Method.Post,
            Lte.card(Html.fasIcon("terminal") + "Default Parameters",
              Lte.cardToggleButton(false),
              Lte.divRow(
                Lte.divCol(Lte.DivSize.F12,
                  Lte.formJsonEditorGroup(KEY_DEFAULT_PARAMETERS, null, "tree", executable.getDefaultParameters().toString(), null)
                )
              ),
              Lte.formSubmitButton("success", "Update"),
              "collapsed-card.stop", null)
          );

        content += Lte.card(Html.fasIcon("file-import") + "Parameter Extractors",
          Html.a(ParameterExtractorComponent.getStaticUrl(executable, "add"), Html.fasIcon("plus-square")),
          Lte.table(null, new Lte.Table() {
            @Override
            public ArrayList<Lte.TableValue> tableHeaders() {
              return null;
            }

            @Override
            public ArrayList<Future<Lte.TableRow>> tableRows() {
              ArrayList<Future<Lte.TableRow>> list = new ArrayList<>();
              List<String> nameList = executable.getExtractorNameList();
              if (nameList != null) {
                for (String extractorName : nameList) {
                  list.add(Main.interfaceThreadPool.submit(() -> {
                    return new Lte.TableRow(
                        Html.a(ParameterExtractorComponent.getUrl(executable, extractorName), null, null, extractorName));
                    }
                  ));
                }
              }
              return list;
            }
          })
          , null, null, "p-0");

        content += Lte.card(Html.fasIcon("dolly-flatbed") + "Result Collectors",
          Html.a(ResultCollectorComponent.getStaticUrl(executable, "add"), Html.fasIcon("plus-square")),
          Lte.table(null, new Lte.Table() {
            @Override
            public ArrayList<Lte.TableValue> tableHeaders() {
              return null;
            }

            @Override
            public ArrayList<Future<Lte.TableRow>> tableRows() {
              ArrayList<Future<Lte.TableRow>> list = new ArrayList<>();
              List<String> nameList = executable.getCollectorNameList();
              if (nameList != null) {
                for (String collectorName : nameList) {
                  list.add(Main.interfaceThreadPool.submit(() -> {
                      return new Lte.TableRow(
                        Html.a(ResultCollectorComponent.getUrl(executable, collectorName), null, null, collectorName));
                    }
                  ));
                }
              }
              return list;
            }
          })
          , null, null, "p-0");

        content += Lte.card(Html.fasIcon("file") + "Files in Simulator Bin Directory",
          Lte.cardToggleButton(false),
          Lte.table("table-sm", new Lte.Table() {
            @Override
            public ArrayList<Lte.TableValue> tableHeaders() {
              return null;
            }

            @Override
            public ArrayList<Future<Lte.TableRow>> tableRows() {
              ArrayList<Future<Lte.TableRow>> list = new ArrayList<>();
              for (File child : executable.getBinDirectory().toFile().listFiles()) {
                list.add(Main.interfaceThreadPool.submit(() -> {
                  return new Lte.TableRow(child.getName());
                  }
                ));
              }
              return list;
            }
          })
          , null, "collapsed-card.stop", "p-0");

        return content;
      }
    }.render(this);
  }

  private void renderTestRun() throws ProjectNotFoundException {
    new ProjectMainTemplate(project) {
      @Override
      protected String pageTitle() {
        return executable.getName();
      }

      @Override
      protected String pageSubTitle() {
        return "TestRun";
      }

      @Override
      protected ArrayList<String> pageBreadcrumb() {
        return new ArrayList<String>(Arrays.asList(
          Html.a(ProjectsComponent.getUrl(), "Projects"),
          Html.a(ProjectComponent.getUrl(project), project.getName()),
          "Simulators",
          executable.getName()
        ));
      }

      @Override
      protected String pageContent() {
        String content = "";

        SimulatorRun latestRun = null;
        try {
          latestRun = executable.getLatestTestRun();
        } catch (RunNotFoundException e) { }

        if (latestRun != null) {

          SimulatorRun finalLatestRun = latestRun;
          content += Lte.card(Html.fasIcon("poll-h") + "Latest Run", null,
            Lte.table("table-condensed table-sm", new Lte.Table() {
              @Override
              public ArrayList<Lte.TableValue> tableHeaders() {
                ArrayList<Lte.TableValue> list = new ArrayList<>();
                list.add(new Lte.TableValue("width:6.5em;", "ID"));
                list.add(new Lte.TableValue("", "Host"));
                list.add(new Lte.TableValue("width:2em;", ""));
                return list;
              }

              @Override
              public ArrayList<Future<Lte.TableRow>> tableRows() {
                ArrayList<Future<Lte.TableRow>> list = new ArrayList<>();
                list.add(Main.interfaceThreadPool.submit(() -> {
                  return new Lte.TableRow(
                    Html.a(RunComponent.getUrl(project, finalLatestRun.getUuid()), finalLatestRun.getName()),
                    (finalLatestRun.getComputer() == null ? "NotFound" : finalLatestRun.getComputer().getName()),
                    Html.spanWithId(finalLatestRun.getId() + "-badge", finalLatestRun.getState().getStatusBadge())
                  );
                }));
                return list;
              }
            })
            , null, null, "p-0");
        }

        content +=
          Html.form(getUrl(executable, "run"), Html.Method.Post,
            Lte.card(Html.fasIcon("terminal") + "TestRun",
              null,
              Lte.divRow(
                Lte.divCol(Lte.DivSize.F12,
                  Lte.formSelectGroup(KEY_COMPUTER, "Host", Computer.getViableList().stream().map(computer -> computer.getName()).collect(Collectors.toList()), null),
                  Lte.formJsonEditorGroup(KEY_PARAMETERS, "Parameters", "tree", executable.getDefaultParameters().toString(2), null)
                )
              )
              ,Lte.formSubmitButton("primary", "Run")
            )
          );

        return content;
      }
    }.render(this);
  }

  void runSimulator() {
    SimulatorRun run = executable.runTest(Computer.find(request.queryParams(KEY_COMPUTER)), request.queryParams(KEY_PARAMETERS));
    response.redirect(RunComponent.getUrl(project, run.getUuid()));
  }

  void updateSimulator() {
    executable.setSimulatorCommand(request.queryParams("sim_cmd"));
    executable.setRequiredThread(Double.parseDouble(request.queryParams("req_t")));
    executable.setRequiredMemory(Double.parseDouble(request.queryParams("req_m")));
    response.redirect(getUrl(executable));
  }

  void updateDefaultParameters() {
    executable.setDefaultParameters(request.queryParams(KEY_DEFAULT_PARAMETERS));
    response.redirect(getUrl(executable));
  }

  public enum Mode {Default, Update, UpdateParameters, TestRun}
}
