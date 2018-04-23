package edu.utah.bmi.simple.gui.controller;

import com.sun.javafx.collections.ObservableMapWrapper;
import edu.utah.bmi.nlp.sql.ColumnInfo;
import edu.utah.bmi.nlp.sql.DAO;
import edu.utah.bmi.nlp.sql.RecordRow;
import edu.utah.bmi.nlp.sql.RecordRowIterator;
import edu.utah.bmi.simple.gui.entry.*;
import edu.utah.bmi.simple.gui.task.ConfigKeys;
import edu.utah.bmi.simple.gui.task.ViewOutputDB;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.concurrent.Task;
import javafx.event.EventHandler;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.*;
import javafx.scene.control.TextField;
import javafx.scene.control.cell.TextFieldOpenableTableCell;
import javafx.util.converter.SettingValueConverter;
import javafx.scene.control.cell.TextFieldTableCell;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.FlowPane;
import javafx.scene.web.WebEngine;
import javafx.scene.web.WebView;
import javafx.util.Callback;

import java.awt.*;
import java.io.File;
import java.io.IOException;
import java.lang.reflect.Array;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.Iterator;
import java.util.Map;

/**
 * Created by Jianlin on 5/19/15.
 */
public class TasksOverviewController {

    public static TasksOverviewController currentTasksOverviewController;

    @FXML
    private TableView<Map.Entry<String, TaskFX>> tasklist;

    @FXML
    private TableColumn<Map.Entry<String, TaskFX>, String> taskNameColumn;


    @FXML
    private BorderPane annoDetails;


    private BottomViewController bottomViewController;


    @FXML
    private SplitPane dbPanel;

    @FXML
    private TableView annoTableView;


    @FXML
    private TableView featureTable;
    @FXML
    private TableColumn<String[], String> featureNameColumn, featureValueColumn;

    @FXML
    private WebView htmlViewer;


    @FXML
    private Button tableRefresh;

    @FXML
    public TextField sqlFilter;


    private TaskFX currentTask;

    private String currentTableName = "", currentDBName = "", currentFilter = "";

    private boolean enableRefresh = true;


    private Main mainApp;


    @FXML
    private TableColumn settingNameColumn;

    @FXML
    private FlowPane executePanel;

    @FXML
    private TableColumn<Map.Entry<String, Setting>, Object> settingValueColumn;

    @FXML
    private TableColumn<Map.Entry<String, Setting>, String> settingDesColumn;


    @FXML
    private TableView<Map.Entry<String, SettingAb>> settingTable;

    public boolean doctable = true;

    private WebEngine webEngine;

    private int snippetPos = 1, limitRecords = 300;

    public TasksOverviewController() {
    }

    /**
     * Initializes the controller class. This method is automatically called
     * after the fxml file has been loaded.
     */
    @FXML
    private void initialize(TasksFX tasks) {

        dbPanel.setVisible(false);
        mainApp.tasks = tasks;
        limitRecords = Integer.parseInt(tasks.getTask("settings").getValue("viewer/limit_records").trim());
        readViewerSettings();
        // Initialize the tasks table with the one column.
        taskNameColumn.setCellValueFactory(p -> {
            // this callback returns property for just one cell, you can't use a loop here
            // for first column we use key
            return new SimpleStringProperty(p.getValue().getValue().getTaskName());
        });


        featureNameColumn.setCellFactory(TextFieldTableCell.forTableColumn());
        featureNameColumn.setCellValueFactory(p -> {
            return new SimpleStringProperty(p.getValue()[0]);
        });
        featureValueColumn.setCellFactory(TextFieldTableCell.forTableColumn());
        featureValueColumn.setCellValueFactory(p -> {
            String[] value = p.getValue();
            if (value != null && value.length > 1)
                return new SimpleStringProperty(p.getValue()[1]);
            else
                return new SimpleStringProperty("");
        });


        tasklist.setItems(mainApp.tasks.getTasksList());

        tasklist.setRowFactory(tv -> {
            TableRow<Map.Entry<String, TaskFX>> row = new TableRow<>();
            row.setOnMouseClicked(new EventHandler<MouseEvent>() {
                @Override
                public void handle(MouseEvent event) {
                    Map.Entry<String, TaskFX> focusedRow = row.getItem();
                    if (focusedRow != null)
                        showTask(focusedRow.getValue());
                }
            });
            return row;
        });

        if (mainApp.tasks.getTasksList().size() > 0) {
            showTask(mainApp.getCurrentTask());
            tasklist.requestFocus();
            tasklist.getSelectionModel().select(mainApp.getCurrentTaskId());
            tasklist.getFocusModel().focus(mainApp.getCurrentTaskId());
        }
        currentTasksOverviewController = this;


//      Enable autoresize of htmleditor
//        GridPane gridPane = (GridPane) htmlViewer.getChildrenUnmodifiable().get(0);
        webEngine = htmlViewer.getEngine();

//        RowConstraints row1 = new RowConstraints();
//        row1.setVgrow(Priority.NEVER);
//        RowConstraints row2 = new RowConstraints();
//        row2.setVgrow(Priority.NEVER);
//        RowConstraints row3 = new RowConstraints();
//        row3.setVgrow(Priority.ALWAYS);
//        gridPane.getRowConstraints().addAll(row1, row2, row3);


        tableRefresh.onMouseClickedProperty().set(new EventHandler<MouseEvent>() {
            @Override
            public void handle(MouseEvent event) {
                enableRefresh = true;
                String condition = sqlFilter.getText().trim();
                if (doctable)
                    showDocTable(currentDBName, currentTableName, condition, ColorAnnotationCell.colorDifferential);
                else
                    new ViewOutputDB(mainApp.tasks).run();
            }
        });

        sqlFilter.setOnKeyPressed(new EventHandler<KeyEvent>() {
            @Override
            public void handle(KeyEvent ke) {
                if (ke.getCode().equals(KeyCode.ENTER)) {
                    enableRefresh = true;
                    String condition = sqlFilter.getText().trim();
                    if (condition.length() > 0) {
                        if (!condition.startsWith("WHERE") || !condition.startsWith("where")) {
                            condition = " WHERE " + condition;
                        }
                    }
                    if (doctable)
                        showDocTable(currentDBName, currentTableName, condition, ColorAnnotationCell.colorDifferential);
                    else
                        new ViewOutputDB(mainApp.tasks).run();
//                        showAnnoTable(currentDBName, currentTableName, condition, ColorAnnotationCell.colorDifferential);
                }
            }
        });


        Callback<TableColumn, TableCell> doubleClickableCellFactory =
                new Callback<TableColumn, TableCell>() {
                    @Override
                    public TableCell call(TableColumn p) {
                        return new DoubleClickableCell();
                    }
                };

        settingNameColumn.setCellFactory(doubleClickableCellFactory);


        settingNameColumn.setCellValueFactory((Callback<TableColumn.CellDataFeatures<ObservableMapWrapper.Entry, Object>, ObservableValue<Object>>)
                param -> new SimpleObjectProperty<>(new Object[]{currentTask, param.getValue().getValue()}));


//        TableColumn<Map.Entry<String, Setting>, String>
        settingValueColumn.setCellValueFactory(p -> new SimpleObjectProperty<>(new Object[]{currentTask, p.getValue().getValue()}));


        Callback<TableColumn<Map.Entry<String, Setting>, Object>, TableCell<Map.Entry<String, Setting>, Object>> openClickableCellFactory =
                p -> new TextFieldOpenableTableCell(new SettingValueConverter());


        settingValueColumn.setCellFactory(openClickableCellFactory);

//        settingValueColumn.setOnEditStart(event -> {
//            Map.Entry<String, Setting> entry = event.getRowValue();
//            Setting setting = entry.getValue();
//            String openApp = setting.getOpenClick();
//            if (openApp.length() > 0) {
//                File file = new File(setting.getSettingValue());
//                javafx.concurrent.Task thisTask = null;
//                Class<? extends javafx.concurrent.Task> c = null;
//                try {
//                    System.out.println(openApp);
//                    c = Class.forName(openApp).asSubclass(javafx.concurrent.Task.class);
//                    Constructor<? extends javafx.concurrent.Task> taskConstructor;
//                    if (file.exists()) {
//                        taskConstructor = c.getConstructor(TaskFX.class, Setting.class);
//                        thisTask = taskConstructor.newInstance(currentTask, setting);
//                        thisTask.run();
//                    }else{
//                        bottomViewController.setMsg("File \"" + file + "\" doesn't exist.");
//                    }
//                } catch (ClassNotFoundException e) {
//                    e.printStackTrace();
//                } catch (NoSuchMethodException e) {
//                    e.printStackTrace();
//                } catch (InstantiationException e) {
//                    e.printStackTrace();
//                } catch (IllegalAccessException e) {
//                    e.printStackTrace();
//                } catch (InvocationTargetException e) {
//                    e.printStackTrace();
//                }
//            }
//        });

        settingValueColumn.setOnEditCommit(event -> {
            String newValue;
            Object value = event.getNewValue();
            if (value instanceof String) {
                newValue = event.getNewValue().toString();
            } else if (value instanceof Object[]) {
                newValue = ((Setting) ((Object[]) value)[1]).getSettingValue();
            } else {
                newValue = "";
            }
            Map.Entry<String, Setting> entry = event.getRowValue();
            Setting setting = entry.getValue();
            String openApp = setting.getOpenClick();
            currentTask.setValue(setting.getSettingName(), newValue, setting.getSettingDesc(), setting.getDoubleClick(), openApp);
            Main.valueChanges.put("//" + currentTask.getTaskName() + "/" + setting.getSettingName(), newValue);
            mainApp.tasks.addTask(currentTask);
//
        });


        settingDesColumn.setCellValueFactory(p -> {
            return p.getValue().getValue().settingDescProperty();
        });


        settingDesColumn.setCellFactory(TextFieldTableCell.forTableColumn());
        settingDesColumn.setOnEditCommit(event -> {
            String newDesc = event.getNewValue();
            Map.Entry<String, Setting> entry = event.getRowValue();
            Setting setting = entry.getValue();
            currentTask.setValue(setting.getSettingName(), setting.getSettingValue(), newDesc, setting.getDoubleClick());
            Main.memochanges.put("//" + currentTask.getTaskName() + "/" + setting.getSettingName() + "/@memo", newDesc);
            mainApp.tasks.addTask(currentTask);
        });
    }


    private void readViewerSettings() {
        StaticVariables.preTag = mainApp.tasks.getTask("settings").getValue("viewer/preTag");
        StaticVariables.postTag = mainApp.tasks.getTask("settings").getValue("viewer/postTag");
        StaticVariables.htmlMarker0 = mainApp.tasks.getTask("settings").getValue("viewer/highlighter_begin");
        StaticVariables.htmlMarker1 = mainApp.tasks.getTask("settings").getValue("viewer/highlighter_end");
        StaticVariables.preTagLength = StaticVariables.preTag.length();
        StaticVariables.postTagLength = StaticVariables.postTag.length();
        StaticVariables.snippetLength = Integer.parseInt(mainApp.tasks.getTask("settings").getValue("viewer/snippet_length"));
        StaticVariables.colorPool.clear();
        StaticVariables.randomPick = mainApp.tasks.getTask("settings").getValue("viewer/random_pick_color").toLowerCase().startsWith("t") ? true : false;
        int i = 0;
        for (String color : mainApp.tasks.getTask("settings").getValue("viewer/color_pool").split("\\|")) {
            StaticVariables.colorPool.put(i, color.trim());
            i++;
        }
        StaticVariables.resetColorPool();
        currentTableName = "";
    }


    public boolean showAnnoTable(String dbName, String tableName, String filter, String colorDifferential) {
        if (doctable)
            annoTableView.getColumns().clear();
        doctable = false;
        return showDBTable(dbName, "queryAnnos", tableName, filter, colorDifferential);
    }


    public boolean showDocTable(String dbName, String tableName, String filter, String colorDifferential) {
        if (!doctable)
            annoTableView.getColumns().clear();
        doctable = true;
        return showDBTable(dbName, "queryDocs", tableName, filter, colorDifferential);
    }

    public boolean showDBTable(RecordRowIterator rs, String colorDifferential) {
        ColumnInfo columanInfo = rs.getColumninfo();
        return showDBTable(rs, columanInfo, colorDifferential, doctable);
    }


    public boolean showDBTable(Iterator rs, ColumnInfo columanInfo, String colorDifferential, boolean doctable) {
        dbPanel.setVisible(true);
        annoTableView.setVisible(true);
        this.doctable = doctable;
        ObservableList<ObservableList> data = FXCollections.observableArrayList();
        ColorAnnotationCell.colorDifferential = colorDifferential;
        int numOfColumns = columanInfo.getColumnInfo().size();
        if (columanInfo.getColumnInfo().containsKey("BEGIN"))
            numOfColumns -= 2;
        Callback<TableColumn, TableCell> colorCellFactory =
                new Callback<TableColumn, TableCell>() {
                    @Override
                    public TableCell call(TableColumn p) {
                        return new ColorAnnotationCell();
                    }
                };
//        System.out.println("annoTableView.getColumns().size=" + annoTableView.getColumns().size());
//        System.out.println("columanInfo size=" + columanInfo.getColumnInfo().size());
        if (annoTableView.getColumns().size() == 0 || annoTableView.getColumns().size() != columanInfo.getColumnInfo().size() - 4) {
            int i = 0;
            annoTableView.getColumns().clear();
            for (String columnName : columanInfo.getColumnInfo().keySet()) {
                //use non property style for making dynamic table
                final int j = i;
                TableColumn col = new TableColumn(columnName);
                String widthStr;
                switch (columnName.toUpperCase()) {
                    case "BEGIN":
                    case "END":
                    case "TEXT":
                    case "FEATURES":
                    case "SNIPPET_BEGIN":
                        continue;
                    case "SNIPPET":
                        snippetPos = i;
                        col.setMaxWidth(dbPanel.getWidth() * 0.7);
                        widthStr = mainApp.tasks.getTask("settings").getValue("viewer/width/" + columnName).trim();
                        if (widthStr.length() > 0)
                            col.setPrefWidth(Integer.parseInt(widthStr));
                        else
                            col.setPrefWidth(StaticVariables.snippetLength * 5);
                        col.setCellFactory(colorCellFactory);
                        col.setCellValueFactory(new Callback<TableColumn.CellDataFeatures<ObservableList, Object>, ObservableValue<Object>>() {
                            public ObservableValue<Object> call(TableColumn.CellDataFeatures<ObservableList, Object> param) {
                                Object record = param.getValue().get(j);
                                if (record != null)
                                    return new SimpleObjectProperty<>(record);
                                else
                                    return new SimpleObjectProperty<>("");
                            }
                        });
                        break;
                    default:

                    case "TYPE":
                        widthStr = mainApp.tasks.getTask("settings").getValue("viewer/width/" + columnName).trim();
                        if (widthStr.length() > 0)
                            col.setPrefWidth(Integer.parseInt(widthStr));
                        col.setCellValueFactory(new Callback<TableColumn.CellDataFeatures<ObservableList, Object>, ObservableValue<Object>>() {
                            public ObservableValue<Object> call(TableColumn.CellDataFeatures<ObservableList, Object> param) {
                                Object record = param.getValue().get(j);
                                if (record != null)
                                    return new SimpleObjectProperty<>(record);
                                else
                                    return new SimpleObjectProperty<>("");
                            }
                        });
                        if (col.getPrefWidth() == 80)
                            col.setPrefWidth(90);
                }
                annoTableView.getColumns().addAll(col);
                i++;
            }
            annoTableView.setRowFactory(tv -> {
                TableRow<ObservableList> row = new TableRow<>();
                row.focusedProperty().addListener(new ChangeListener<Boolean>() {
                    @Override
                    public void changed(ObservableValue<? extends Boolean> observable, Boolean oldValue, Boolean newValue) {
                        if (newValue && !row.isEmpty()) {
                            ObservableList clickedRow = row.getItem();
                            if (clickedRow.get(snippetPos) instanceof RecordRow)
                                updateHTMLEditor((RecordRow) clickedRow.get(snippetPos));
                        }
                    }
                });
                return row;
            });
        }
        dbPanel.widthProperty().addListener(new ChangeListener<Number>() {
            @Override
            public void changed(ObservableValue<? extends Number> observable, Number oldValue, Number newValue) {
                if (annoTableView.getColumns() != null && annoTableView.getColumns().size() > 1) {
                    TableColumn col = (TableColumn) annoTableView.getColumns().get(snippetPos);
//                    System.out.println("reset the width of column " + snippetPos);
                    String widthStr = mainApp.tasks.getTask("settings").getValue("viewer/width/" + col.getText()).trim();
                    if (widthStr.length() > 0)
                        col.setPrefWidth(Integer.parseInt(widthStr));
                    else {
                        col.setMaxWidth((int) newValue.doubleValue() * 0.9);
                        col.setPrefWidth((int) newValue.doubleValue() * 0.38);
                    }
                    htmlViewer.setPrefWidth(newValue.doubleValue() * 0.11);
                }
            }
        });
        boolean haveRead = false;
        while (rs != null && rs.hasNext()) {
            //Iterate Row
            ObservableList<Object> row = FXCollections.observableArrayList();
            RecordRow record = (RecordRow) rs.next();
            for (String columnName : columanInfo.getColumnInfo().keySet()) {
                TableColumn col = new TableColumn(columnName);
                if (record.getValueByColumnName(columnName) == null) {
                    record.addCell(columnName, "");
                }
                switch (columnName.toUpperCase()) {
                    case "BEGIN":
                    case "END":
                    case "TEXT":
                    case "FEATURES":
                    case "SNIPPET_BEGIN":
                        continue;
                    case "SNIPPET":
                        row.add(record);
                        break;
                    default:
                        Object value = record.getValueByColumnName(columnName);
                        if (value == null)
                            row.add("");
                        else
                            row.add(record.getValueByColumnName(columnName));
                        break;
                }
            }
            data.add(row);
            haveRead = true;
        }
        annoTableView.setItems(data);
        annoTableView.refresh();
        return haveRead;
    }

    public boolean showDBTable(String dbName, String queryName,
                               String tableName, String filter, String colorDifferential) {
//        settingPanel.settingPanel.setVisible(false);

        if (!currentTableName.equals(tableName) || !currentDBName.equals(dbName) || !currentFilter.equals(filter) || enableRefresh) {
            currentTableName = tableName;
            currentDBName = dbName;
            currentFilter = filter;
            enableRefresh = false;
        } else {
            dbPanel.setVisible(true);
            annoTableView.setVisible(true);
            return true;
        }


        DAO dao = new DAO(new File(dbName));
        String condition = filter;
        String lowerCasedCondition = condition.toLowerCase();
        if (filter.length() > 7) {
            if (lowerCasedCondition.indexOf("where") != -1) {
                filter = filter.substring(lowerCasedCondition.indexOf("where") + 5);
                condition = filter;
            }
        }
        String limitSyntacs = dao.configReader.getValue("syntax/limit/sql").toString();
        String limitSyntacsLowerCase = limitSyntacs.toLowerCase();
        if (!lowerCasedCondition.trim().startsWith(limitSyntacsLowerCase + " ") &&
                lowerCasedCondition.indexOf(" " + limitSyntacsLowerCase + " ") == -1) {
            if (lowerCasedCondition.endsWith(";")) {
                condition = condition.substring(0, condition.length() - 1) + " " + limitSyntacs + " " + limitRecords + ";";
                filter = filter.substring(0, filter.length() - 1) + " " + limitSyntacs + " " + limitRecords + ";";
            } else {
                condition = condition + " " + limitSyntacs + " " + limitRecords + "";
                filter = filter + " " + limitSyntacs + " " + limitRecords + "";
            }
        }
        if (!filter.trim().toLowerCase().startsWith(limitSyntacsLowerCase)) {
            filter = " where " + filter;
        }

        sqlFilter.setText(condition);

        RecordRowIterator rs = queryRecords(dao, queryName, tableName, filter);


        boolean haveRead = showDBTable(rs, colorDifferential);
        dao.close();
        return haveRead;
    }


    public RecordRowIterator queryRecords(DAO dao, String queryName, String tableName, String condition) {
        if (!dao.checkTableExits(tableName)) {
            System.out.println(tableName + " doesn't exist");
            return null;
        }

        StringBuilder sql = new StringBuilder();
        sql.append(dao.queries.get(queryName).replaceAll("\\{tableName}", tableName));
        if (condition != null && condition.length() > 0) {
            sql.append(" ");
            sql.append(condition);
        }
        if (sql.charAt(sql.length() - 1) != ';')
            sql.append(";");

        RecordRowIterator recordIterator = dao.queryRecords(sql.toString());
        return recordIterator;
    }


    private void updateHTMLEditor(RecordRow record) {
        String text;
        if (doctable)
            text = record.getStrByColumnName("TEXT");
        else
            text = record.getStrByColumnName("SNIPPET");
        String color = ColorAnnotationCell.pickColor(record, ColorAnnotationCell.colorDifferential);
        if (text == null || text.length() == 0) {
            text = record.getStrByColumnName("TEXT");
        } else if (!doctable) {
            text = ColorAnnotationCell.generateHTML(text,
                    (int) record.getValueByColumnName("BEGIN"),
                    (int) record.getValueByColumnName("END"),
                    color);
        }
        text = text.replaceAll("\\n", "<br>");
//        htmlEditor.setHtmlText(text);
        webEngine.loadContent(text);
        if (record.getStrByColumnName("FEATURES") != null && record.getStrByColumnName("FEATURES").length() > 0) {
            annoDetails.setBottom(featureTable);
            updateFeatureTable(record.getStrByColumnName("FEATURES"));
        } else {
            annoDetails.setBottom(null);
        }
    }

    private void updateFeatureTable(String features) {
        if (features.startsWith("Note:") && features.substring(5, features.indexOf("\n")).indexOf(":") > 0) {
            features = "\t" + features.substring(5);
        }
        ObservableList<String[]> data = FXCollections.observableArrayList();
        for (String featureNameValue : features.split("\\n")) {
            ObservableList<Object> row = FXCollections.observableArrayList();
            data.add(featureNameValue.split(":"));
        }
        featureTable.setItems(data);
        featureTable.refresh();
    }


    /**
     * Is called by the main application to give a reference back to itself.
     *
     * @param mainApp
     */
    public void setMainApp(Main mainApp) {
        this.mainApp = mainApp;
        // Add observable list data to the table
        initialize(mainApp.getTasks());
        this.bottomViewController = mainApp.bottomViewController;
    }

    public void showTask(TaskFX currentTask) {
        dbPanel.setVisible(false);
        this.currentTask = currentTask;
        mainApp.setCurrentTaskName(currentTask.getTaskName());
//        settingPanel.settingPanel.setVisible(true);
        settingTable.setItems(currentTask.getSettings());
//        settingtable.setRowFactory(tv -> {
//            TableRow<Map.Entry<String, SettingAb>> row = new TableRow<>();
//            row.setOnMouseClicked(event -> {
//                if (event.getClickCount() == 2 && (!row.isEmpty())) {
//                    String command = row.getItem().getValue().getDoubleClick();
//                    Thread thisThread = new Thread(executeCommand(command));
//                    if (thisThread.isAlive())
//                        thisThread.interrupt();
//                    else
//                        thisThread.start();
//                }
//            });
//            return row;
//        });
        ObservableList<Map.Entry<String, SettingAb>> executes = currentTask.getExecutes();
//        System.out.println(executes.size());
        executePanel.getChildren().removeAll(executePanel.getChildren());
        for (Map.Entry<String, SettingAb> exe : executes) {
            String label = exe.getKey();
            int splitter = label.indexOf("/");
            SettingAb setting = exe.getValue();
            label = label.substring(splitter + 1);
            Button button = new Button(label);
            if (setting.getSettingDesc().length() > 0)
                button.setTooltip(new Tooltip(setting.getSettingDesc()));
            button.setOnAction(event -> {
                javafx.concurrent.Task thisTask = getTaskFromString(setting.getSettingValue().trim());
                mainApp.bottomViewController.progressBar.progressProperty().bind(thisTask.progressProperty());
                mainApp.bottomViewController.msg.textProperty().bind(thisTask.messageProperty());
                Thread thisThread = new Thread(thisTask);
                thisThread.start();
                mainApp.bottomViewController.cancelButton.setOnAction((cancel) -> {
                    thisThread.interrupt();
//                        bottomViewController.progressBar.progressProperty().unbind();
//                        bottomViewController.msg.setText("Task cancelled.");
                });
            });
            executePanel.getChildren().add(button);
        }

    }

    public javafx.concurrent.Task getTaskFromString(String taskString) {
        String para = "", taskClassName = "";
        int splitter = taskString.indexOf(" ");
        if (splitter != -1) {
            taskClassName = taskString.substring(0, splitter);
            para = taskString.substring(splitter + 1).trim();
        } else {
            taskClassName = taskString;
        }
        javafx.concurrent.Task thisTask = null;
        Class<? extends javafx.concurrent.Task> c = null;
        try {
            c = Class.forName(taskClassName).asSubclass(javafx.concurrent.Task.class);
            Constructor<? extends Task> taskConstructor;

            if (para.length() > 0) {
                taskConstructor = c.getConstructor(TasksFX.class, String.class);
                thisTask = taskConstructor.newInstance(mainApp.tasks, para);
            } else {
                taskConstructor = c.getConstructor(TasksFX.class);
                thisTask = taskConstructor.newInstance(mainApp.tasks);
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

    public TableView<Map.Entry<String, SettingAb>> getSettingTable() {
        return settingTable;
    }
}
