package edu.utah.bmi.nlp;

import org.joda.time.DateTime;
import org.joda.time.format.DateTimeFormatter;
import org.joda.time.format.ISODateTimeFormat;
import org.junit.Test;
import org.pojava.datetime.DateTimeConfig;

import java.util.Calendar;
import java.util.Date;
import java.util.GregorianCalendar;

public class TestJodaTime {
    @Test
    public void test(){
        String strInputDateTime;
        // string is populated with a date time string in some fashion
        DateTimeFormatter fmt = ISODateTimeFormat.dateTime();
        DateTime dt = fmt.parseDateTime("02/78");
        System.out.println(dt.toString());
    }

    @Test
    public void testPojava(){
        Date utilDate = null;
        try {
            DateTimeConfig dcfg =  DateTimeConfig.getDateTimeConfig(new GregorianCalendar(78 + 2000, 4, 22).getTime());
            System.out.println(new org.pojava.datetime.DateTime("July of 2009",dcfg).toDate());
            System.out.println(new org.pojava.datetime.DateTime("2/3/77",dcfg));
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Test
    public void testPojava2(){
        org.pojava.datetime.DateTime dt = new org.pojava.datetime.DateTime("2:53 pm, January 26, 1969");
        System.out.println(dt.toString());
        DateTimeConfig dcfg = DateTimeConfig.getDateTimeConfig(new GregorianCalendar(78 + 2000, 4, 22).getTime());
        org.pojava.datetime.DateTime utilDate = new org.pojava.datetime.DateTime("2/77", dcfg);
        System.out.println(utilDate);
    }



}
