package edu.utah.bmi.simple.gui.task;


import edu.utah.bmi.simple.gui.controller.TasksOverviewController;
import edu.utah.bmi.simple.gui.entry.TaskFX;
import edu.utah.bmi.simple.gui.entry.TasksFX;
import javafx.application.Platform;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;

/**
 * @author Jianlin Shi
 * Created on 2/13/17.
 */
public class ExecuteOsCommand extends javafx.concurrent.Task {
    protected String command;

    public ExecuteOsCommand(TasksFX tasks, String args) {
        initiate(tasks, args);
    }

    private void initiate(TasksFX tasks, String args) {
        command = args;
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
            String line = "";
            while ((line = b.readLine()) != null) {
                System.out.println(line);
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
        updateMessage("Execute Results:|Command \"" + command + "\":|" + stb.toString() + "|Execute complete.");
        return null;
    }

}
