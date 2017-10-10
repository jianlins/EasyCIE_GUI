package edu.utah.bmi.simple.gui.controller;

import edu.utah.bmi.nlp.sql.RecordRow;
import edu.utah.bmi.simple.gui.entry.StaticVariables;
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
 * Created on 2/13/17.
 */
public class ColorAnnotationCell extends TableCell<ObservableList, Object> {
    HBox hbox;
    private int maxTxtWindow;
    public static String colorDifferential;
    public final static String colorOutput = "output";
    public final static String colorCompare = "diff";


    public ColorAnnotationCell() {
    }


    protected void updateItem(Object item, boolean empty) {
//        System.out.println(">>" + item + "<<");
        super.updateItem(item, empty);
        if (!empty) {
            hbox = new HBox();
            addText((RecordRow) item);
        }
    }

    private void addText(RecordRow recordRow) {
        String sentence = (String) recordRow.getValueByColumnName("SNIPPET");

        String pre, marker, post;
        if (recordRow.getValueByColumnName("BEGIN") != null) {
            sentence = sentence.replaceAll("\\n", " ");
            int sentenceLength = sentence.length();
            int begin = Integer.parseInt(recordRow.getValueByColumnName("BEGIN") + "");
            begin = begin < 0 ? 0 : begin;
            int end = Integer.parseInt(recordRow.getValueByColumnName("END") + "");
            end = end < 0 ? 0 : end;
            maxTxtWindow = (snippetLength - (end - begin)) / 2;
            maxTxtWindow = maxTxtWindow < 0 ? 0 : maxTxtWindow;
            hbox.setPrefWidth(snippetLength + 20);
            int postCut = cutTail(sentence, begin, end);
            int preCut = cutHeader(postCut, begin, end);
            if (preCut > 3 && preCut < begin)
                pre = "..." + sentence.substring(preCut + 3, begin);
            else
                pre = sentence.substring(preCut, begin);

            marker = sentence.substring(begin, end);
            if (postCut > 3 + end && postCut < sentenceLength)
                post = sentence.substring(end, postCut - 3) + "...";
            else
                post = sentence.substring(end, postCut);
        } else {
            if (sentence.length() > snippetLength) {
                sentence = sentence.substring(0, snippetLength) + "...";
            }
            pre = sentence;
            marker = "";
            post = "";
        }
        String color = pickColor(recordRow, colorDifferential);
        pre = pre.replaceAll("[\\s|\\n]", " ");
        marker = marker.replaceAll("[\\s|\\n]", " ");
        post = post.replaceAll("[\\s|\\n]", " ");
        renderHighlighter(pre, marker, post, color);
    }

    public static String pickColor(RecordRow RecordRow, String differential) {
        String color;
        String key = "";
        switch (differential) {
            case colorOutput:
                key = (String) RecordRow.getValueByColumnName("TYPE");
                break;
            case colorCompare:
                key = RecordRow.getValueByColumnName("ANNOTATOR") + "|" + RecordRow.getValueByColumnName("COMMENTS");
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


    private int cutTail(String snippet, int begin, int end) {
        int postLength = snippet.length() - end;
        if (snippet.length() < StaticVariables.snippetLength) {
            return snippet.length();
        }
        int minCutLength = maxTxtWindow;
        int preTxtLength = begin;
        if (preTxtLength < maxTxtWindow) {
            minCutLength += maxTxtWindow - preTxtLength;
        }
        if (postLength > minCutLength) {
            postLength = minCutLength;
        }
        return end + postLength;
    }

    private int cutHeader(int postCut, int begin, int end) {
        if (postCut < StaticVariables.snippetLength) {
            return 0;
        }
        int preLength = begin;
        int markerEnd = end;
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
