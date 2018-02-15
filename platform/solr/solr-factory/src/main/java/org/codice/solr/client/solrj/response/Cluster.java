package org.codice.solr.client.solrj.response;

import java.util.List;

/**
 * This class represents a cluster of Solr Docs . The cluster is produced from a set of Solr
 * documents from the results. It is a direct mapping for the Json object Solr is returning.
 */
public interface Cluster {
  public List<String> getLabels();

  public void setLabels(List<String> labels);

  public double getScore();

  public void setScore(double score);

  public List<String> getDocs();

  public void setDocs(List<String> docIds);

  public List<org.apache.solr.client.solrj.response.Cluster> getSubclusters();

  /**
   * @return If <code>true</code>, the cluster contains references to documents that are not
   *     semantically associated and form a group of documents not related to any other cluster (or
   *     themselves).
   */
  public boolean isOtherTopics();
}
