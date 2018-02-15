package org.codice.solr.client.solrj.response;

import org.apache.solr.common.util.NamedList;
import org.codice.solr.client.solrj.SolrResponse;

public interface SolrResponseBase extends SolrResponse {
  public NamedList getResponseHeader();

  public int getStatus();

  public int getQTime();

  public String getRequestUrl();

  public void setRequestUrl(String requestUrl);
}
