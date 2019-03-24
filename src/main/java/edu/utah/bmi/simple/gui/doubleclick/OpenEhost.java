package edu.utah.bmi.simple.gui.doubleclick;

import com.mashape.unirest.http.HttpResponse;
import edu.utah.bmi.nlp.core.GUITask;
import edu.utah.bmi.simple.gui.entry.TaskFX;
import edu.utah.bmi.simple.gui.entry.TasksFX;
import edu.utah.bmi.simple.gui.task.ConfigKeys;
import edu.utah.bmi.simple.gui.task.ExecuteOsCommand;

import java.io.*;
import java.net.URL;
import java.net.URLConnection;
import java.util.Properties;
import java.util.logging.Logger;

import static java.lang.Thread.sleep;

public class OpenEhost extends GUITask {
    public static Logger logger = Logger.getLogger(OpenEhost.class.getCanonicalName());
    private File projectRoot;
    private String fileName;
    private TasksFX tasks;
    private String server = "";
    private String port = "";

    /**
     * @param tasks TasksFX configuration read from project_config.xml
     * @param args  If has two strings, the 1st string is either "ref" (show reference standard) or "exp" (show system output--previously exported)
     *              The 2nd string is the file name.
     *              If has one string, the string can be a file name, pointed to the reference standard
     *              or a txt file's (under an eHOST project) absolute path.
     */
    public OpenEhost(TasksFX tasks, String... args) {
        tasks = tasks;
//      Solve the project root and file name
        TaskFX task = tasks.getTask(ConfigKeys.imporTask);
        if (args.length > 1) {
            if (args[0].equals("ref")) {
                projectRoot = new File(task.getValue(ConfigKeys.annotationDir));
            } else {
                projectRoot = new File(tasks.getTask(ConfigKeys.exportTask).getValue(ConfigKeys.outputEhostDir),
                        tasks.getTask(ConfigKeys.maintask).getValue(ConfigKeys.annotator));
            }
            logger.fine("Display ehost project is set to: " + projectRoot.getAbsolutePath());
            fileName = args[1];
            logger.fine("Display file: " + fileName);
        } else if (args.length > 0) {
            fileName = args[0];
            File file = new File(fileName);
            if (file.exists()) {
                projectRoot = file.getParentFile().getParentFile();
                fileName = file.getName();
                logger.fine("Display ehost project is inferred as: " + projectRoot.getAbsolutePath());
            } else {
                projectRoot = new File(task.getValue(ConfigKeys.annotationDir));
            }
            logger.fine("Display file: " + fileName);
        }
        File txtFile = new File(new File(projectRoot, "corpus"), fileName);
        if (!txtFile.exists()) {
            logger.fine("File not find: " + txtFile.getAbsolutePath());
        }
        String[] configs = readEhostServerConfig();
        server = configs[0];
        port = configs[1];
    }

    public void run() {
        try {
            this.call();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    protected Object call() throws Exception {
        if (!checkEhostStatus(1, server, port)) {
            logger.fine("eHOST is not ready, try open it");
            openEhost();
        }
        logger.fine("Check ehost status after open it...");
        if (checkEhostStatus(10, server, port)) {
            showFile(projectRoot.getName(), fileName);
        } else {
//            updateGUIMessage("Ehost is not ready. Check if ehost jar is added and ehost configurations in side 'lib' directory.");
//            updateGUIProgress(0, 0);
        }
//        Unirest.shutdown();
        return null;
    }

    private void openEhost() {
        String command = "java -jar lib/ehost.jar " + projectRoot.getParentFile().getAbsolutePath() + "";
        logger.fine(ExecuteOsCommand.execute(command, false));
    }

    public static boolean checkEhostStatus(int timeout, String server, String port) {
        int count = 0;

        while (count < timeout) {
            try {
                logger.fine("Check status: " + count);
                String status =getRequest(String.format("http://%s:%s/status", server, port));
                logger.fine("Responded status: " + status);
                if (status != null && status.length() > 0 && status.startsWith("true"))
                    return true;
            } catch (Exception e) {
            }
//          need to separate sleep out, otherwise any exception will skip the sleep.
            try {
                sleep(1000);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            count++;
        }
        return false;
    }

    private boolean showFile(String projectName, String fileName) {
        HttpResponse<String> response;
        String status = "";
        try {
            status = getRequest(String.format("http://%s:%s/ehost/%s/%s", server, port, projectName, fileName));
//            updateGUIProgress(1, 1);
            return true;
        } catch (Exception e) {
            status = "Ehost server is not online.";
        }
//        updateGUIMessage(status);
//        updateGUIProgress(0, 0);
        return false;
    }

    public static void closeEhost(String server, String port) {
        if (checkEhostStatus(6, server, port)) {
            sendRequest(String.format("http://%s:%s/shutdown", server, port));
        }
    }

    public static void closeEhost() {
        String[] config = readEhostServerConfig();
        closeEhost(config[0], config[1]);
    }

    public static String[] readEhostServerConfig() {
        File ehostServerConifg = new File("application.properties");
        String[] configs = new String[]{"127.0.0.1", "8009"};
        if (ehostServerConifg.exists()) {
            Properties defaultProps = new Properties();
            FileInputStream in = null;
            try {
                in = new FileInputStream(ehostServerConifg);
                defaultProps.load(in);
                in.close();
                configs[0] = defaultProps.getProperty("server.address");
                configs[1] = defaultProps.getProperty("server.port");
                logger.fine("Read server config: " + configs[0]);
                logger.fine("Read port config: " + configs[1]);
            } catch (FileNotFoundException e) {
                e.printStackTrace(); e.printStackTrace();
            } catch (IOException e) {
                e.printStackTrace();
            }
        } else {
            logger.fine("Use default ehost server 127.0.0.1 and port config 8009.");
        }
        return configs;
    }

    public static String getRequest(String url) {
        StringBuilder sb = new StringBuilder();
        try {
            URL oracle = new URL(url);
            URLConnection yc = null;
            yc = oracle.openConnection();
            BufferedReader in = new BufferedReader(new InputStreamReader(
                    yc.getInputStream()));
            String inputLine;
            while ((inputLine = in.readLine()) != null)
                sb.append(inputLine + "\n");
            in.close();
        } catch (Exception e) {
            logger.fine(e.toString());
        }
        return sb.toString().trim();
    }

    public static void sendRequest(String url, int... timeouts) {
        int timeout = 5000;
        if (timeouts.length > 0)
            timeout = timeouts[0];
        StringBuilder sb = new StringBuilder();
        try {
            URL oracle = new URL(url);
            URLConnection yc = null;
            yc = oracle.openConnection();
            yc.setConnectTimeout(timeout);
            Object in = yc.getContent();
            if (in instanceof InputStream)
                ((InputStream)in).close();
        } catch (Exception e) {
            logger.fine(e.toString());
        }
    }
}
