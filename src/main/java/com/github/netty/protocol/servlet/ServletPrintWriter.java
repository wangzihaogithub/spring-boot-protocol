package com.github.netty.protocol.servlet;

import java.io.*;
import java.nio.charset.Charset;
import java.util.Formatter;
import java.util.Locale;

/**
 *  Printing flow
 * @author wangzihao
 */
public class ServletPrintWriter extends PrintWriter{
    private OutputStream out;
    private Charset charset;
    private String lineSeparator = System.lineSeparator();
    private boolean error = false;
    private static final Writer EMPTY_WRITER = new StringWriter(0);

    ServletPrintWriter(OutputStream out, Charset charset) {
        super(EMPTY_WRITER, false);
        this.out = out;
        this.charset = charset;
    }

    @Override
    public void flush() {
        try {
            out.flush();
        } catch (IOException e) {
            error = true;
        }
    }

    @Override
    public void close() {
        try {
            out.close();
        } catch (IOException e) {
            error = true;
        }
    }

    @Override
    public boolean checkError() {
        return error;
    }

    @Override
    protected void setError() {
        if(error){
            return;
        }
        this.error = true;
    }

    @Override
    protected void clearError() {
        error = false;
    }

    @Override
    public void write(int c) {
        write(String.valueOf(c));
    }

    @Override
    public void write(char[] buf, int off, int len) {
        write(String.valueOf(buf, off, len));
    }

    @Override
    public void write(char[] buf) {
        write(String.valueOf(buf));
    }

    @Override
    public void write(String s, int off, int len) {
        String writeStr;
        if(off == 0 && s.length() == len){
            writeStr = s;
        }else {
            writeStr = s.substring(off, off + len);
        }
        byte[] bytes = writeStr.getBytes(charset);

        try {
            out.write(bytes);
        } catch (IOException e) {
            setError();
        }
    }

    @Override
    public void write(String s) {
        write(s,0,s.length());
    }

    @Override
    public void print(boolean b) {
        write(b ? "true" : "false");
    }

    @Override
    public void print(char c) {
        write(String.valueOf(c));
    }

    @Override
    public void print(int i) {
        write(String.valueOf(i));
    }

    @Override
    public void print(long l) {
        write(String.valueOf(l));
    }

    @Override
    public void print(float f) {
        write(String.valueOf(f));
    }

    @Override
    public void print(double d) {
        write(String.valueOf(d));
    }

    @Override
    public void print(char[] s) {
        write(s);
    }

    @Override
    public void print(String s) {
        write(s);
    }

    @Override
    public void print(Object obj) {
        write(String.valueOf(obj));
    }

    @Override
    public void println() {
        write(lineSeparator);
    }

    @Override
    public void println(boolean b) {
        write((b ? "true" : "false") + lineSeparator);
    }

    @Override
    public void println(char x) {
        write(x + lineSeparator);
    }

    @Override
    public void println(int x) {
        write(x + lineSeparator);
    }

    @Override
    public void println(long x) {
        write(x + lineSeparator);
    }

    @Override
    public void println(float x) {
        write(x + lineSeparator);
    }

    @Override
    public void println(double x) {
        write(x + lineSeparator);
    }

    @Override
    public void println(char[] x) {
        write(String.valueOf(x) + lineSeparator);
    }

    @Override
    public void println(String x) {
        write(x + lineSeparator);
    }

    @Override
    public void println(Object x) {
        write(String.valueOf(x) + lineSeparator);
    }

    @Override
    public PrintWriter printf(String format, Object... args) {
        format(Locale.getDefault(),format,args);
        return this;
    }

    @Override
    public PrintWriter printf(Locale l, String format, Object... args) {
        format(l,format,args);
        return this;
    }

    @Override
    public PrintWriter format(String format, Object... args) {
        format(Locale.getDefault(),format,args);
        return this;
    }

    @Override
    public PrintWriter format(Locale l, String format, Object... args) {
        StringBuilder sb = new StringBuilder();
        Formatter formatter = new Formatter(sb, l);
        formatter.format(l, format, args);
        write(sb.toString());
        return this;
    }

    @Override
    public PrintWriter append(CharSequence csq) {
        if (csq == null) {
            write("null");
        } else {
            write(csq.toString());
        }
        return this;
    }

    @Override
    public PrintWriter append(CharSequence csq, int start, int end) {
        CharSequence cs = (csq == null ? "null" : csq);
        write(cs.subSequence(start, end).toString());
        return this;
    }

    @Override
    public PrintWriter append(char c) {
        write(c);
        return this;
    }
}
