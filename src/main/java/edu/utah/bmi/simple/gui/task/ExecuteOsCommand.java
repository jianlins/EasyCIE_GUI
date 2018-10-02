package edu.utah.bmi.simple.gui.task;


import edu.utah.bmi.nlp.core.IOUtil;
import edu.utah.bmi.simple.gui.entry.TasksFX;
import javafx.concurrents.Task;
import org.codehaus.plexus.util.cli.CommandLineUtils;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * @author Jianlin Shi
 * Created on 2/13/17.
 */
public class ExecuteOsCommand extends Task {
    protected String command;
    public static Logger logger = IOUtil.getLogger(ExecuteOsCommand.class);

    public ExecuteOsCommand(TasksFX tasks, String args) {
        initiate(tasks, args);
    }

    private void initiate(TasksFX tasks, String args) {
        command = smartParseArgs(tasks, args);
    }

    /**
     * Use argument in the format of '-x taskName/configKey', can read values from EasyCIE configurations
     * For example: "gov.va.ehost.Ehost -x export/format/ehost" will read the configuration value of "format/ehost" from the "export" guitask,
     * and replace the "-x export/format/ehost" with the actual value as the argument to execute "gov.va.ehost.Ehost"
     *
     * @param tasks configuration tasks
     * @param args  arguments string to execute the command
     * @return value-filled arguments string
     */
    private String smartParseArgs(TasksFX tasks, String args) {
        StringBuilder output = new StringBuilder();
        try {
            String[] myArgs = CommandLineUtils.translateCommandline(args);
            if (myArgs.length > 1) {
                output.append(myArgs[0]);
                for (int i = 1; i < myArgs.length; i++) {
                    if (myArgs[i].equals("-x")) {
                        if (myArgs.length > i) {
                            String para = myArgs[i + 1];
                            int split = para.indexOf("/");
                            String taskName = para.substring(0, split).replaceAll("//", "");
                            String configKey = para.substring(split + 1);
                            output.append(" " + tasks.getTask(taskName).getValue(configKey));
                            i++;
                        } else {
                            logger.warning("'-x' parameter is supposed to follow a setting name that will read the configuration value from your EasyCIE settings.");
                        }
                    } else {
                        output.append(" " + myArgs[i]);
                    }
                }
            } else {
                output.append(args);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return output.toString();
    }


    @Override
    protected Object call() throws Exception {
        updateMessage("execute: " + command);
        java.lang.Runtime rt = java.lang.Runtime.getRuntime();
        // Start a new process: UNIX command ls
        Process p = null;
        StringBuilder stb = new StringBuilder();
        try {
            p = rt.exec(command);

            // Show exit code of process

            BufferedReader b = new BufferedReader(new InputStreamReader(p.getInputStream()));
            String line ;
            while ((line = b.readLine()) != null) {
                if (line.startsWith("prg:")) {
                    String[] progressNums = line.substring(4).trim().split(",");
                    Long workDone = Long.parseLong(progressNums[0]);
                    Long max = Long.parseLong(progressNums[1]);
                    updateProgress(workDone, max);
                } else if (line.startsWith("msg:")) {
                    updateMessage(line.substring(4));
                } else {
                    stb.append(line);
                    stb.append("\n");
                }
            }
            p.waitFor();
            b.close();
        } catch (IOException e) {
            e.printStackTrace();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        updateProgress(1, 1);
        if (logger.isLoggable(Level.FINE)) {
            updateMessage("Execute Results:|Command \"" + command + "\":|" + stb.toString() + "|Execute complete.");
        }
        return null;
    }

}
