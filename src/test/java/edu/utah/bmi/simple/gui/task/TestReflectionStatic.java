package edu.utah.bmi.simple.gui.task;

import java.lang.reflect.Method;

public class TestReflectionStatic {
    public static String getValue(int i) {
        return i + "";
    }
    public static void main(String[]args) throws NoSuchMethodException {
        for(Method m:TestReflectionStatic.class.getDeclaredMethods()){
            System.out.println(m.getName());
        }
        Method m = TestReflectionStatic.class.getDeclaredMethod("getValue", Integer.class);
        System.out.println(m.getName());
    }
}
