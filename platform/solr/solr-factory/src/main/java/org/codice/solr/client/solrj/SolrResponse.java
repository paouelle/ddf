package org.codice.solr.client.solrj;

import org.codice.solr.common.util.NamedList;

public interface SolrResponse {
  public long getElapsedTime();

  public void setResponse(NamedList<Object> rsp);

  public void setElapsedTime(long elapsedTime);

  public NamedList<Object> getResponse();
}
