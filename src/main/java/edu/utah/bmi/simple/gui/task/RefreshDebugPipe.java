package edu.utah.bmi.simple.gui.task;

import edu.utah.bmi.nlp.core.GUITask;
import edu.utah.bmi.simple.gui.entry.TasksFX;
import javafx.application.Platform;

public class RefreshDebugPipe extends GUITask {
    protected GUITask guiTask;
    private TasksFX tasks;

    public RefreshDebugPipe(TasksFX tasks) {
        this.tasks = tasks;
        guiTask = this;
    }

    @Override
    protected Object call() throws Exception {
        if (guiEnabled)
            FastDebugPipe.getInstance(tasks, guiTask).refreshPipe();
        return null;
    }
}
