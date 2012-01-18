package org.cloudname.codelabs;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import org.cloudname.testtools.Net;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;

/**
 * A class that has a web server responding to /info. It has a instance number that it publishes.
 * @author dybdahl
 */
public class ServerExample {
    private int port;
    private int instance;

    /**
     * Constructor
     * @param instance number for this instance.
     */
    ServerExample(int instance) {
        this.instance = instance;
    }

    /**
     * Handler for HTTP requests on /info.
     */
    class InfoHandler implements HttpHandler {
        public void handle(HttpExchange t) throws IOException {
            InputStream is = t.getRequestBody();
            String response = String.format("Port %s, instance %s", Integer.toString(port), Integer.toString(instance));
            t.sendResponseHeaders(200, response.length());
            OutputStream os = t.getResponseBody();
            os.write(response.getBytes());
            os.close();
        }
    }

    /**
     * Method to set-up and start the web server.
     * @throws IOException
     */
    public  void runServer() throws IOException {
        port = Net.getFreePort();
        System.err.println("I think that port " + Integer.toString(port) + " is free and will use it.");
        HttpServer server = HttpServer.create(new InetSocketAddress(port), 41 /*backlog*/);
        server.createContext("/info", new InfoHandler());
        server.setExecutor(null);
        server.start();
    }

    /**
     * @param args The first and only argument is the instance number.
     * @throws IOException
     */
    public static void main(String[] args) throws IOException {
        ServerExample server = new ServerExample(Integer.parseInt(args[0]));
        server.runServer();
    }
}
