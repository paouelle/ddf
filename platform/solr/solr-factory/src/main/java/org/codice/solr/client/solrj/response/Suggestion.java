package org.codice.solr.client.solrj.response;

public interface Suggestion {
  public String getTerm();

  public long getWeight();

  public String getPayload();
}
