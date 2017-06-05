package org.codice.solr.server;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.apache.commons.exec.CommandLine;
import org.apache.commons.exec.DefaultExecutor;
//import org.slf4j.Logger;
//import org.slf4j.LoggerFactory;

public class SolrServer {
    //private static final Logger LOGGER = LoggerFactory.getLogger(SolrServer.class);

    private final boolean standalone;

    public SolrServer() {
        //LOGGER.warn("*** SolrServer()");
        System.out.println("*** SolrServer()");
        final String clientType = System.getProperty("solr.client", "HttpSolrClient");

        if ("EmbeddedSolrServer".equals(clientType)) {
            this.standalone = false;
        } else if ("CloudSolrClient".equals(clientType)) {
            this.standalone = false;
        } else {
            this.standalone = true;
        }
        //LOGGER.info("Solr client = {}, standalone = {}", clientType, standalone);
        System.out.println("*** SolrServer: client=" + clientType + ", standalone=" + standalone);
    }

    public void init() throws IOException {
        //LOGGER.warn("*** SolrServer.init()");
        System.out.println("*** SolrServer.init()");
        if (standalone) {
            //LOGGER.info("starting up standalone Solr server");
            System.out.println("*** SolrServer.init() - starting standalone Solr server");
            final Path ddfHome = Paths.get(System.getProperty("ddf.home"));
            final CommandLine cmd = new CommandLine(ddfHome + "/solr/bin/solr");

            cmd.addArgument("start")
                    .addArgument("-p")
                    .addArgument("8984");
            final DefaultExecutor exec = new DefaultExecutor();

            exec.setExitValue(0);
            try {
                exec.execute(cmd);
            } catch (IOException e) {
                //LOGGER.error("failed to start standalone Solr server", e);
                System.out.println("*** SolrServer.init() - failed to start Solr server; " + e);
                throw e;
            }
        }
    }

    public void destroy() {
        //LOGGER.warn("*** SolrServer.destroy()");
        System.out.println("*** SolrServer.init()");
    }
}