package edu.utah.bmi.simple.gui.doubleclick;

import edu.utah.bmi.simple.gui.controller.Main;
import edu.utah.bmi.simple.gui.controller.TasksOverviewController;
import edu.utah.bmi.simple.gui.entry.Setting;
import edu.utah.bmi.simple.gui.entry.TaskFX;
import javafx.application.Platform;
import javafx.stage.DirectoryChooser;

import java.io.File;

/**
 * Created by
 *
 * @Author Jianlin Shi on 4/10/17.
 */
public class ConfigDirectoryChooser extends javafx.concurrent.Task {
    private TaskFX currentTask;
    private Setting setting;

    public ConfigDirectoryChooser(TaskFX currentTask, Setting setting) {
        this.currentTask = currentTask;
        this.setting = setting;
    }

    protected Object call() throws Exception {
        Platform.runLater(new Runnable() {
            public void run() {
                DirectoryChooser dirChooser = new DirectoryChooser();
                dirChooser.setTitle("Choose " + setting.getSettingName());
                File oldParentDir = new File(setting.getSettingValue()).getParentFile();
                if (oldParentDir.exists())
                    dirChooser.setInitialDirectory(oldParentDir);
                File file = dirChooser.showDialog(null);
                if (file != null) {
                    String newValue = Main.getRelativePath(file.getAbsolutePath());
                    Main.valueChanges.put("//" + currentTask.getTaskName() + "/" + setting.getSettingName(), newValue);
                    currentTask.setValue(setting.getSettingName(), newValue, setting.getSettingDesc(), setting.getDoubleClick());
                    TasksOverviewController.currentTasksOverviewController.getSettingTable().refresh();
                }
            }
        });
        return null;
    }
}
