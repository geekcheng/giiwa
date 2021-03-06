/*
 * Copyright 2015 Giiwa, Inc. and/or its affiliates.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * 
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
*/
package org.giiwa.app.web;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.HashMap;
import java.util.Map;

import net.sf.json.JSONArray;

import org.apache.commons.configuration.Configuration;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.giiwa.app.web.admin.setting;
import org.giiwa.core.bean.Bean;
import org.giiwa.core.bean.Beans;
import org.giiwa.core.bean.KeyField;
import org.giiwa.core.bean.UID;
import org.giiwa.core.bean.X;
import org.giiwa.core.conf.Global;
import org.giiwa.core.db.DB;
import org.giiwa.core.task.Task;
import org.giiwa.framework.bean.AccessLog;
import org.giiwa.framework.bean.Menu;
import org.giiwa.framework.bean.OpLog;
import org.giiwa.framework.bean.Repo;
import org.giiwa.framework.bean.Temp;
import org.giiwa.framework.bean.User;
import org.giiwa.framework.utils.FileUtil;
import org.giiwa.framework.utils.Shell;
import org.giiwa.framework.web.Language;
import org.giiwa.framework.web.IListener;
import org.giiwa.framework.web.Model;
import org.giiwa.framework.web.Module;

import com.mongodb.BasicDBObject;

// TODO: Auto-generated Javadoc
/**
 * startup life listener.
 * 
 * @author joe
 * 
 */
public class DefaultListener implements IListener {

  public static final DefaultListener owner = new DefaultListener();

  /**
   * backup.
   * 
   * @author joe
   *
   */
  public static class BackupTask extends Task {

    /**
     * Path.
     *
     * @return the string
     */
    public static String path() {
      return Global.s("backup.path", "/opt/backup");
    }

    @Override
    public String getName() {
      return "backup.task";
    }

    /*
     * (non-Javadoc)
     * 
     * @see org.giiwa.core.task.Task#onExecute()
     */
    @Override
    public void onExecute() {
      Module m = Module.home;
      try {

        String out = path() + "/" + Language.getLanguage().format(System.currentTimeMillis(), "yyyyMMddHHmm");
        new File(out).mkdirs();

        File f = m.getFile("/admin/clone/backup_db.sh");
        Shell.run("chmod ugo+x " + f.getParent() + "/*.sh");

        /**
         * 1, backup postgresql
         */
        String url = conf.getString("db.url", null);
        if (!X.isEmpty(url)) {
          int i = url.indexOf("?");
          if (i > 0) {
            url = url.substring(0, i);
          }
          i = url.lastIndexOf("/");
          if (i > 0) {
            url = url.substring(i + 1);
          }

          String r = Shell.run(f.getCanonicalPath() + " " + url + " " + out + "/" + url + ".dmp");
          log.debug("result: " + r);
        }

        /**
         * 2, backup mongo
         */
        f = m.getFile("/admin/clone/backup_mongo.sh");
        url = conf.getString("mongo[prod].url", null);
        if (!X.isEmpty(url)) {
          String db = conf.getString("mongo[prod].db", "demo");

          url = url.split(";")[0];
          int i = url.indexOf(":");
          if (i > 0) {
            String host = url.substring(0, i);
            String port = url.substring(i + 1);

            Shell.run(f.getCanonicalPath() + " " + host + " " + port + " " + db + " " + out);
          }
        }

        /**
         * 3, backup repo
         */
        f = m.getFile("/admin/clone/backup_tar.sh");
        url = conf.getString("repo.path", null);
        if (!X.isEmpty(url)) {
          Shell.run(f.getCanonicalPath() + " " + out + "/repo.tar.gz " + url);
        }

      } catch (Exception e) {
        log.error(e.getMessage(), e);
      }
    }

  };

  private static class NtpTask extends Task {

    static NtpTask owner = new NtpTask();

    private NtpTask() {
    }

    @Override
    public void onExecute() {
      String ntp = Global.s("ntp.server", null);
      if (!X.isEmpty((Object) ntp)) {
        try {
          String r = Shell.run("ntpdate -u " + ntp);
          OpLog.info("ntp", X.EMPTY, "时钟同步： " + r);
        } catch (Exception e) {
          OpLog.error("ntp", X.EMPTY, "时钟同步： " + e.getMessage());
        }
      }
    }

    @Override
    public void onFinish() {
      this.schedule(X.AHOUR);
    }
  }

  static Log log = LogFactory.getLog(DefaultListener.class);

  /*
   * (non-Javadoc)
   * 
   * @see org.giiwa.framework.web.LifeListener#onStart(org.apache.commons.
   * configuration.Configuration, org.giiwa.framework.web.Module)
   */
  public void onStart(Configuration conf, Module module) {

    /**
     * clean up the old version's jar
     */
    if (cleanup(new File(Model.HOME), new HashMap<String, FileUtil>())) {
      System.exit(0);
      return;
    }

    if (log.isDebugEnabled()) {
      log.debug("upgrade.enabled=" + Global.s(conf.getString("node") + ".upgrade.framework.enabled", "false"));
    }

    // cleanup
    // File f = new File(Model.HOME +
    // "/WEB-INF/lib/mina-core-2.0.0-M4.jar");
    // if (f.exists()) {
    // f.delete();
    // System.exit(0);
    // }

    setting.register("system", setting.system.class);
    setting.register("sync", setting.sync.class);
    setting.register("smtp", setting.mail.class);
    setting.register("counter", setting.counter.class);

    NtpTask.owner.schedule(X.AMINUTE);
    new CleanupTask(conf).schedule(X.AMINUTE);
    // new VersionCheckTask().schedule(X.AMINUTE);
    // new SorterTask().schedule(X.AMINUTE);
    new AppdogTask().schedule(X.AMINUTE);

    /**
     * check and initialize
     */
    User.checkAndInit();

  }

  /*
   * (non-Javadoc)
   * 
   * @see org.giiwa.framework.web.LifeListener#onStop()
   */
  public void onStop() {
  }

  /**
   * Run db script.
   *
   * @param f
   *          the f
   * @throws IOException
   *           Signals that an I/O exception has occurred.
   * @throws SQLException
   *           the SQL exception
   */
  public static void runDBScript(File f) throws IOException, SQLException {
    BufferedReader in = null;
    Connection c = null;
    Statement s = null;
    try {
      c = Bean.getConnection();
      if (c != null) {
        in = new BufferedReader(new InputStreamReader(new FileInputStream(f), "utf-8"));
        StringBuilder sb = new StringBuilder();
        try {
          String line = in.readLine();
          while (line != null) {
            line = line.trim();
            if (!"".equals(line) && !line.startsWith("#")) {

              sb.append(line).append("\r\n");

              if (line.endsWith(";")) {
                String sql = sb.toString().trim();

                try {
                  s = c.createStatement();
                  s.executeUpdate(sql);
                  s.close();
                } catch (Exception e) {
                  log.error(sb.toString(), e);
                }
                s = null;
                sb = new StringBuilder();
              }
            }
            line = in.readLine();
          }

          String sql = sb.toString().trim();
          if (!"".equals(sql)) {
            s = c.createStatement();
            s.executeUpdate(sql);
          }
        } catch (Exception e) {
          if (log.isErrorEnabled()) {
            log.error(sb.toString(), e);
          }
        }
      } else {
        if (log.isWarnEnabled()) {
          log.warn("database not configured !");
        }
      }
    } catch (Exception e) {
      log.error(e.getMessage(), e);
    } finally {
      if (in != null) {
        in.close();
      }
      Bean.close(s, c);
    }

  }

  /*
   * (non-Javadoc)
   * 
   * @see org.giiwa.framework.web.LifeListener#upgrade(org.apache.commons.
   * configuration.Configuration, org.giiwa.framework.web.Module)
   */
  public void upgrade(Configuration conf, Module module) {
    if (log.isDebugEnabled()) {
      log.debug(module + " upgrading...");
    }

    /**
     * test database connection has configured?
     */
    try {
      /**
       * test the database has been installed?
       */
      String dbname = DB.getDriver();

      if (!X.isEmpty(dbname) && DB.isConfigured()) {
        /**
         * initial the database
         */
        File f = module.loadResource("/install/" + dbname + "/initial.sql", false);
        if (f != null && f.exists()) {
          String key = module.getName() + ".db.initial." + dbname + "." + f.lastModified();
          int b = Global.i(key, 0);
          if (b == 0) {
            if (log.isWarnEnabled()) {
              log.warn("db[" + key + "] has not been initialized! initializing...");
            }

            try {
              runDBScript(f);
              Global.setConfig(key, (int) 1);
              if (log.isWarnEnabled()) {
                log.warn("db[" + key + "] has been initialized! ");
              }
            } catch (Exception e) {
              if (log.isErrorEnabled()) {
                log.error(f.getAbsolutePath(), e);
              }
            }

          }
        } else {
          if (log.isWarnEnabled()) {
            log.warn("db[" + module.getName() + "." + dbname + "] not exists ! ");
          }
        }

        f = module.loadResource("/install/" + dbname + "/upgrade.sql", false);
        if (f != null && f.exists()) {
          String key = module.getName() + ".db.upgrade." + dbname + "." + f.lastModified();
          int b = Global.i(key, 0);

          if (b == 0) {

            try {
              runDBScript(f);

              Global.setConfig(key, (int) 1);

              if (log.isWarnEnabled()) {
                log.warn("db[" + key + "] has been upgraded! ");
              }
            } catch (Exception e) {
              if (log.isErrorEnabled()) {
                log.error(f.getAbsolutePath(), e);
              }
            } finally {
            }

          }
        }
      } else {
        if (log.isErrorEnabled()) {
          log.error("DB was miss configured, please congiure it in [" + Model.GIIWA_HOME + "/giiwa/giiwa.properties]");
        }
      }
    } catch (Exception e) {
      if (log.isErrorEnabled()) {
        log.error("database is not configured!", e);
      }
      return;
    }

    if (Bean.isConfigured()) {
      /**
       * check the menus
       * 
       */
      File f = module.getFile("/install/menu.json");
      if (f != null && f.exists()) {
        BufferedReader reader = null;
        try {
          if (log.isDebugEnabled()) {
            log.debug("initialize [" + f.getCanonicalPath() + "]");
          }

          reader = new BufferedReader(new InputStreamReader(new FileInputStream(f), "UTF-8"));
          StringBuilder sb = new StringBuilder();
          String line = reader.readLine();
          while (line != null) {
            sb.append(line).append("\r\n");
            line = reader.readLine();
          }

          /**
           * convert the string to json array
           */
          JSONArray arr = JSONArray.fromObject(sb.toString());
          Menu.insertOrUpdate(arr, module.getName());

        } catch (Exception e) {
          if (log.isErrorEnabled()) {
            log.error(e.getMessage(), e);
          }
        } finally {
          if (reader != null) {
            try {
              reader.close();
            } catch (IOException e) {
              if (log.isErrorEnabled()) {
                log.error(e);
              }
            }
          }
        }
      }
    } else {
      if (log.isErrorEnabled()) {
        log.error("Mongo was miss configured, please congiure it in [" + Model.GIIWA_HOME + "/giiwa/giiwa.properties]");
      }
      return;
    }
  }

  /*
   * (non-Javadoc)
   * 
   * @see org.giiwa.framework.web.LifeListener#uninstall(org.apache.commons.
   * configuration.Configuration, org.giiwa.framework.web.Module)
   */
  public void uninstall(Configuration conf, Module module) {
    Menu.remove(module.getName());
  }

  /**
   * 
   * @param f
   * @param map
   * @return
   */
  private boolean cleanup(File f, Map<String, FileUtil> map) {
    /**
     * list and compare all jar files
     */
    boolean changed = false;

    if (f.isDirectory()) {
      for (File f1 : f.listFiles()) {
        if (cleanup(f1, map)) {
          changed = true;
        }
      }
    } else if (f.isFile() && f.getName().endsWith(".jar")) {
      FileUtil f1 = new FileUtil(f);
      String name = f1.getName();

      FileUtil f2 = map.get(name);
      if (f2 == null) {
        map.put(f1.getName(), f1);
      } else {
        FileUtil.R r = f1.compareTo(f2);
        if (r == FileUtil.R.HIGH || r == FileUtil.R.SAME) {
          // remove f2
          if (log.isWarnEnabled()) {
            log.warn("delete duplicated jar file, but low version:" + f2.getFile().getAbsolutePath() + ", keep: "
                + f2.getFile().getAbsolutePath());
          }
          f2.getFile().delete();
          map.put(name, f1);
        } else if (r == FileUtil.R.LOW) {
          // remove f1;
          if (log.isWarnEnabled()) {
            log.warn("delete duplicated jar file, but low version:" + f1.getFile().getAbsolutePath() + ", keep: "
                + f1.getFile().getAbsolutePath());
          }
          f1.getFile().delete();
        }
      }
    }

    return changed;
  }

  /**
   * The main method.
   *
   * @param args
   *          the arguments
   * @deprecated
   */
  public static void main(String[] args) {
    DefaultListener d = new DefaultListener();
    File f = new File("/home/joe/d/workspace/");
    Map<String, FileUtil> map = new HashMap<String, FileUtil>();
    d.cleanup(f, map);
    System.out.println(map);

  }

  /**
   * check the new version
   * 
   * @author joe
   *
   */
  // private static class VersionCheckTask extends Task {
  //
  // @Override
  // public String getName() {
  // return "versioncheck.task";
  // }
  //
  // @Override
  // public void onExecute() {
  // Response res = Http.post("http://giiwa.org/version",
  // new String[][] { { "version", Module.load("default").getVersion() },
  // { "build", Module.load("default").getBuild() } });
  // if (res.status == 200 && !X.isEmpty(res.body)) {
  // JSONObject jo = JSONObject.fromObject(res.body);
  // String ver = jo.getString("version");
  // String build = jo.getString("build");
  // if (!X.isSame(ver, Module.load("default").getVersion())
  // || !X.isSame(build, Module.load("default").getBuild())) {
  // ConfigGlobal.setConfig("notification.enabled", 1);
  // ConfigGlobal.setConfig("notification.message",
  // Language.getLanguage().get("notification.newbuild")
  // + ": <a href='http://giiwa.org' target='_blank'>" + ver + "." + build +
  // "</a>");
  // }
  // }
  // }
  //
  // }

  private static class SorterTask extends Task {

    @Override
    public String getName() {
      return "sorter.task";
    }

    @Override
    public void onExecute() {
      int s = 0;

      BasicDBObject q = new BasicDBObject("status", new BasicDBObject("$ne", "done"));
      BasicDBObject order = new BasicDBObject();

      Beans<KeyField> bs = KeyField.load(q, order, s, 10);
      while (bs != null && bs.getList() != null && bs.getList().size() > 0) {

        for (KeyField f : bs.getList()) {
          f.run();
        }
        s += bs.getList().size();
        bs = KeyField.load(q, order, s, 10);

      }
    }

  }

  /**
   * check the appdog has been setup proper
   * 
   * @author joe
   *
   */
  private static class AppdogTask extends Task {

    @Override
    public String getName() {
      return "appdog.task";
    }

    @Override
    public void onExecute() {
      File f = new File("/etc/init.d/appdog");
      if (!f.exists()) {
        // copy one to there
        f = Module.home.getFile("/admin/clone/etc/init.d/appdog");
        if (f.exists()) {
          // copying

          if (Shell.isLinux()) {
            BufferedReader in = null;
            PrintStream out = null;
            try {
              Shell.run("cp " + f.getCanonicalPath() + " /etc/init.d/");
              Shell.run("chmod ugo+x /etc/init.d/appdog");

              if (Shell.isUbuntu()) {
                Shell.run("sysv-rc-conf --add appdog");
                Shell.run("sysv-rc-conf appdog on");
              } else {
                Shell.run("chkconfig --add appdog");
                Shell.run("chkconfig appdog on");
              }

              // check the apps.conf
              f = Module.home.getFile("/admin/clone/etc/appdog/apps.conf");
              Shell.run("mkdir /etc/appdog");
              Shell.run("cp " + f.getCanonicalPath() + " /etc/appdog/");

              // check the application is in appdog ?
              f = new File("/etc/appdog/apps.conf");
              String bin = Model.GIIWA_HOME + "/bin";

              in = new BufferedReader(new FileReader(f));
              String line = in.readLine();
              boolean found = false;
              while (line != null) {
                if (line.indexOf(bin) > 0) {
                  found = true;
                  break;
                }
              }
              in.close();
              in = null;
              if (!found) {
                out = new PrintStream(new FileOutputStream(f, true));
                /**
                 * [app:giiwa_xxxxx]<br>
                 * start=/opt/giiwa/bin/startup.sh<br>
                 * pattern=/opt/giiwa/bin<br>
                 * path=/opt/giiwa/bin<br>
                 * user=<br>
                 * check=0.5<br>
                 * enabled=1<br>
                 */
                out.println();
                out.println("[app:giiwa_" + UID.random(5) + "]");
                out.println("start=" + bin + "/startup.sh");
                out.println("pattern=" + bin);
                out.println("path=" + bin);
                out.println("user=");
                out.println("check=0.5");
                out.println("enabled=1");
                out.close();
                out = null;
              }

              Shell.run("/etc/init.d/appdog start");

            } catch (Exception e) {
              log.error(e.getMessage(), e);
            } finally {
              if (in != null) {
                try {
                  in.close();
                } catch (IOException e) {
                }
              }
              if (out != null) {
                out.close();
              }
            }
          } else {
            log.warn("[giiwa] can be more effective in [Linux]");
            OpLog.warn(null, "[giiwa] can be more effective in [Linux]", null);
          }
        }
      }

    }
  }

  /**
   * clean up the oplog, temp file in Temp
   * 
   * @author joe
   * 
   */
  private static class CleanupTask extends Task {

    static Log log = LogFactory.getLog(CleanupTask.class);

    String     home;

    /**
     * Instantiates a new cleanup task.
     * 
     * @param conf
     *          the conf
     */
    public CleanupTask(Configuration conf) {
      home = Model.GIIWA_HOME;
    }

    @Override
    public String getName() {
      return "cleanup.task";
    }

    /*
     * (non-Javadoc)
     * 
     * @see org.giiwa.worker.WorkerTask#onExecute()
     */
    @Override
    public void onExecute() {
      try {
        /**
         * clean up the local temp files
         */
        int count = 0;
        for (String f : folders) {
          String path = home + f;
          count += cleanup(path, X.ADAY);
        }

        /**
         * clean files in Temp
         */
        if (!X.isEmpty(Temp.ROOT)) {
          count += cleanup(Temp.ROOT, X.ADAY);
        }

        /**
         * clean temp files in tomcat
         */
        if (!X.isEmpty(Model.GIIWA_HOME)) {
          // do it
          count += cleanup(Model.GIIWA_HOME + "/work", X.ADAY);
          count += cleanup(Model.GIIWA_HOME + "/logs", X.ADAY * 3);
        }
        if (log.isInfoEnabled()) {
          log.info("cleanup temp files: " + count);
        }

        // OpLog.cleanup();

        // AccessLog.cleanup();

        /**
         * cleanup repo
         */
        Repo.cleanup();

      } catch (Exception e) {
        // eat the exception
      }
    }

    private int cleanup(String path, long expired) {
      int count = 0;
      try {
        File f = new File(path);

        /**
         * test the file last modified exceed the cache time
         */
        if (f.isFile() && System.currentTimeMillis() - f.lastModified() > expired) {
          f.delete();
          if (log.isInfoEnabled()) {
            log.info("delete file: " + f.getCanonicalPath());
          }
          count++;
        } else if (f.isDirectory()) {
          File[] list = f.listFiles();
          if (list != null) {
            /**
             * cleanup the sub folder
             */
            for (File f1 : list) {
              count += cleanup(f1.getAbsolutePath(), expired);
            }
          }
        }
      } catch (Exception e) {
        if (log.isErrorEnabled()) {
          log.error(e.getMessage(), e);
        }
      }

      return count;
    }

    /*
     * (non-Javadoc)
     * 
     * @see org.giiwa.worker.WorkerTask#priority()
     */
    @Override
    public int priority() {
      return Thread.MIN_PRIORITY;
    }

    /*
     * (non-Javadoc)
     * 
     * @see org.giiwa.worker.WorkerTask#onFinish()
     */
    @Override
    public void onFinish() {
      this.schedule(X.AHOUR);
    }

    static String[] folders = { "/temp/_cache", "/temp/_raw" };
  }

}
