package jp.tkms.waffle.web.component.job;

import jp.tkms.waffle.Main;
import jp.tkms.waffle.web.component.AbstractAccessControlledComponent;
import jp.tkms.waffle.web.component.computer.ComputersComponent;
import jp.tkms.waffle.web.component.project.workspace.run.RunComponent;
import jp.tkms.waffle.web.component.project.ProjectComponent;
import jp.tkms.waffle.web.component.project.executable.ExecutableComponent;
import jp.tkms.waffle.web.template.Html;
import jp.tkms.waffle.web.template.Lte;
import jp.tkms.waffle.web.template.MainTemplate;
import jp.tkms.waffle.data.job.Job;
import jp.tkms.waffle.exception.RunNotFoundException;
import jp.tkms.waffle.data.log.message.ErrorLogMessage;
import jp.tkms.waffle.data.log.message.WarnLogMessage;
import spark.Spark;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Map;
import java.util.concurrent.Future;

public class JobsComponent extends AbstractAccessControlledComponent {
  private Mode mode;

  public JobsComponent(Mode mode) {
    super();
    this.mode = mode;
  }

  public JobsComponent() {
    this(Mode.Default);
  }

  static public void register() {
    Spark.get(getUrl(), new JobsComponent());
    Spark.get(getUrl(Mode.Cancel, null), new JobsComponent(Mode.Cancel));
  }

  public static String getUrl() {
    return "/jobs";
  }

  public static String getUrl(Mode mode, Job job) {
    return "/jobs/" + mode.name() + "/" + (job == null ? ":id" : job.getId());
  }

  @Override
  public void controller() {
    if (mode == Mode.Cancel) {
      Job job = Job.getInstance(request.params("id"));
      if (job != null) {
        try {
          job.cancel();
        } catch (RunNotFoundException e) {
          ErrorLogMessage.issue(e);
        }
        response.redirect(getUrl());
      }
    } else {
     renderJobList();
    }
  }

  private void renderJobList() {
    new MainTemplate() {
      @Override
      protected ArrayList<Map.Entry<String, String>> pageNavigation() {
        return null;
      }

      @Override
      protected String pageTitle() {
        return "Jobs";
      }

      @Override
      protected ArrayList<String> pageBreadcrumb() {
        return new ArrayList<String>(Arrays.asList(
          "Jobs"));
      }

      @Override
      protected String pageContent() {
        return
          Lte.card(null, null,
          Lte.table("table-condensed table-sm", new Lte.Table() {
            @Override
            public ArrayList<Lte.TableValue> tableHeaders() {
              ArrayList<Lte.TableValue> list = new ArrayList<>();
              list.add(new Lte.TableValue("width:6.5em;", "ID"));
              list.add(new Lte.TableValue("", "Project"));
              list.add(new Lte.TableValue("", "Simulator"));
              list.add(new Lte.TableValue("", "Host"));
              list.add(new Lte.TableValue("width:5em;", "JobID"));
              list.add(new Lte.TableValue("width:3em;", ""));
              list.add(new Lte.TableValue("width:1em;", ""));
              return list;
            }

            @Override
            public ArrayList<Future<Lte.TableRow>> tableRows() {
              ArrayList<Future<Lte.TableRow>> list = new ArrayList<>();
              for (Job job : Job.getList()) {
                list.add(Main.interfaceThreadPool.submit(() -> {
                  SimulatorRun run = job.getRun();
                  try {
                    return new Lte.TableRow(
                      Html.a(RunComponent.getUrl(job.getRun()), job.getHexCode()),
                      Html.a(
                        ProjectComponent.getUrl(job.getProject()),
                        job.getProject().getName()
                      ),
                      Html.a(
                        ExecutableComponent.getUrl(run.getSimulator()),
                        run.getSimulator().getName()
                      ),
                      Html.a(
                        ComputersComponent.getUrl(null, job.getComputer()),
                        job.getComputer().getName()
                      ),
                      job.getJobId(),
                      Html.spanWithId(job.getId() + "-badge", job.getState().getStatusBadge()),
                      Html.a(getUrl(Mode.Cancel, job), Html.fasIcon("times-circle"))
                    ).setAttributes(new Html.Attributes(Html.value("id", job.getId() + "-jobrow")));
                  } catch (Exception e) {
                    WarnLogMessage.issue(e);
                    return new Lte.TableRow("");
                  }
                }));
              }
              return list;
            }
          })
          , null, null, "p-0");
      }
    }.render(this);
  }

  public enum Mode {Default, Cancel}
}