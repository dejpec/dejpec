
import org.eclipse.jetty.proxy.ProxyServlet;
import org.eclipse.jetty.server.Connector;
import org.eclipse.jetty.server.HttpConfiguration;
import org.eclipse.jetty.server.HttpConnectionFactory;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;
import org.eclipse.jetty.servlet.ServletContextHandler;

import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

public class Expect100Timeout {

    public static class TestServlet extends HttpServlet {
        @Override
        protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws IOException {
            //reading all bytes binary works
            //req.getInputStream().readAllBytes();
            //reading all lines will time out
            req.getReader().lines().forEach(s -> {});
            resp.getWriter().println("OK");
            resp.getWriter().flush();
        }
    }

    public static void main(String[] args) throws Exception {
        Server testServletServer = new Server();
        testServletServer.setConnectors(new Connector[]{new ServerConnector(testServletServer, new HttpConnectionFactory(new HttpConfiguration()))});
        ServletContextHandler testServletContext = new ServletContextHandler();
        testServletContext.addServlet(TestServlet.class.getName(), "/*");
        testServletServer.setHandler(testServletContext);
        testServletServer.start();
        try {
            Server proxyServer = new Server();
            proxyServer.setConnectors(new Connector[]{new ServerConnector(proxyServer, new HttpConnectionFactory(new HttpConfiguration()))});
            ServletContextHandler proxyContext = new ServletContextHandler();
            proxyContext.addServlet(ProxyServlet.Transparent.class.getName(), "/*").setInitParameter("proxyTo", "http://localhost:" + testServletServer.getURI().getPort());
            proxyServer.setHandler(proxyContext);
            proxyServer.start();
            try {
                int port = proxyServer.getURI().getPort();
                //int port = testServletServer.getURI().getPort();
                sendPost(port);
                sendPost(port);
            } finally {
                proxyServer.stop();
            }
        } finally {
            testServletServer.stop();
        }
    }

    private static void sendPost(int port) throws IOException {
        final String urlString = "http://localhost:" + port;
        URL url = new URL(urlString);
        HttpURLConnection hc = (HttpURLConnection) url.openConnection();
        hc.setDoOutput(true);
        hc.setRequestMethod("POST");
        hc.setRequestProperty("Expect", "100-continue");
        byte[] buffer = "1234\n5678\n\n".getBytes(StandardCharsets.UTF_8);
        hc.setFixedLengthStreamingMode(buffer.length);
        OutputStream out = hc.getOutputStream();
        out.write(buffer);
        out.flush();
        int status = hc.getResponseCode();
        String result;
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(hc.getInputStream(), StandardCharsets.UTF_8))) {
            result = reader.readLine();
        }
        System.out.println("Post " + urlString + " Status: " + status + " Result: " + result);
    }
}
