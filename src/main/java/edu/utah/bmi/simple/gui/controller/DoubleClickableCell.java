package edu.utah.bmi.simple.gui.controller;

import edu.utah.bmi.simple.gui.entry.Setting;
import edu.utah.bmi.simple.gui.entry.TaskFX;
import javafx.collections.ObservableList;
import javafx.concurrent.Task;
import javafx.scene.control.TableCell;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;


/**
 * Make the table cell double clickable -- execute external class with parameters set in element value.
 *
 * @author Jianlin Shi
 *         Created on 2/13/17.
 */
public class DoubleClickableCell extends TableCell<ObservableList, Object> {
    protected final String functionalColor = "-fx-background-color: orange";
    protected final String unfuntionalColor = "";
    protected final String activeColor = "-fx-background-color: darkseagreen";
    private Thread thisThread;
    private TaskFX currentTask;


    public DoubleClickableCell() {
    }


    protected void updateItem(Object item, boolean empty) {
//        System.out.println(">>" + item + "<<");
        super.updateItem(item, empty);
        if (!empty) {
            Object[] paras = (Object[]) item;
            currentTask = (TaskFX) paras[0];
            addText((Setting) paras[1]);
        } else {
            setText("");
            setStyle(unfuntionalColor);
        }
    }

    private void addText(Setting setting) {
        String settingName = setting.getSettingName();
        String name = settingName;
        String[] names = settingName.split("/");
        int splitterCounts = names.length;
        if (splitterCounts > 0) {
            name = new String(new char[splitterCounts]).replace("\0", "   ") + names[splitterCounts - 1];
        }
        setText(name);

        String doubleClickString = setting.getDoubleClick();
        String para = setting.getSettingValue();
        Boolean functional = doubleClickString != null && doubleClickString.trim().length() > 0;
        if (functional) {
            setStyle(functionalColor);
            setOnMouseClicked(event -> {
                if (event.getClickCount() == 2) {
                    Thread th = new Thread(executeCommand(currentTask, setting));
                    th.start();
                }
            });
        } else {
            setStyle(unfuntionalColor);
            setOnMouseClicked(event -> {
                if (event.getClickCount() == 2) {
                    return;
                }
            });
        }
    }

    public Task executeCommand(TaskFX currentTask, Setting setting) {
        String taskClassName = setting.getDoubleClick().trim();
        String para = setting.getSettingValue();
        String settingName = setting.getSettingName();
        javafx.concurrent.Task thisTask = null;
        Class<? extends javafx.concurrent.Task> c = null;
        try {
            System.out.println(taskClassName);
            c = Class.forName(taskClassName).asSubclass(javafx.concurrent.Task.class);
            Constructor<? extends javafx.concurrent.Task> taskConstructor;
            if (para.length() > 0) {
                taskConstructor = c.getConstructor(TaskFX.class, Setting.class);
                thisTask = taskConstructor.newInstance(currentTask, setting);
            } else {
                taskConstructor = c.getConstructor();
                thisTask = taskConstructor.newInstance();
            }
        } catch (ClassNotFoundException e) {
            e.printStackTrace();
        } catch (NoSuchMethodException e) {
            e.printStackTrace();
        } catch (InstantiationException e) {
            e.printStackTrace();
        } catch (IllegalAccessException e) {
            e.printStackTrace();
        } catch (InvocationTargetException e) {
            e.printStackTrace();
        }
        return thisTask;
    }


}
