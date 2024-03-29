package jp.tkms.waffle.web.component.misc;

import jp.tkms.waffle.data.web.BrowserMessage;
import jp.tkms.waffle.script.ruby.util.RubyScript;
import jp.tkms.waffle.web.component.AbstractAccessControlledComponent;
import jp.tkms.waffle.web.component.ResponseBuilder;
import jp.tkms.waffle.web.updater.GeneralUpdater;
import spark.Spark;

public class BrowserMessageComponent extends AbstractAccessControlledComponent {
  private static final String KEY_CURRENT_ROWID = "cid";

  public static void register() {
    Spark.post(getUrl(null), new ResponseBuilder(() -> new BrowserMessageComponent()));
  }

  public static String getUrl(String id) {
    return "/bm" + (id == null ? "/:cid" : "/" + id);
  }

  @Override
  public void controller() {
    //String result = "try{rubyRunningStatus(" + (RubyScript.hasRunning() ? "true" : "false") + ");}catch(e){}";
    //response.body(result);
    String result = "try{" + GeneralUpdater.getUpdateScriptDirectly(request.body()) + "}catch(e){}";

    String browserId = request.params(KEY_CURRENT_ROWID);

    try {
      for (BrowserMessage message : BrowserMessage.getList(Long.valueOf(browserId))) {
        result += "cid=" + message.getRowId() + ";" + message.getMessage() + ";";
      }
    } catch (Exception e) {}

    response.body(result);
  }
}
