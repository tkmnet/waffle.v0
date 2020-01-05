package jp.tkms.waffle.data;

import jp.tkms.waffle.Environment;
import jp.tkms.waffle.data.util.Sql;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.*;
import java.util.Properties;

public class Database implements AutoCloseable {
  static Logger logger = LoggerFactory.getLogger("Database");

  static final String SYSTEM_TABLE = "system";
  static final String KEY_NAME = "name";
  static final String KEY_VALUE = "value";

  private static boolean initialized = false;
  private Connection connection;
  private String url;
  private static Properties properties;

  private Database(String url) throws SQLException {
    this.url = url;
    this.connection = DriverManager.getConnection(url, properties);
    connection.setAutoCommit(false);
  }

  private static void initialize() {
    if (!initialized) {
      try {
        Class.forName("org.sqlite.JDBC");
      } catch (ClassNotFoundException e) {
        e.printStackTrace();
      }

      properties = new Properties();
      properties.put("journal_mode", "MEMORY");
    }
  }

  public static synchronized Database getDatabase(Path path) {
    if (path == null) {
      path = Paths.get(Environment.MAIN_DB_NAME);
    }

    initialize();
    Database db = null;
    String url = "jdbc:sqlite:" + path.toAbsolutePath().toString();

    try {
      db = new Database(url);
    } catch (SQLException e) {
      e.printStackTrace();
    }

    return db;
  }

  public void commit() throws SQLException {
    connection.commit();
  }

  public void close() throws SQLException {
    connection.close();
  }

  public int getVersion(String tag) {
    if (tag == null) {
      tag = "";
    }
    int version = -1;
    try (ResultSet resultSet = executeQuery("select value from " + SYSTEM_TABLE + " where name='version-" + tag + "';")) {
      while (resultSet.next()) {
        version = resultSet.getInt(1);
      }
    } catch (SQLException e) {
      logger.info("Add new main database");
    }
    if (version == -1) {
      try {
        execute("create table if not exists system(name,value);");
        execute("insert into system(name,value) values('version-" + tag + "',0);");
        version = 0;
        commit();
      } catch (SQLException e) {
        logger.error(e.getMessage());
      }
    }
    return version;
  }

  public void setVersion(String tag, int version) {
    try {
      execute("update system set value=" + version + " where name='version-" + tag + "';");
    } catch (SQLException e) {
      logger.error(e.getMessage());
    }
  }

  public void execute(String sql) throws SQLException {
    Statement statement = connection.createStatement();
    statement.execute(sql);
  }

  public ResultSet executeQuery(String sql) throws SQLException {
    Statement statement = connection.createStatement();
    return statement.executeQuery(sql);
  }

  public PreparedStatement preparedStatement(String sql) throws SQLException {
    return connection.prepareStatement(sql);
  }

  public Sql.Insert createInsert(String table, String... keys) {
    return new Sql.Insert(this, table, keys);
  }

  public Sql.Select createSelect(String table, String... keys) {
    return new Sql.Select(this, table, keys);
  }
}