package com.shutdownhook.sharetoroku.util;

/* RELEASE */

public class Loggy {
    public Loggy(String tag) { }
    public String e(String format, Object... args) { return(""); }
    public String w(String format, Object... args) { return(""); }
    public String i(String format, Object... args) { return(""); }
    public String d(String format, Object... args) { return(""); }
}

/* DEVELOPMENT */

/*
import android.util.Log;

public class Loggy {

    public Loggy(String tag) {
        this.tag = tag;
    }

    public String e(String format, Object... args) {
        String msg = String.format(format, args);
        Log.e(tag, msg);
        return(msg);
    }

    public String w(String format, Object... args) {
        String msg = String.format(format, args);
        Log.w(tag, msg);
        return(msg);
    }

    public String i(String format, Object... args) {
        String msg = String.format(format, args);
        Log.i(tag, msg);
        return(msg);
    }

    public String d(String format, Object... args) {
        String msg = String.format(format, args);
        Log.d(tag, msg);
        return(msg);
    }

    private String tag;
}
*/