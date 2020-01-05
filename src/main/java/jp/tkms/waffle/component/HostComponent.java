package jp.tkms.waffle.component;

import jp.tkms.waffle.component.template.Html;
import jp.tkms.waffle.component.template.Lte;
import jp.tkms.waffle.component.template.MainTemplate;
import jp.tkms.waffle.data.Host;
import jp.tkms.waffle.data.Project;
import org.json.JSONObject;
import spark.Spark;

import java.util.ArrayList;
import java.util.Arrays;

public class HostComponent extends AbstractAccessControlledComponent {
  private static final String KEY_WORKBASE = "work_base_dir";
  private static final String KEY_XSUB = "xsub_dir";
  private static final String KEY_POLLING = "polling_interval";
  private static final String KEY_MAX_JOBS = "maximum_jobs";
  private static final String KEY_PARAMETERS = "parameters";
  private Mode mode;

  ;
  private Host host;
  public HostComponent(Mode mode) {
    super();
    this.mode = mode;
  }

  public HostComponent() {
    this(Mode.Default);
  }

  static public void register() {
    Spark.get(getUrl(null), new HostComponent());
    Spark.post(getUrl(null, "update"), new HostComponent(Mode.Update));
  }

  public static String getUrl(Host host) {
    return "/host/" + (host == null ? ":id" : host.getId());
  }

  public static String getUrl(Host host, String mode) {
    return getUrl(host) + '/' + mode;
  }

  @Override
  public void controller() {
    host = Host.getInstance(request.params("id"));
    switch (mode) {
      case Update:
        updateHost();
        break;
      default:
        renderHost();
    }
  }

  private void renderHost() {
    new MainTemplate() {
      @Override
      protected String pageTitle() {
        return host.getName();
      }

      @Override
      protected ArrayList<String> pageBreadcrumb() {
        return new ArrayList<String>(Arrays.asList(
          Html.a(HostsComponent.getUrl(), "Hosts"),
          host.getId()
        ));
      }

      @Override
      protected String pageContent() {
        String content = "";

        ArrayList<Lte.FormError> errors = new ArrayList<>();

        content += Lte.card(Html.faIcon("terminal") + "Properties",
          null,
          Html.form(getUrl(host, "update"), Html.Method.Post,
            Html.div(null,
              Lte.formInputGroup("text", KEY_XSUB,
                "Xsub directory on host",
                "depends on $PATH", host.getXsubDirectory(), errors),
              Lte.formInputGroup("text", KEY_WORKBASE,
                "Work base directory on host", "", host.getWorkBaseDirectory(), errors),
              Lte.formInputGroup("text", KEY_MAX_JOBS,
                "Maximum number of jobs", "", host.getMaximumNumberOfJobs().toString(), errors),
              Lte.formInputGroup("text", KEY_POLLING,
                "Polling interval (seconds)", "", host.getPollingInterval().toString(), errors),
              Lte.formTextAreaGroup(KEY_PARAMETERS, "Parameters", 10, host.getParameters().toString(2), null),
              Lte.formSubmitButton("success", "Update")
            )
          )
          , null);

        return content;
      }
    }.render(this);
  }

  private ArrayList<Lte.FormError> checkCreateProjectFormError() {
    return new ArrayList<>();
  }

  private ArrayList<Lte.TableValue> getProjectTableHeader() {
    ArrayList<Lte.TableValue> list = new ArrayList<>();
    list.add(new Lte.TableValue("width:8em;", "ID"));
    list.add(new Lte.TableValue("", "Name"));
    return list;
  }

  private ArrayList<Lte.TableRow> getProjectTableRow() {
    ArrayList<Lte.TableRow> list = new ArrayList<>();
    for (Project project : Project.getList()) {
      list.add(new Lte.TableRow(
        Html.a("", null, null, project.getShortId()),
        project.getName())
      );
    }
    return list;
  }

  private void updateHost() {
    host.setXsubDirectory(request.queryParams(KEY_XSUB));
    host.setWorkBaseDirectory(request.queryParams(KEY_WORKBASE));
    host.setMaximumNumberOfJobs(Integer.parseInt(request.queryParams(KEY_MAX_JOBS)));
    host.setPollingInterval(Integer.parseInt(request.queryParams(KEY_POLLING)));
    host.setParameters(new JSONObject(request.queryParams(KEY_PARAMETERS)));
    response.redirect(getUrl(host));
  }

  public enum Mode {Default, Update}
}