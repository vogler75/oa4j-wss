/*
    OA4J - WinCC Open Architecture for Java
    Copyright (C) 2017 Andreas Vogler

    This program is free software: you can redistribute it and/or modify
    it under the terms of the GNU Affero General Public License as
    published by the Free Software Foundation, either version 3 of the
    License, or (at your option) any later version.

    This program is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU Affero General Public License for more details.

    You should have received a copy of the GNU Affero General Public License
    along with this program.  If not, see <https://www.gnu.org/licenses/>.
*/
package wss;

import at.rocworks.oa4j.base.*;

import org.eclipse.jetty.http.HttpVersion;
import org.eclipse.jetty.server.*;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.servlet.ServletHolder;
import org.eclipse.jetty.util.ssl.SslContextFactory;

import java.util.logging.Level;

public class Server {

    public static void main(String[] args) throws Exception {
        JManager m = new JManager();
        m.init(args);
        //m.setLoopWaitUSec(1000);
        m.start();
        new Server().run();
        m.stop();
    }

    public void run() throws InterruptedException {
        wss(8080, 8443);
    }

    public void wss(int port, int sslPort) {
        org.eclipse.jetty.server.Server server = new org.eclipse.jetty.server.Server();
        ServletContextHandler context = new ServletContextHandler(ServletContextHandler.SESSIONS);
        context.setContextPath("/");
        server.setHandler(context);

        ServletHolder wsHolder = new ServletHolder("winccoa", new ServerSocketServlet());
        context.addServlet(wsHolder,"/winccoa");

        ServerConnector wsConnector = new ServerConnector(server);
        wsConnector.setPort(port);
        server.addConnector(wsConnector);


        HttpConfiguration httpConfig = new HttpConfiguration();
        httpConfig.setSecureScheme("https");
        httpConfig.setSecurePort(sslPort);

        HttpConfiguration https_config = new HttpConfiguration(httpConfig);
        https_config.addCustomizer(new SecureRequestCustomizer());

        if (sslPort>0) {
            SslContextFactory sslContextFactory = new SslContextFactory();
            sslContextFactory.setKeyStorePath("keystore.jks");
            sslContextFactory.setKeyStorePassword("OBF:1l1a1s3g1yf41xtv20731xtn1yf21s3m1kxs");
            ServerConnector wssConnector = new ServerConnector(server,
                    new SslConnectionFactory(sslContextFactory, HttpVersion.HTTP_1_1.asString()),
                    new HttpConnectionFactory(https_config));
            wssConnector.setPort(sslPort);
            server.addConnector(wssConnector);
        }

        try
        {
            server.start();
            server.join();
        }
        catch (Exception e)
        {
            JDebug.StackTrace(Level.SEVERE, e);
        }
    }
}