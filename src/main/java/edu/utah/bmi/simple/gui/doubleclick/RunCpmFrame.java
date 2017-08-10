package edu.utah.bmi.simple.gui.doubleclick;

import edu.utah.bmi.simple.gui.entry.Setting;
import edu.utah.bmi.simple.gui.entry.TaskFX;
import javafx.application.Platform;
import org.apache.uima.tools.cpm.MyCpmFrame;

import javax.swing.*;

/**
 * @author Jianlin Shi
 * Created on 2/27/17.
 */
public class RunCpmFrame extends javafx.concurrent.Task {

    protected String cpeDescriptor;

    public RunCpmFrame(TaskFX currentTask, Setting setting) {
        this.cpeDescriptor = setting.getSettingValue();
    }

    @Override
    protected Object call() throws Exception {
        Platform.runLater(new Runnable() {
            public void run() {
                MyCpmFrame.main(new String[]{cpeDescriptor});
            }
        });
        updateMessage("Open UIMA CPE Configurator");
        updateProgress(1, 1);
        return null;
    }


}
