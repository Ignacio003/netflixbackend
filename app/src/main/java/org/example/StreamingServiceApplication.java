package org.example;

import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.servlet.ServletHolder;
import org.glassfish.jersey.servlet.ServletContainer;
import org.glassfish.jersey.server.ResourceConfig;

public class StreamingServiceApplication {
    public static void main(String[] args) throws Exception {
        ResourceConfig config = new ResourceConfig();
        config.packages("org.example.api");  

        ServletHolder servletHolder = new ServletHolder(new ServletContainer(config));

        ServletContextHandler context = new ServletContextHandler(ServletContextHandler.SESSIONS);
        context.setContextPath("/");
        context.addServlet(servletHolder, "/*");

        Server server = new Server(8080);
        server.setHandler(context);
        
        server.start();
        System.out.println("Server started on port 8080");
        server.join();
    }
}
