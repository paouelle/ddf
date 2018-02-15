package org.codice.solr.client.solrj.response;

import java.util.List;

/** Encapsulates responses from ClusteringComponent */
public interface ClusteringResponse {
  public List<Cluster> getClusters();
}
