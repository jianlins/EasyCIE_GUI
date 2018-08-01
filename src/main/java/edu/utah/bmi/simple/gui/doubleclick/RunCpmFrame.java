package edu.utah.bmi.simple.gui.doubleclick;

import edu.utah.bmi.simple.gui.entry.Setting;
import edu.utah.bmi.simple.gui.entry.TaskFX;
import javafx.application.Platform;
import org.apache.uima.tools.cpm.AdaptableCpmFrame;
import org.apache.uima.tools.cpm.MyCpmFrame;

import javax.swing.*;
import java.awt.*;

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
    protected Object call() {
        SwingUtilities.invokeLater(() -> {
            AdaptableCpmFrame.main(new String[]{cpeDescriptor});
            Frame[] frames = AdaptableCpmFrame.getFrames();
            for (Frame frame : frames)
                frame.setSize(800, 600);
        });
        updateMessage("Open UIMA CPE Configurator");
        updateProgress(1, 1);
        return null;
    }


}
