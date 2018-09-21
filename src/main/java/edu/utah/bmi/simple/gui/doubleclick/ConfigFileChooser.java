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
 * @author Jianlin Shi on 4/10/17.
 */
public class ConfigFileChooser extends javafx.concurrent.Task {
    private TaskFX currentTask;
    private Setting setting;

    public ConfigFileChooser(TaskFX currentTask, Setting setting) {
        this.currentTask = currentTask;
        this.setting = setting;
    }


    protected Object call() throws Exception {
        Platform.runLater(() -> {
            FileChooser fileChooser = new FileChooser();
            fileChooser.setTitle("Choose " + setting.getSettingName());
            File thisFile = new File(setting.getSettingValue());
            File dir = thisFile.getParentFile();
            if (dir != null)
                while (!dir.exists()) {
                    dir = dir.getParentFile();
                }
            else
                dir = new File("./");

            fileChooser.setInitialDirectory(dir);

            File file = fileChooser.showOpenDialog(null);
            if (file != null) {
                String newValue = Main.getRelativePath(file.getAbsolutePath());
                fileChooser.setInitialFileName(thisFile.getName());
                Main.valueChanges.put("//" + currentTask.getTaskName() + "/" + setting.getSettingName(), newValue);
                currentTask.setValue(setting.getSettingName(), newValue, setting.getSettingDesc(), setting.getDoubleClick(), setting.getOpenClick());
                TasksOverviewController.currentTasksOverviewController.getSettingTable().refresh();
            }
        });
        return null;
    }
}
