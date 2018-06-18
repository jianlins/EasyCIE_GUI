package edu.utah.bmi.simple.gui.task;

import edu.utah.bmi.nlp.core.GUITask;
import edu.utah.bmi.simple.gui.entry.TasksFX;
import javafx.application.Platform;

public class RefreshDebugPipe extends GUITask {
	protected GUITask guiTask;
	private static TasksFX tasks;

	public RefreshDebugPipe(TasksFX tasks) {
		this.tasks = tasks;
		guiTask = this;
	}

	@Override
	protected Object call() throws Exception {
		if (guiEnabled)
			FastDebugPipe.getInstance(tasks).refreshPipe();

		return null;
	}

	public static void showResults() {
		FastDebugPipe.getInstance(tasks).showResults();
	}
}
