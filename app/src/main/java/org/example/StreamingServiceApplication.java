package org.example;

import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.servlet.ServletHolder;
import org.glassfish.jersey.servlet.ServletContainer;
import org.glassfish.jersey.server.ResourceConfig;

public class StreamingServiceApplication {
    public static void main(String[] args) throws Exception {
        // Set up Jersey resource configuration
        ResourceConfig config = new ResourceConfig();
        config.packages("org.example.api");  

        // Wrap the Jersey ServletContainer in a ServletHolder
        ServletHolder servletHolder = new ServletHolder(new ServletContainer(config));

        // Set up Jetty server with Jersey servlet
        ServletContextHandler context = new ServletContextHandler(ServletContextHandler.SESSIONS);
        context.setContextPath("/");
        context.addServlet(servletHolder, "/*");

        Server server = new Server(8080);
        server.setHandler(context);
        

        // Start the Jetty server
        server.start();
        System.out.println("Server started on port 8080");
        server.join();
    }
}
