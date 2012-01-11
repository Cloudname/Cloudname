package org.cloudname.testtools;

import java.net.URL;
import java.net.URLConnection;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.IOException;

import java.net.MalformedURLException;

/**
 * Simple tools for use in HTTP tests.  We could have used some fancy
 * HTTP client library, but since this is for testing only we will
 * make do with what the standard library offers.
 *
 * @author borud
 */
public class Http {
    private static final int HTTP_BUFFER_LEN = 10 * 1024;

    /**
     * Fetch an URL using HTTP GET and return the contents as a
     * String.
     *
     * @param url the URL we want to GET.
     *
     * @throws MalformedURLException if the URL is invalid.
     * @throws IOException if an IO error occurs.
     *
     * @return the content of the URL as a String.
     */
    public static String doGet(String urlString) throws MalformedURLException, IOException {
        URL url = new URL(urlString);
        URLConnection urlConnection = url.openConnection();
        urlConnection.setUseCaches(false);
        urlConnection.connect();

        InputStreamReader in = null;
        try {
            in = new InputStreamReader(urlConnection.getInputStream());
            char[] cbuf = new char[HTTP_BUFFER_LEN];
            StringBuilder buff = new StringBuilder();

            int readLen = 0;
            while((readLen = in.read(cbuf, 0, HTTP_BUFFER_LEN)) > 0) {
                buff.append(cbuf, 0, readLen);
            }

            return buff.toString();
        } finally {
            in.close();
        }
    }
}