package org.codice.solr.client.solrj;

import org.codice.solr.common.SolrDocument;

/** A callback interface for streaming response */
public interface StreamingResponseCallback {
  /*
   * Called for each SolrDocument in the response
   */
  public void streamSolrDocument(SolrDocument doc);

  /*
   * Called at the beginning of each DocList (and SolrDocumentList)
   */
  public void streamDocListInfo(long numFound, long start, Float maxScore);
}
