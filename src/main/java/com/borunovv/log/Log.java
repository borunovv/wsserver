/**
 * Copyright (c) 2012-2100 HeroCraft.
 */
package com.borunovv.log;

import java.text.SimpleDateFormat;
import java.util.Date;

/**
 * @author borunovv
 */
public class Log {

    private static final ThreadLocal<SimpleDateFormat> DATE_FORMAT =
            ThreadLocal.withInitial(() -> new SimpleDateFormat("yyyy-MM-dd HH:mm:ss"));


    public static void trace(String msg) {
        write("TRACE", msg);
    }

    public static void debug(String msg) {
        write("DEBUG", msg);
    }

    public static void info(String msg) {
        write("INFO", msg);
    }

    public static void warn(String msg) {
        write("WARN", msg);
    }

    public static void error(String msg) {
        write("ERROR", msg);
    }

    public static void error(String msg, Throwable t) {
        write("ERROR", msg, t);
    }

    private static void write(String level, String msg) {
        write(level, msg, null);
    }
    
    private static void write(String level, String msg, Throwable t) {
        String timestamp = DATE_FORMAT.get().format(new Date());
        System.out.println(String.format("%s %s %s", timestamp, level, msg));
        if (t != null) {
            t.printStackTrace(System.out);
        }
    }
}
