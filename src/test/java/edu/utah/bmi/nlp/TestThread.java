package edu.utah.bmi.nlp;

import static java.lang.Thread.sleep;

public class TestThread {
    static int i;

    public static void main(String[] args) {
        T1 t = new T1();
        Thread thread = new Thread(t);
        Thread thread2 = new Thread(thread);
        thread.start();
        thread2.start();
    }


}

class T1 implements Runnable {
    int j=0;
    public T1() {

    }

    @Override
    public void run() {
        for (int i = 0; i < 100; i++) {
            try {
                sleep(1000);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            System.out.print(Thread.currentThread().getName()+"\t");
            System.out.println(j);
            j++;
        }

    }
}