package org.codice.solr.client.solrj.beans;

import java.util.List;
import org.apache.solr.common.SolrInputDocument;
import org.codice.solr.common.SolrDocument;
import org.codice.solr.common.SolrDocumentList;

/** A class to map objects to and from solr documents. */
public interface DocumentObjectBinder {
  public <T> List<T> getBeans(Class<T> clazz, SolrDocumentList solrDocList);

  public <T> T getBean(Class<T> clazz, SolrDocument solrDoc);

  public SolrInputDocument toSolrInputDocument(Object obj);
}
