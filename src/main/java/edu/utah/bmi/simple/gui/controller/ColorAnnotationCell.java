package edu.utah.bmi.simple.gui.controller;

import edu.emory.mathcs.backport.java.util.Arrays;
import edu.utah.bmi.nlp.sql.RecordRow;
import edu.utah.bmi.simple.gui.entry.StaticVariables;
import javafx.collections.ObservableList;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.control.OverrunStyle;
import javafx.scene.control.TableCell;
import javafx.scene.layout.*;

import java.util.ArrayList;
import java.util.List;

import static edu.utah.bmi.simple.gui.entry.StaticVariables.snippetLength;


/**
 * @author Jianlin Shi
 * Created on 2/13/17.
 */
public class ColorAnnotationCell extends TableCell<ObservableList, Object> {
    protected HBox hbox;
    protected int maxTxtWindow;
    public static String colorDifferential;
    public final static String colorOutput = "output";
    public final static String colorCompare = "diff";


    public ColorAnnotationCell() {
    }

    protected void superUpdateItem(Object item, boolean empty) {
        super.updateItem(item, empty);
    }

    protected void updateItem(Object item, boolean empty) {
//        System.out.println(">>" + item + "<<");
        superUpdateItem(item, empty);
        if (!empty) {
            hbox = new HBox();
            if (item instanceof RecordRow)
                addText((RecordRow) item);
            else
                addText(item + "");
        }
    }

    protected void addText(String text) {
        renderHighlighter("", "", text, "000000", Background.EMPTY);
    }

    protected void addText(RecordRow recordRow) {
        String sentence = (String) recordRow.getValueByColumnName("SNIPPET");
        if (sentence != null) {

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
                if (preCut > 3 && preCut + 3 < begin) {
//                    System.out.println(preCut+"\t"+begin+"\t"+sentence.length());
                    pre = "..." + sentence.substring(preCut + 3, begin);
                } else
                    pre = sentence.substring(preCut, begin);
                if (end > sentenceLength) {
                    end = sentenceLength;
                }
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
            renderHighlighter(pre, marker, post, color, Background.EMPTY);
        }
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

    protected void renderHighlighter(List<String> chunks, List<String> colors, Background background) {
        ArrayList<Label> labels = new ArrayList<>();
        hbox.setAlignment(Pos.CENTER_LEFT);
        hbox.setBackground(background);
        hbox.getChildren().clear();
        for (int i = 0; i < chunks.size(); i++) {
            String chunk = chunks.get(i).replaceAll("[\\n\\r]", " ");
            Label label = new Label(chunk);
            if (i == 0) {
                label.setTextOverrun(OverrunStyle.LEADING_ELLIPSIS);
            }
            if (colors.get(i).length() > 0) {
                label.setStyle("-fx-background-color: #" + colors.get(i) + ";");
                label.setMinWidth(Region.USE_PREF_SIZE);
            }
            labels.add(label);
        }
        hbox.getChildren().addAll(labels);
        for (int i = 0; i < labels.size() - 1; i++) {
            HBox.setHgrow(labels.get(i), Priority.NEVER);
        }
        HBox.setHgrow(labels.get(labels.size() - 1), Priority.ALWAYS);
        setGraphic(hbox);
    }

    protected void renderHighlighter(String pre, String marker, String post, String color, Background background) {
        renderHighlighter(Arrays.asList(new String[]{pre, marker, post}), Arrays.asList(new String[]{"", color, ""}), background);
//        pre = pre.replaceAll("\\n", " ");
//        marker = marker.replaceAll("\\n", " ");
//        post = post.replaceAll("\\n", " ");
//        hbox.setAlignment(Pos.CENTER_LEFT);
//        hbox.setBackground(background);
//        hbox.getChildren().clear();
//        Label preLabel = new Label(pre);
//        preLabel.setTextOverrun(OverrunStyle.LEADING_ELLIPSIS);
//        Label highlighter = new Label(marker);
//        highlighter.setStyle("-fx-background-color: #" + color + ";");
//        highlighter.setBackground(background);
//        highlighter.setMinWidth(Region.USE_PREF_SIZE);
//        Label postLabel = new Label(post);
//        hbox.getChildren().addAll(preLabel, highlighter, postLabel);
//        HBox.setHgrow(preLabel, Priority.NEVER);
//        HBox.setHgrow(highlighter, Priority.NEVER);
//        HBox.setHgrow(postLabel, Priority.ALWAYS);
//        setGraphic(hbox);
    }


    protected int cutTail(String snippet, int begin, int end) {
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

    protected int cutHeader(int postCut, int begin, int end) {
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


    public String generateHTML() {
        Object value = this.itemProperty().getValue();
        return generateHTMLFromUnKnownType(value);
    }

    public static String generateHTMLFromUnKnownType(Object value) {
        if (value instanceof RecordRow) {
            return generateHTML((RecordRow) value);
        } else {
            return generateHTML(value.toString());
        }
    }

    public static String generateHTML(String value) {
        String html = value + "";
        html = html.replaceAll("\\n", "<br>");
        return html;
    }


    public static String generateHTML(RecordRow recordRow) {
        String html;
        if (recordRow.getValueByColumnName("BEGIN") == null) {
            if (recordRow.getValueByColumnName("SNIPPET") == null)
                html = recordRow.getStrByColumnName("TEXT");
            else
                html = recordRow.getStrByColumnName("SNIPPET");
        } else {
            html = recordRow.getStrByColumnName("SNIPPET");
            String color = ColorAnnotationCell.pickColor(recordRow, ColorAnnotationCell.colorDifferential);
            html = ColorAnnotationCell.generateHTML(html,
                    getIntValue(recordRow.getValueByColumnName("BEGIN")),
                    getIntValue(recordRow.getValueByColumnName("END")),
                    color);
        }
        html = html.replaceAll("\\n", "<br>");
        return html;
    }

    public static int getIntValue(Object value) {
        if (value instanceof Long) {
            return ((Long) value).intValue();
        } else {
            return (int) value;
        }
    }
}
