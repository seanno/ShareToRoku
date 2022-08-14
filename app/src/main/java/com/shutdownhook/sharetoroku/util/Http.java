package com.shutdownhook.sharetoroku.util;

import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.Response;
import com.android.volley.VolleyError;
import com.android.volley.toolbox.BasicNetwork;
import com.android.volley.toolbox.HurlStack;
import com.android.volley.toolbox.NoCache;
import com.android.volley.toolbox.StringRequest;

import java.io.ByteArrayInputStream;
import java.io.Closeable;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

import org.w3c.dom.Document;

import javax.xml.parsers.DocumentBuilderFactory;

public class Http implements Closeable {

    public Http() {
        queue = new RequestQueue(new NoCache(), new BasicNetwork(new HurlStack()));
        queue.start();
    }

    @Override
    public void close() {
        queue.stop();
        queue = null;
    }

    // +---------+
    // | Strings |
    // +---------+

    public interface StringHandler {
        public void success(String response);
        public void error(Exception e);
    }

    public StringRequest get(String url, StringHandler handler) {

        StringRequest req = new StringRequest(Request.Method.GET, url,
            new Response.Listener<String>() {
                @Override public void onResponse(String s) { handler.success(s); }
            },
            new Response.ErrorListener() {
                @Override public void onErrorResponse(VolleyError e) { handler.error(e); }
            }
        );

        queue.add(req);
        return(req);
    }

    public StringRequest post(String url, StringHandler handler) {

        StringRequest req = new StringRequest(Request.Method.POST, url,
            new Response.Listener<String>() {
                @Override public void onResponse(String s) { handler.success(s); }
            },
            new Response.ErrorListener() {
                @Override public void onErrorResponse(VolleyError e) { handler.error(e); }
            }
        );

        queue.add(req);
        return(req);
    }

    // +-----+
    // | XML |
    // +-----+

    public interface XmlHandler {
        public void success(Document response);
        public void error(Exception e);
    }

    public StringRequest get(String url, XmlHandler handler) {

        StringRequest req = new StringRequest(Request.Method.GET, url,
                new Response.Listener<String>() {
                    @Override public void onResponse(String s) {
                        try { handler.success(readXml(s)); }
                        catch (Exception e) { handler.error(e); }
                    }
                },
                new Response.ErrorListener() {
                    @Override public void onErrorResponse(VolleyError e) { handler.error(e); }
                }
        );

        queue.add(req);
        return(req);
    }

    private Document readXml(String msg) throws Exception {

        ByteArrayInputStream input = null;
        Document xmlDoc = null;

        try {
            input = new ByteArrayInputStream(msg.trim().getBytes(StandardCharsets.UTF_8));

            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(true);
            xmlDoc = factory.newDocumentBuilder().parse(input);
        }
        finally {
            try { if (input != null) input.close(); }
            catch (Exception e2) { /* eat it */ }
        }

        return(xmlDoc);
    }

    // +-------------------+
    // | Helpers & Members |
    // +-------------------+

    public static String urlEncode(String input) {
        try { return(URLEncoder.encode(input, "UTF-8")); }
        catch (UnsupportedEncodingException e) { return(null); } // won't happen
    }

    private RequestQueue queue;

    private final static Loggy log = new Loggy(Http.class.getName());
}
