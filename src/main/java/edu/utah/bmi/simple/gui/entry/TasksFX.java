package edu.utah.bmi.simple.gui.entry;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Created by Jianlin_Shi on 10/26/15.
 */
public class TasksFX {
    protected final LinkedHashMap<String, TaskFX> obtasks = new LinkedHashMap<String, TaskFX>();
    protected HashMap<String, Integer> nameId = new HashMap<>();
    private int lastId = 0;

    public ObservableList<Map.Entry<String, TaskFX>> getTasksList() {
        return FXCollections.observableArrayList(obtasks.entrySet());
    }

    public void addTask(TaskFX task) {
        nameId.put(task.getTaskName(), lastId);
        obtasks.put(task.getTaskName(), task);
        lastId++;
    }

    public TaskFX getTask(String taskName) {
        if (obtasks.containsKey(taskName)) {
            return obtasks.get(taskName);
        } else {
            return null;
        }
    }

    public int getTaskId(String taskName) {
        return nameId.get(taskName);
    }
}
