package edu.utah.bmi.simple.gui.controller;

import edu.utah.bmi.simple.gui.entry.StaticVariables;
import edu.utah.bmi.sql.Record;
import javafx.collections.ObservableList;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.control.OverrunStyle;
import javafx.scene.control.TableCell;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;

import static edu.utah.bmi.simple.gui.entry.StaticVariables.snippetLength;


/**
 * @author Jianlin Shi
 *         Created on 2/13/17.
 */
public class ColorAnnotationCell extends TableCell<ObservableList, Object> {
    HBox hbox;
    private int maxTxtWindow;
    public static String colorDifferential;

    public ColorAnnotationCell() {
    }


    protected void updateItem(Object item, boolean empty) {
//        System.out.println(">>" + item + "<<");
        super.updateItem(item, empty);
        if (!empty) {
            hbox = new HBox();
            addText((Record) item);
        }
    }

    private void addText(Record record) {
        int sentenceLength = record.sentence.length();
        String pre, marker, post;
        if (sentenceLength > 0) {
            maxTxtWindow = (snippetLength - record.getText().length()) / 2;
            hbox.setPrefWidth(snippetLength + 20);
            int postCut = cutTail(record);
            int preCut = cutHeader(record, postCut);
            String text = record.getSentence();
            if (preCut > 0)
                pre = "..." + text.substring(preCut + 3, record.getBegin());
            else
                pre = text.substring(preCut, record.getBegin());

            marker = text.substring(record.getBegin(), record.getEnd());
            if (postCut < sentenceLength)
                post = text.substring(record.getEnd(), postCut - 3) + "...";
            else
                post = text.substring(record.getEnd(), postCut);
        } else {
            String text = record.getText();
            if (text.length() > snippetLength) {
                text = text.substring(0, snippetLength) + "...";
            }
            pre = text;
            marker = "";
            post = "";
        }
        String color = pickColor(record, colorDifferential);
        pre = pre.replaceAll("[\\s|\\n]", " ");
        marker = marker.replaceAll("[\\s|\\n]", " ");
        post = post.replaceAll("[\\s|\\n]", " ");
        renderHighlighter(pre, marker, post, color);
    }

    public static String pickColor(Record record, String differential) {
        String color;
        String key = "";
        switch (differential) {
            case "output":
                key = record.type;
                break;
            case "diff":
                key = record.annotator + "|" + record.note;
                break;
        }
        color = StaticVariables.pickColor(key);
        return color;
    }

    private void renderHighlighter(String pre, String marker, String post, String color) {
        pre = pre.replaceAll("\\n", " ");
        marker = marker.replaceAll("\\n", " ");
        post = post.replaceAll("\\n", " ");
        hbox.setAlignment(Pos.CENTER_LEFT);
        hbox.getChildren().clear();
        Label preLabel = new Label(pre);
        preLabel.setTextOverrun(OverrunStyle.LEADING_ELLIPSIS);
        Label highlighter = new Label(marker);
        highlighter.setStyle("-fx-background-color: #" + color + ";");
        highlighter.setMinWidth(Region.USE_PREF_SIZE);
        Label postLabel = new Label(post);
        hbox.getChildren().addAll(preLabel, highlighter, postLabel);
        hbox.setHgrow(preLabel, Priority.NEVER);
        hbox.setHgrow(highlighter, Priority.NEVER);
        hbox.setHgrow(postLabel, Priority.ALWAYS);

        setGraphic(hbox);
    }


    private int cutTail(Record record) {
        String snippet = record.getSentence();
        int markerEnd = (record.getEnd());
        int postLength = snippet.length() - markerEnd;
        if (snippet.length() < StaticVariables.snippetLength) {
            return snippet.length();
        }
        int minCutLength = maxTxtWindow;
        int preTxtLength = record.getBegin();
        if (preTxtLength < maxTxtWindow) {
            minCutLength += maxTxtWindow - preTxtLength;
        }
        if (postLength > minCutLength) {
            postLength = minCutLength;
        }
        return markerEnd + postLength;
    }

    private int cutHeader(Record record, int postCut) {
        if (postCut < StaticVariables.snippetLength) {
            return 0;
        }
        int preLength = record.getBegin();
        int markerEnd = (record.getEnd());
        int postLength = postCut - markerEnd;
        int minCutLength = maxTxtWindow;
        if (postLength < maxTxtWindow) {
            minCutLength += maxTxtWindow - postLength;
        }
        if (preLength < minCutLength) {
            return 0;
        }
        return preLength - minCutLength;
    }


    public static String generateHTML(String text, int begin, int end, String color) {
        String html;
        String pre = text.substring(0, begin);
        String txt = text.substring(begin, end);
        String post = text.substring(end);
        html = StaticVariables.preTag + pre + StaticVariables.htmlMarker0.toLowerCase().replaceAll("ffffff", color) + txt + StaticVariables.htmlMarker1 + post + StaticVariables.postTag;
        return html;
    }


}
