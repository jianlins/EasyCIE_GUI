package edu.utah.bmi.simple.gui.core;

import edu.utah.bmi.nlp.sql.RecordRow;

import java.util.ArrayList;

/**
 * @author by Jianlin Shi on 5/4/2017.
 */
public class EvalCounter {

    public ArrayList<RecordRow> fns, fps;

    public int tp, fp, tn, fn;

    protected boolean tnAvaiable = false;

    public EvalCounter() {
        tp = 0;
        fp = 0;
        tn = 0;
        fn = 0;
        fns = new ArrayList<>();
        fps = new ArrayList<>();
    }

    public EvalCounter(int tp, int fp, int tn, int fn) {
        this.tp = tp;
        this.fp = fp;
        this.tn = tn;
        this.fn = fn;
    }

    public void addTp() {
        tp++;
    }

    public void addFp() {
        fp++;
    }

    public void addTn() {
        tn++;
        tnAvaiable = true;
    }

    public void addFn() {
        this.fn++;
    }

    public void addTp(int amount) {
        tp += amount;
    }

    public void addFp(int amount) {
        fp += amount;
    }

    public void addTn(int amount) {
        tn += amount;
        tnAvaiable = true;
    }

    public void addFn(int amount) {
        this.fn += amount;
    }

    public double ppv() {
        return Math.round (100.0 * tp / (tp + fp))/100.0;
    }

    public double npv() {
        return Math.round (100.0 * tn / (tn + fn))/100.0;
    }

    public double accuracy() {
        return Math.round (100.0 * (tp + tn) / total())/100.0;
    }

    public double precision() {
        return Math.round (100.0 * tp / (tp + fp))/100.0;
    }

    public double recall() {
        return Math.round (100.0 * tp / (tp + fn))/100.0;
    }

    public double f1() {
        return Math.round (200.0 * tp / (2 * tp + fp + fn))/100.0;
    }

    public int total() {
        return tp + fp + tn + fn;
    }

    public double sensitivity() {
        return Math.round (100.0 * tp / (tp + fn))/100.0;
    }

    public double specificity() {
        return Math.round (100.0* tn / (tn + fp))/100.0;
    }

    public String report() {
        return report("");
    }

    public String report(String offset) {
        StringBuilder sb = new StringBuilder();
        sb.append(offset);
        sb.append("Total : \t");
        sb.append(total());
        sb.append("\n" + offset);


        sb.append("True Positive: " + tp);
        sb.append("\n" + offset);
        sb.append("False Positive: " + fp);
        sb.append("\n" + offset);
        if (tnAvaiable) {
            sb.append("True Negative: " + tn);
        } else {
            sb.append("True Negative: N/A");
        }
        sb.append("\n" + offset);


        sb.append("False Negative: " + fn);
        sb.append("\n" + offset);

        sb.append("Precision: \t");
        sb.append(tp + "/" + "(" + tp + "+" + fp + ")=" + precision());
        sb.append("\n\n" + offset);

        sb.append("Recall: \t");
        sb.append(tp + "/" + "(" + tp + "+" + fn + ")=" + recall());
        sb.append("\n\n" + offset);

        sb.append("F1 score: \t");
        sb.append("2 x " + precision() + " x " + recall() + " / (" + precision() + " + " + recall() + ") = " + f1());
        sb.append("\n\n" + offset);

        sb.append("Accuracy: \t");
        sb.append("(" + tp + "+" + tn + ")/" + total() + "=" + accuracy());
        sb.append("\n\n" + offset);

        sb.append("Positive Predictive Value: \t");
        sb.append(tp + "/ (" + tp + "+" + fp + ")=" + ppv());
        sb.append("\n" + offset);
        if (tnAvaiable) {
            sb.append("Negative Predictive Value: \t");
            sb.append(tn + "/ (" + tn + "+" + fn + ")=" + npv());
            sb.append("\n\n" + offset);
        }

        sb.append("Sensitivity: \t");
        sb.append(tp + "/ (" + tp + "+" + fn + ")=" + sensitivity());
        sb.append("\n" + offset);

        if (tnAvaiable) {
            sb.append("Specificity: \t");
            sb.append(tn + "/ (" + tn + "+" + fp + ")=" + specificity());
        }


        return sb.toString();
    }

    public static void main(String[] args) {
        EvalCounter evalCounter = new EvalCounter(214, 2, 8, 6);
        System.out.println(evalCounter.report());

    }
}
