package edu.utah.bmi.simple.gui.controller;

import edu.utah.bmi.nlp.sql.EDAO;
import edu.utah.bmi.nlp.sql.RecordRow;
import edu.utah.bmi.nlp.sql.RecordRowIterator;
import edu.utah.bmi.simple.gui.entry.TaskFX;
import edu.utah.bmi.simple.gui.task.ConfigKeys;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;

import java.io.File;


/**
 * @author Jianlin Shi
 * Created on 2/13/17.
 */
public class ColorAnnotationCellHide extends ColorAnnotationCell {
    protected Color[] colors = new Color[]{Color.LIGHTGREY, Color.WHITE};
    protected Background[] backgrounds = new Background[]{new Background(new BackgroundFill(colors[0], null, null)),
            new Background(new BackgroundFill(colors[1], null, null))};
    protected static String previousValue = "";
    protected static int backgroundId = 0, previousSnippetBegin = -1;
    private static String previousHTML = "";


    protected void updateItem(Object item, boolean empty) {
//        System.out.println(">>" + item + "<<");
        superUpdateItem(item, empty);
        if (!empty) {
            hbox = new HBox();
            if (item instanceof RecordRow)
                addText(((RecordRow) item).getStrByColumnName("DOC_NAME"));
            else
                addText(item + "");
        }
    }

    protected void addText(String text) {
        if (!text.equals(previousValue)) {
            backgroundId = backgroundId == 1 ? 0 : 1;
            previousValue = text;
        }
        renderHighlighter("", "", text, "000000", backgrounds[backgroundId]);
    }

    public String generateHTML() {
        Object value = this.itemProperty().getValue();
        return generateHTML(value);
    }


    public static String generateHTML(Object value) {
//       when click DOC_NAME, show document text with snippet highlighted
        String html;
        if (value instanceof RecordRow && ((RecordRow) value).getValueByColumnName("DOC_TEXT") != null) {
            RecordRow recordRow = (RecordRow) value;
            if (recordRow.getValueByColumnName("SNIPPET_BEGIN") == null ||
                    recordRow.getValueByColumnName("SNIPPET") == null ||
                    recordRow.getValueByColumnName("SNIPPET").equals("")) {
                html = recordRow.getStrByColumnName("DOC_TEXT");
            } else {
                html = recordRow.getStrByColumnName("DOC_TEXT");
                String color = ColorAnnotationCell.pickColor(recordRow, ColorAnnotationCell.colorDifferential);
                int begin, end;
                if (recordRow.getStrByColumnName("TYPE").toLowerCase().startsWith("sentence")) {
                    int snippetBegin = getIntValue(recordRow.getValueByColumnName("SNIPPET_BEGIN"));
                    begin = getIntValue(recordRow.getValueByColumnName("BEGIN")) + snippetBegin;
                    end = getIntValue(recordRow.getValueByColumnName("END")) + snippetBegin;
                } else {
                    begin = getIntValue(recordRow.getValueByColumnName("SNIPPET_BEGIN"));
                    end = begin + recordRow.getStrByColumnName("SNIPPET").length();
                }
                html = ColorAnnotationCell.generateHTML(html,
                        begin, end,
                        color);
            }
            html = html.replaceAll("\\n", "<br>");
        } else if (value instanceof RecordRow) {
            RecordRow recordRow = (RecordRow) value;
            Object docName = recordRow.getValueByColumnName("DOC_NAME");
            if (!docName.equals(previousValue) || (recordRow.getValueByColumnName("SNIPPET_BEGIN") != null && getIntValue(recordRow.getValueByColumnName("SNIPPET_BEGIN")) != previousSnippetBegin)) {
                html = queryDocContent(docName.toString()).getStrByColumnName("TEXT");
                if (html.length() > 0 && recordRow.getValueByColumnName("SNIPPET_BEGIN") != null) {
                    String color = ColorAnnotationCell.pickColor(recordRow, ColorAnnotationCell.colorDifferential);
                    int begin = getIntValue(recordRow.getValueByColumnName("SNIPPET_BEGIN"));
                    previousSnippetBegin = begin;
                    int end = begin + recordRow.getStrByColumnName("SNIPPET").length();
                    html = ColorAnnotationCell.generateHTML(html,
                            begin, end,
                            color);
                } else {
                    html = docName + "";
                }
                html = html.replaceAll("\\n", "<br>");
                previousHTML = html;
                previousValue = docName + "";
            } else {
                html = previousHTML;
            }
        } else {
            html = value + "";
            html = html.replaceAll("\\n", "<br>");
        }
        return html;
    }

    public static RecordRow queryDocContent(String docName) {
        TaskFX task = TasksOverviewController.currentTasksOverviewController.mainApp.tasks.getTask("settings");
        String inputDB = task.getValue(ConfigKeys.readDBConfigFileName);
        EDAO dao = EDAO.getInstance(new File(inputDB));
        String docTableName = task.getValue(ConfigKeys.inputTableName);
        dao.initiateTableFromTemplate("DOCUMENTS_TABLE", docTableName, false);
        RecordRowIterator records = dao.queryRecordsFromPstmt(docTableName, docName);
        RecordRow recordRow = null;
        if (records.hasNext()) {
            recordRow = records.next();
        }
        dao.close();
        return recordRow;
    }


}
