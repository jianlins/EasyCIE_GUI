package edu.utah.bmi.simple.gui.entry;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Created by Jianlin_Shi on 10/26/15.
 */
public class TasksFX {
    protected final LinkedHashMap<String, TaskFX> obtasks = new LinkedHashMap<String, TaskFX>();

    public ObservableList<Map.Entry<String, TaskFX>> getTasksList() {
        return FXCollections.observableArrayList(obtasks.entrySet());
    }

    public void addTask(TaskFX task) {
        obtasks.put(task.getTaskName(), task);
    }

    public TaskFX getTask(String taskName) {
        if (obtasks.containsKey(taskName)) {
            return obtasks.get(taskName);
        } else {
            return null;
        }
    }
}
