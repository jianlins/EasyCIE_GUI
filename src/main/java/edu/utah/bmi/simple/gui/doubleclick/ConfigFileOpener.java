package edu.utah.bmi.simple.gui.doubleclick;

import edu.utah.bmi.nlp.core.GUITask;
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
public class ConfigFileOpener extends GUITask {
    private TaskFX currentTask;
    private Setting setting;

    public ConfigFileOpener(TaskFX currentTask, Setting setting) {
        this.currentTask = currentTask;
        this.setting = setting;
    }

    protected Object call() throws Exception {
        File thisFile = new File(setting.getSettingValue());
        Thread th = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    Desktop.getDesktop().open(thisFile);
                } catch (IOException e) {
                    e.printStackTrace();
                }

            }
        });
        th.start();
//        Platform.runLater(new Runnable() {
//            public void run() {
////                File oldParentDir=new File(setting.getSettingValue()).getParentFile();
//                File thisFile = new File(setting.getSettingValue());
//                if (thisFile.exists())
//                    try {
//                        Desktop.getDesktop().open(thisFile);
//                    } catch (IOException e) {
//                        e.printStackTrace();
//                    }
//                else
//                    popDialog("Note","The file '"+setting.getSettingValue()+"' doesn't exist.",
//                            "Please choose the correct file.");
//
//            }
//        });
        return null;
    }
}
