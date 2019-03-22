package edu.utah.bmi.simple.gui.controller;

import com.sun.javafx.collections.ObservableMapWrapper;
import edu.emory.mathcs.backport.java.util.Arrays;
import edu.emory.mathcs.backport.java.util.Collections;
import edu.utah.bmi.nlp.core.GUITask;
import edu.utah.bmi.nlp.sql.ColumnInfo;
import edu.utah.bmi.nlp.sql.EDAO;
import edu.utah.bmi.nlp.sql.RecordRow;
import edu.utah.bmi.nlp.sql.RecordRowIterator;
import edu.utah.bmi.simple.gui.core.AnnotationLogger;
import edu.utah.bmi.simple.gui.entry.*;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.concurrents.Task;
import javafx.event.EventHandler;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.control.cell.TextFieldOpenableTableCell;
import javafx.scene.control.cell.TextFieldTableCell;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.AnchorPane;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.FlowPane;
import javafx.scene.web.WebEngine;
import javafx.scene.web.WebView;
import javafx.util.Callback;
import javafx.util.converter.SettingValueConverter;
import org.w3c.dom.Document;

import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import java.io.File;
import java.io.StringWriter;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.*;

import static edu.utah.bmi.simple.gui.controller.CellFactories.*;

/**
 * Created by Jianlin on 5/19/15.
 */
public class TasksOverviewController {
    protected static final ColumnInfo columnInfo = new ColumnInfo();
    public static final String DocView = "DocView", AnnoView = "AnnoView", DebugView = "DebugView", CompareView = "CompareView";

    public static final HashMap<String, Integer> tableMemoRowId = new HashMap<>();

    public static String currentViewName = DocView;

    public static TasksOverviewController currentTasksOverviewController;

    @FXML
    private TableView<Map.Entry<String, TaskFX>> tasklist;

    @FXML
    private TableColumn<Map.Entry<String, TaskFX>, String> taskNameColumn;


    @FXML
    private BorderPane annoDetails;


    private BottomViewController bottomViewController;

    @FXML
    private AnchorPane contentPanel;


    @FXML
    private SplitPane dbPanel;

    @FXML
    private TableView docTableView, compareTableView, debugTableView;

    @FXML
    public TableView annoTableView;

    @FXML
    public TabPane tabPane;


    @FXML
    private TableView featureTable;
    @FXML
    private TableColumn<String[], String> featureNameColumn, featureValueColumn;

    @FXML
    private WebView htmlViewer;


    @FXML
    public Button annoTableRefresh, docTableRefresh, compareTableRefresh;

    @FXML
    public TextField annoSqlFilter, docSqlFilter, compareSqlFilter;


    private TaskFX currentTask;

    private String currentTableName = "", currentDBName = "", currentFilter = "";


    public Main mainApp;


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


    private WebEngine webEngine;

    private int limitRecords = 300;
    private EDAO dao;

    public GUITask currentGUITask;

    private HashMap<Object, Integer> tabLocator = new HashMap<>();
    //    save the current sqls displayed in Tabviews
    private HashMap<String, String> currentSQLs = new HashMap<>();
    //    save the current db used displayed in Tabviews
    public HashMap<String, String> currentDBFileName = new HashMap<>();

    public HashMap<Integer, HashMap<String, Integer>> docIdRowPosMap = new HashMap<>();
    private boolean showing = false;

    private Class debugCls;

    public TasksOverviewController() {
    }

    /**
     * Initializes the controller class. This method is automatically called
     * after the fxml file has been loaded.
     */
    @FXML
    private void initialize(TasksFX tasks) {
        columnInfo.addColumnInfo("ID", "string");
        columnInfo.addColumnInfo("TYPE", "string");
        columnInfo.addColumnInfo("BEGIN", "int");
        columnInfo.addColumnInfo("END", "int");

        columnInfo.addColumnInfo("FEATURES", "string");
        columnInfo.addColumnInfo("SNIPPET", "string");

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
        webEngine.setJavaScriptEnabled(true);
        htmlViewer.setContextMenuEnabled(false);
        createContextMenu(htmlViewer);


//        RowConstraints row1 = new RowConstraints();
//        row1.setVgrow(Priority.NEVER);
//        RowConstraints row2 = new RowConstraints();
//        row2.setVgrow(Priority.NEVER);
//        RowConstraints row3 = new RowConstraints();
//        row3.setVgrow(Priority.ALWAYS);
//        gridPane.getRowConstraints().addAll(row1, row2, row3);


        docTableRefresh.onMouseClickedProperty().set(new EventHandler<MouseEvent>() {
            @Override
            public void handle(MouseEvent event) {
                refreshTableView(DocView, docSqlFilter);
            }
        });

        docSqlFilter.setOnKeyPressed(new EventHandler<KeyEvent>() {
            @Override
            public void handle(KeyEvent ke) {
                if (ke.getCode().equals(KeyCode.ENTER)) {
                    refreshTableView(DocView, docSqlFilter);
                }
            }
        });
        annoTableRefresh.onMouseClickedProperty().set(new EventHandler<MouseEvent>() {
            @Override
            public void handle(MouseEvent event) {
                refreshTableView(AnnoView, annoSqlFilter);
            }
        });

        annoSqlFilter.setOnKeyPressed(new EventHandler<KeyEvent>() {
            @Override
            public void handle(KeyEvent ke) {
                if (ke.getCode().equals(KeyCode.ENTER)) {
                    refreshTableView(AnnoView, annoSqlFilter);
                }
            }
        });
        compareTableRefresh.onMouseClickedProperty().set(new EventHandler<MouseEvent>() {
            @Override
            public void handle(MouseEvent event) {
                refreshTableView(CompareView, compareSqlFilter);
            }
        });

        compareSqlFilter.setOnKeyPressed(new EventHandler<KeyEvent>() {
            @Override
            public void handle(KeyEvent ke) {
                if (ke.getCode().equals(KeyCode.ENTER)) {
                    refreshTableView(CompareView, compareSqlFilter);
                }
            }
        });

        tabPane.getSelectionModel().selectedItemProperty().addListener((ov, oldTab, newTab) -> {
            String tabName = newTab.getText();
            switch (tabName) {
                case DocView:
                    if (currentSQLs.containsKey(DocView)) {
                        refreshTableView(DocView, docSqlFilter);
                    } else {
                        executeTaskClass("edu.utah.bmi.simple.gui.task.ViewImportDB");
                    }
                    break;
                case AnnoView:
                    if (currentSQLs.containsKey(AnnoView)) {
                        refreshTableView(AnnoView, annoSqlFilter);
                    } else {
                        executeTaskClass("edu.utah.bmi.simple.gui.task.ViewOutputDB");
                    }
                    break;
                case CompareView:
                    if (currentSQLs.containsKey(CompareView)) {
                        refreshTableView(CompareView, compareSqlFilter);
                    } else {
                        executeTaskClass("edu.utah.bmi.simple.gui.task.ViewDiffDB");
                    }
                    break;
                case DebugView:
                    refreshDebugView();
                    break;

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
                p -> {
                    TextFieldOpenableTableCell cell = new TextFieldOpenableTableCell(new SettingValueConverter());
                    cell.setOnMouseClicked(ke -> {
                        if (ke.getButton().equals(MouseButton.SECONDARY))
                            cell.startClear();

                    });
                    return cell;
                };


        settingValueColumn.setCellFactory(openClickableCellFactory);

//        settingValueColumn.setOnEditStart(event -> {
//            Map.Entry<String, Setting> entry = event.getRowValue();
//            Setting setting = entry.getValue();
//            String openApp = setting.getOpenClick();
//            if (openApp.length() > 0) {
//                File file = new File(setting.getSettingValue());
//                Task thisTask = null;
//                Class<? extends Task> c = null;
//                try {
//                    System.out.println(openApp);
//                    c = Class.forName(openApp).asSubclass(Task.class);
//                    Constructor<? extends Task> taskConstructor;
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
            Setting setting = (Setting) ((Object[]) value)[1];
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


    public void refreshTableView(String viewName, TextField sqlFilter) {
        if (!showing) {
            String condition = sqlFilter.getText().trim();
            String conditionLower = condition.toLowerCase();
            if (condition.length() > 0) {
                if (!conditionLower.startsWith("where") && !conditionLower.startsWith("limit")) {
                    condition = " WHERE " + condition;
                }
            }
            String sql = currentSQLs.get(viewName) + condition;
            showDBTable(sql, currentDBFileName.get(viewName), ColorAnnotationCell.colorDifferential, viewName);
        }
    }

    public void refreshDebugView() {

        showDBTable(AnnotationLogger.getRecords(true).iterator(), columnInfo, "output", TasksOverviewController.DebugView);

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
        StaticVariables.randomPick = mainApp.tasks.getTask("settings").getValue("viewer/random_pick_color").toLowerCase().startsWith("t");
        int i = 0;
        for (String color : mainApp.tasks.getTask("settings").getValue("viewer/color_pool").split("\\|")) {
            StaticVariables.colorPool.put(i, color.trim());
            i++;
        }
        StaticVariables.resetColorPool();
        currentTableName = "";
    }


//    public boolean showAnnoTable(String dbName, String tableName, String filter, String colorDifferential) {
//        if (doctable)
//            annoTableView.getColumns().clear();
//        doctable = false;
//        return showDBTable(dbName, "queryAnnos", tableName, filter, colorDifferential);
//    }
//
//
//    public boolean showDocTable(String dbName, String tableName, String filter, String colorDifferential) {
//        if (!doctable)
//            annoTableView.getColumns().clear();
//        doctable = true;
//        return showDBTable(dbName, "queryDocs", tableName, filter, colorDifferential);
//    }

//    public boolean showDBTable(RecordRowIterator rs, String colorDifferential) {
//        ColumnInfo columanInfo = rs.getColumninfo();
//        return showDBTable(rs, columanInfo, colorDifferential, annoTableView);
//    }

    private int findTab(Object tabContentRegion) {
        if (tabLocator.containsKey(tabContentRegion)) {
            return tabLocator.get(tabContentRegion);
        } else {
            int i = 0;
            for (Tab t : tabPane.getTabs()) {
                tabLocator.put(t.getContent().getParent(), i);
                i++;
            }
            if (tabLocator.containsKey(tabContentRegion)) {
                return tabLocator.get(tabContentRegion);
            } else {
                return -1;
            }
        }
    }

    public boolean showDBTable(String sql, String dbName, String colorDifferential, String tableViewName, Object... values) {
        if (sql == null || sql.length() == 0 || sql.equals("null"))
            return false;
        dao = EDAO.getInstance(new File(dbName));
        RecordRowIterator recordRowIter;
        ColumnInfo columnInfo;
        Object[] res;
        if (sql.toLowerCase().startsWith("select ")) {
            res = dao.queryRecordsNMeta(sql);
        } else {
            res = dao.queryRecordsNMetaFromPstmt(sql, values);
        }
        recordRowIter = (RecordRowIterator) res[0];
        columnInfo = (ColumnInfo) res[1];
        TableView tableView = null;
        String[] splitCondition = splitCondition(sql);
        String core = splitCondition[0];
        String filter = splitCondition[1];
        switch (tableViewName) {
            case DocView:
                tableView = docTableView;
                currentSQLs.put(DocView, core);
                docSqlFilter.setText(filter);
                currentDBFileName.put(DocView, dbName);
                break;
            case AnnoView:
                tableView = annoTableView;
                currentSQLs.put(AnnoView, core);
                annoSqlFilter.setText(filter);
                currentDBFileName.put(AnnoView, dbName);
                break;
            case CompareView:
                tableView = compareTableView;
                currentSQLs.put(CompareView, core);
                compareSqlFilter.setText(filter);
                currentDBFileName.put(CompareView, dbName);
                break;
            case DebugView:
                tableView = debugTableView;
                currentSQLs.put(DebugView, core);
                currentDBFileName.put(DebugView, dbName);
                break;
        }
        return showDBTable(recordRowIter, columnInfo, colorDifferential, tableView, tableViewName);
    }

    private String[] splitCondition(String sql) {
        String sqlLower = sql.toLowerCase();
        String filter = "";
        String core = sql;
        int pos = sqlLower.indexOf("where");
        if (pos != -1) {
            filter = sql.substring(pos + 6);
            core = sql.substring(0, pos);
        } else {
            pos = sqlLower.indexOf("limit");
            if (pos != -1) {
                filter = sql.substring(pos);
                core = sql.substring(0, pos);
            }
        }
        return new String[]{core, filter};
    }

    public boolean showDBTable(Object[] queryOutputs, String colorDifferential, String tableViewName, String... filterColumnNames) {
        if (filterColumnNames == null || filterColumnNames.length == 0) {
            return showDBTable((Iterator) queryOutputs[0], (ColumnInfo) queryOutputs[1], colorDifferential, tableViewName);
        } else {
            HashSet<String> excludeColumns = new HashSet<>(Arrays.asList(filterColumnNames));
            ColumnInfo columnInfo = (ColumnInfo) queryOutputs[1];
            ColumnInfo filteredColumnInfo = new ColumnInfo();
            for (Map.Entry<String, String> col : columnInfo.getColumnInfo().entrySet()) {
                String columnName = col.getKey();
                String columnType = col.getValue();
                if (!excludeColumns.contains(columnName))
                    filteredColumnInfo.addColumnInfo(columnName, columnType);
            }
            return showDBTable((Iterator) queryOutputs[0], filteredColumnInfo, colorDifferential, tableViewName);
        }

    }

    public boolean showDBTable(Iterator rs, ColumnInfo columanInfo, String colorDifferential, String tableViewName) {
        TableView tableView = null;
        switch (tableViewName) {
            case DocView:
                tableView = docTableView;
                break;
            case AnnoView:
                tableView = annoTableView;
                break;
            case CompareView:
                tableView = compareTableView;
                break;
            case DebugView:
                tableView = debugTableView;
                break;
        }
        return showDBTable(rs, columanInfo, colorDifferential, tableView, tableViewName);
    }

    public boolean showDBTable(Iterator rs, ColumnInfo columanInfo, String colorDifferential, TableView tableView, String tableViewName) {
        dbPanel.setVisible(true);
//      magic: tabPane won't show without following line
        tabPane.setPrefSize(600, 500);
        showing = true;

        tableView.setVisible(true);
        Object tabContentRegion = tableView.getParent().getParent();
        int tabId = findTab(tabContentRegion);
        SingleSelectionModel<Tab> selectionModel = tabPane.getSelectionModel();
        if (tabId != -1 && selectionModel.getSelectedIndex() != tabId) {
            selectionModel.select(tabId);
        }
        tabPane.setVisible(true);

        ObservableList<ObservableList> data = FXCollections.observableArrayList();
        ColorAnnotationCell.colorDifferential = colorDifferential;
        int numOfColumns = columanInfo.getColumnInfo().size();
        if (columanInfo.getColumnInfo().containsKey("BEGIN"))
            numOfColumns -= 2;
//        System.out.println("annoTableView.getColumns().size=" + annoTableView.getColumns().size());
//        System.out.println("columanInfo size=" + columanInfo.getColumnInfo().size());
        if (tableView.getColumns().size() == 0 || tableView.getColumns().size() != columanInfo.getColumnInfo().size() - 4) {
            int i = 0;
            tableView.getColumns().clear();
            int docNamePos = -1, snippetPos = -1;
            for (String columnName : columanInfo.getColumnInfo().keySet()) {
                //use non property style for making dynamic table
                final int j = i;
                TableColumn col = new TableColumn(columnName);
                String widthStr;
                switch (columnName.toUpperCase()) {
                    case "BEGIN":
                    case "END":
                    case "TEXT":
                    case "DOC_TEXT":
                    case "FEATURES":
                    case "SNIPPET_BEGIN":
                        continue;
                    case "DOC_NAME":
                        docNamePos = i;
                        widthStr = mainApp.tasks.getTask("settings").getValue("viewer/width/" + columnName).trim();
                        if (widthStr.length() > 0)
                            col.setPrefWidth(Integer.parseInt(widthStr));
                        col.setCellFactory(colorCellHidenFactory);
                        break;
                    case "SNIPPET":
                        snippetPos = i;
                        col.setMaxWidth(dbPanel.getWidth() * 0.7);
                        widthStr = mainApp.tasks.getTask("settings").getValue("viewer/width/" + columnName).trim();
                        if (widthStr.length() > 0)
                            col.setPrefWidth(Integer.parseInt(widthStr));
                        else
                            col.setPrefWidth(StaticVariables.snippetLength * 5);
                        col.setCellFactory(colorCellFactory);
                        col.setCellValueFactory((Callback<TableColumn.CellDataFeatures<ObservableList, Object>, ObservableValue<Object>>) param -> {
                            Object record = param.getValue().get(j);
                            if (record != null && record instanceof RecordRow && !((RecordRow) record).getStrByColumnName("SNIPPET").equals("")
                                    && !((RecordRow) record).getStrByColumnName("SNIPPET").equals("null"))
                                return new SimpleObjectProperty<>(record);
                            else
                                return new SimpleObjectProperty<>("");
                        });
                        break;
                    default:
//                    case "TYPE":
                        widthStr = mainApp.tasks.getTask("settings").getValue("viewer/width/" + columnName).trim();
                        if (widthStr.length() > 0)
                            col.setPrefWidth(Integer.parseInt(widthStr));
                        col.setCellFactory(textCellFactory);
                        if (columnName.equals("DOC_NAME")) {

                        } else {
                            col.setCellValueFactory((Callback<TableColumn.CellDataFeatures<ObservableList, Object>, ObservableValue<Object>>) param -> {
                                Object record = param.getValue().get(j);
                                if (record != null) {
//                                  cleaner display of features
                                    if (columnName.endsWith("_FEATURES")) {
                                        String features = (String) record;
                                        int featuresPointer = features.toLowerCase().indexOf("features:");
                                        if (featuresPointer > 0) {
                                            if (features.toLowerCase().indexOf("features: null") == -1) {
                                                features = features.substring(featuresPointer + 9);
                                                features = features.replaceAll("\t\t", "");
                                            } else {
                                                features = features.substring(0, featuresPointer) +
                                                        features.substring(features.indexOf("\n", featuresPointer) + 1);
                                                features = features.replaceAll("Note: null\n", "");
                                                features = features.replaceAll("Topic:[^\\n]+\n", "");
                                            }
                                        }
                                        return new SimpleObjectProperty<>(features);
                                    } else
                                        return new SimpleObjectProperty<>(record);

                                } else
                                    return new SimpleObjectProperty<>("");
                            });
                        }
                        if (col.getPrefWidth() == 80)
                            col.setPrefWidth(90);
                }
                tableView.getColumns().addAll(col);

                i++;
            }
            tableView.setRowFactory(new KeyboardNavRowFactory(null, tableViewName));

            ObservableList columns = tableView.getColumns();
            if (docNamePos > 0) {
                TableColumn docNameColumn = (TableColumn) columns.get(docNamePos);
//            System.out.println(docNameColumn);
                final int staticSnippetPos = snippetPos;
                final int staticDocNamePos = docNamePos;
                docNameColumn.setCellValueFactory((Callback<TableColumn.CellDataFeatures<ObservableList, Object>, ObservableValue<Object>>) param -> {
                    Object record = param.getValue();
                    if (staticSnippetPos != -1)
                        param.getValue().get(staticSnippetPos);
                    if (record != null && record instanceof RecordRow) {
                        RecordRow newRecordRow = ((RecordRow) record).clone();
                        return new SimpleObjectProperty<>(newRecordRow);
                    } else
                        return new SimpleObjectProperty<>(param.getValue().get(staticDocNamePos));
                });
            }
        }
        dbPanel.widthProperty().addListener(new ChangeListener<Number>() {
            @Override
            public void changed(ObservableValue<? extends Number> observable, Number oldValue, Number newValue) {
                if (tableView.getColumns() != null && tableView.getColumns().size() > 1) {
                    htmlViewer.setPrefWidth(newValue.doubleValue() * 0.11);
                }
            }
        });
        int tabIdx = selectionModel.getSelectedIndex();
        docIdRowPosMap.put(tabIdx, new HashMap<>());
        HashMap<String, Integer> thisTabRowsMap = docIdRowPosMap.get(tabIdx);
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
                    case "DOC_TEXT":
                    case "FEATURES":
                    case "SNIPPET_BEGIN":
                        continue;
                    case "DOC_NAME":
                        row.add(record);
                        break;
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
            String bunchId = record.getStrByColumnName("BUNCH_ID");
            if (bunchId != null) {
                if (!thisTabRowsMap.containsKey(bunchId))
                    thisTabRowsMap.put(bunchId, data.size());
            }
            data.add(row);
            haveRead = true;
        }
        if (dao != null && !dao.isClosed())
            dao.close();
        tableView.setItems(data);
        tableView.refresh();

        showing = false;
        if (tableMemoRowId.containsKey(tableViewName)) {
            tableView.getSelectionModel().clearSelection();
            tableView.requestFocus();
            Main.logger.info("Retrieve table "+tableViewName+"'s memo row id: "+tableMemoRowId.get(tableViewName));
            tableView.getSelectionModel().select(tableMemoRowId.get(tableViewName));
            tableView.getFocusModel().focus(tableMemoRowId.get(tableViewName));
        }
        return haveRead;
    }


    public void updateHTMLEditor(String html, Object item) {
        webEngine.loadContent(html);
        if (item instanceof RecordRow) {
            RecordRow record = (RecordRow) item;
            if (record.getStrByColumnName("FEATURES") != null && record.getStrByColumnName("FEATURES").length() > 0) {
                annoDetails.setBottom(featureTable);
                updateFeatureTable(record.getStrByColumnName("FEATURES"));
            } else {
                annoDetails.setBottom(null);
            }
        } else {
            annoDetails.setBottom(null);
        }
    }

    public void updateHTMLEditor(String text) {
        text = text.replaceAll("\\n", "<br>");
//        htmlEditor.setHtmlText(text);
        updateHTMLEditor(text, "");
    }


    public static StringBuilder scrollWebView(int xPos, int yPos) {
        StringBuilder script = new StringBuilder().append("<html>");
        script.append("<head>");
        script.append("   <script language=\"javascript\" type=\"text/javascript\">");
        script.append("       function toBottom(){");
        script.append("           window.scrollTo(" + xPos + ", " + yPos + ");");
        script.append("       }");
        script.append("   </script>");
        script.append("</head>");
        script.append("<body onload='toBottom()'>");
        return script;
    }


    private void updateFeatureTable(String features) {
        if (features.startsWith("Note:") && features.substring(5, features.indexOf("\n")).indexOf(":") > 0) {
            features = "\t" + features.substring(5);
        }
        ObservableList<String[]> data = FXCollections.observableArrayList();
        String[] featurelines = features.split("\\n");
        for (int i = 0; i < featurelines.length; i++) {
            String featureNameValue = featurelines[i];
            if (featureNameValue.startsWith("Features: \t\t")) {
                StringBuilder sb = new StringBuilder();
                sb.append(featureNameValue.substring(12));
                i++;
                while (i < featurelines.length && featurelines[i].startsWith("\t\t")) {
                    sb.append("\n");
                    sb.append(featurelines[i].substring(3));
                    i++;
                }
                ObservableList<Object> row = FXCollections.observableArrayList();
                data.add(new String[]{"Features", sb.toString()});
                continue;
            }
            ObservableList<Object> row = FXCollections.observableArrayList();
            data.add(featureNameValue.split(":"));
        }
        featureTable.setItems(data);
        featureTable.refresh();
    }


    /**
     * Is called by the main application to give a reference back to itself.
     *
     * @param mainApp main class instance
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
                executeTaskClass(setting.getSettingValue().trim(), button);

            });
            executePanel.getChildren().add(button);
        }
    }

    private Thread executeTaskClass(String taskClassName, Button... buttons) {
        Task thisTask = getTaskFromString(taskClassName, buttons);
        if (thisTask instanceof GUITask)
            currentGUITask = (GUITask) thisTask;
        mainApp.bottomViewController.progressBar.progressProperty().bind(thisTask.progressProperty());
        mainApp.bottomViewController.msg.textProperty().bind(thisTask.messageProperty());
        Thread thisThread = new Thread(thisTask);
        thisThread.start();
        mainApp.bottomViewController.cancelButton.setOnAction((cancel) -> {
            thisThread.interrupt();
        });
        return thisThread;
    }

    public Task getTaskFromString(String taskString, Button... buttons) {
        String para = "", taskClassName = "";
        int splitter = taskString.indexOf(" ");
        if (splitter != -1) {
            taskClassName = taskString.substring(0, splitter);
            para = taskString.substring(splitter + 1).trim();
        } else {
            taskClassName = taskString;
        }
        Task thisTask = null;
        Class<? extends Task> c = null;
        try {
            c = Class.forName(taskClassName).asSubclass(Task.class);
            Constructor<? extends Task> taskConstructor = null;
            if (para.length() > 0) {
                if (buttons.length > 0) {
                    try {
                        taskConstructor = c.getConstructor(TasksFX.class, String.class, Button.class);
                        thisTask = taskConstructor.newInstance(mainApp.tasks, para, buttons[0]);
                    } catch (NoSuchMethodException e) {

                    }
                }
                if (taskConstructor == null) {
                    try {
                        taskConstructor = c.getConstructor(TasksFX.class, String.class);
                        thisTask = taskConstructor.newInstance(mainApp.tasks, para);
                    } catch (NoSuchMethodException e) {
                        e.printStackTrace();
                    }
                }
            } else {
                if (buttons.length > 0) {
                    try {
                        taskConstructor = c.getConstructor(TasksFX.class, Button.class);
                        thisTask = taskConstructor.newInstance(mainApp.tasks, buttons[0]);
                    } catch (NoSuchMethodException e) {
                    }
                }
                if (taskConstructor == null) {
                    try {
                        taskConstructor = c.getConstructor(TasksFX.class);
                        thisTask = taskConstructor.newInstance(mainApp.tasks);
                    } catch (NoSuchMethodException e) {
                        e.printStackTrace();
                    }
                }
            }
            if (thisTask instanceof GUITask)
                currentGUITask = (GUITask) thisTask;
        } catch (ClassNotFoundException e) {
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


    private void createContextMenu(WebView webView) {
        ContextMenu contextMenu = new ContextMenu();
        MenuItem reload = new MenuItem("Reload");
        reload.setOnAction(e -> webView.getEngine().reload());
        MenuItem savePage = new MenuItem("Debug");
        savePage.setOnAction(e -> {
            Document doc = webView.getEngine().getDocument();
            String content;
//            String content = doc.getDocumentElement().getTextContent();
//
//            System.out.println(content);
            StringWriter sw = new StringWriter();
            try {
                Transformer transformer = TransformerFactory.newInstance().newTransformer();
                transformer.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "no");
                transformer.setOutputProperty(OutputKeys.METHOD, "xml");
                transformer.setOutputProperty(OutputKeys.INDENT, "yes");
                transformer.setOutputProperty(OutputKeys.ENCODING, "UTF-8");
                transformer.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "4");

                transformer.transform(new DOMSource(doc.getDocumentElement()),
                        new StreamResult(sw));
            } catch (Exception ex) {
                ex.printStackTrace();
            }
            content = sw.toString();
            content = content.replaceAll("<BR/>", "\n")
                    .replaceAll("<SPAN style=[^>]+>", "")
                    .replaceAll("</SPAN>", "")
                    .replaceAll("</DIV>", "")
                    .replaceAll("</DIV>", "")
                    .replaceAll("<DIV[^>]+>", "")
                    .replaceAll("<BODY[^>]+>", "")
                    .replaceAll("</BODY>", "")
                    .replaceAll("</HTML>", "")
                    .replaceAll("<HEAD/>", "")
                    .replace("<?xml version=\"1.0\" encoding=\"UTF-8\"?><HTML xmlns=\"http://www.w3.org/1999/xhtml\">", "");

//            System.out.println(content.trim());
//            DebugPipe fastDebugPipe = new DebugPipe(mainApp.tasks, currentGUITask);
//            System.out.println(this.annoTableView.getSelectionModel().getSelectedItem());
        });
        contextMenu.getItems().addAll(reload, savePage);

        webView.setOnMousePressed(e -> {
            if (e.getButton() == MouseButton.SECONDARY) {
                contextMenu.show(webView, e.getScreenX(), e.getScreenY());
            } else {
                contextMenu.hide();
            }
        });
    }
}
