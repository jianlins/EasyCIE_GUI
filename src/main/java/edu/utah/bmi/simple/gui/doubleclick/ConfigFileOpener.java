package edu.utah.bmi.simple.gui.doubleclick;

import edu.utah.bmi.simple.gui.controller.TasksOverviewController;
import edu.utah.bmi.simple.gui.entry.Setting;
import edu.utah.bmi.simple.gui.entry.TaskFX;
import javafx.application.Platform;
import javafx.stage.FileChooser;

import java.awt.*;
import java.io.File;
import java.io.IOException;

/**
 * Created by
 *
 * @Author Jianlin Shi on 4/10/17.
 */
public class ConfigFileOpener extends javafx.concurrent.Task {
    private TaskFX currentTask;
    private Setting setting;

    public ConfigFileOpener(TaskFX currentTask, Setting setting) {
        this.currentTask = currentTask;
        this.setting = setting;
    }

    protected Object call() throws Exception {
        Platform.runLater(new Runnable() {
            public void run() {
//                File oldParentDir=new File(setting.getSettingValue()).getParentFile();
                File thisFile = new File(setting.getSettingValue());

                try {
                    Desktop.getDesktop().open(thisFile);
                } catch (IOException e) {
                    e.printStackTrace();
                }

            }
        });
        return null;
    }
}
