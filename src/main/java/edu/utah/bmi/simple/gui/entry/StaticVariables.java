package edu.utah.bmi.simple.gui.entry;

import java.util.*;

/**
 * @author Jianlin Shi
 *         Created on 2/14/17.
 */
public class StaticVariables {

    public static String htmlMarker0 = "<span style=\"background-color: #FFFF00\">";
    public static String htmlMarker1 = "</span>";
    public static String preTag = "";
    public static String postTag = "";

    public static int preTagLength, postTagLength;
    public static int snippetLength = 100;

    public static LinkedHashMap<Integer, String> colorPool = new LinkedHashMap<>();
    public static LinkedHashMap<String, String> usedColor = new LinkedHashMap<>();
    public static Stack<Integer> availableColor = new Stack<>();
    public static boolean randomPick = true;

    public static String pickColor(String colorDifferential) {
        String color = "00DDFF";
        if (usedColor.containsKey(colorDifferential)) {
            color = usedColor.get(colorDifferential);
        } else {
            int colorId;
            if (availableColor.size() > 0) {
                colorId = availableColor.pop();
            } else {
                System.out.println("not enough colors in the color pool, randomly pick a used one.");
                colorId = (int) new Random().nextDouble() * availableColor.size();
            }
            color = colorPool.get(colorId);
            usedColor.put(colorDifferential, color);
        }
        return color;
    }

    public static void resetColorPool() {
        availableColor.clear();
        for (int i = 0; i < colorPool.size(); i++) {
            availableColor.add(i);
        }
        if (randomPick)
            Collections.shuffle(availableColor);
        usedColor.clear();
    }
}
