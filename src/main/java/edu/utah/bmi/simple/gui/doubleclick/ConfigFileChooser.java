package edu.utah.bmi.simple.gui.doubleclick;

import edu.utah.bmi.simple.gui.controller.Main;
import edu.utah.bmi.simple.gui.controller.TasksOverviewController;
import edu.utah.bmi.simple.gui.entry.Setting;
import edu.utah.bmi.simple.gui.entry.TaskFX;
import javafx.application.Platform;
import javafx.stage.FileChooser;

import java.io.File;

/**
 * Created by
 *
 * @Author Jianlin Shi on 4/10/17.
 */
public class ConfigFileChooser extends javafx.concurrent.Task {
    private TaskFX currentTask;
    private Setting setting;

    public ConfigFileChooser(TaskFX currentTask, Setting setting) {
        this.currentTask = currentTask;
        this.setting = setting;
    }


    protected Object call() throws Exception {
        Platform.runLater(new Runnable() {
            public void run() {
                FileChooser fileChooser = new FileChooser();
                fileChooser.setTitle("Choose " + setting.getSettingName());
                File thisFile = new File(setting.getSettingValue());
                if (thisFile.exists()) {
                    File oldParentDir = new File(setting.getSettingValue()).getParentFile();
                    if (oldParentDir.exists())
                        fileChooser.setInitialDirectory(oldParentDir);
                } else {
                    fileChooser.setInitialDirectory(new File("./"));
                }
                File file = fileChooser.showOpenDialog(null);
                if (file != null) {
                    String newValue = Main.getRelativePath(file.getAbsolutePath());
                    Main.valueChanges.put("//" + currentTask.getTaskName() + "/" + setting.getSettingName(), newValue);
                    currentTask.setValue(setting.getSettingName(), newValue, setting.getSettingDesc(), setting.getDoubleClick(),setting.isOpenable());
                    TasksOverviewController.currentTasksOverviewController.getSettingTable().refresh();
                }
            }
        });
        return null;
    }
}
