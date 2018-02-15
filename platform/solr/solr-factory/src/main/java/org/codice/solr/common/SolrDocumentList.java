package org.codice.solr.common;

import java.util.List;

/**
 * Represent a list of SolrDocuments returned from a search. This includes position and offset
 * information.
 *
 * @since solr 1.3
 */
public interface SolrDocumentList extends List<SolrDocument> {
  public Float getMaxScore();

  public void setMaxScore(Float maxScore);

  public long getNumFound();

  public void setNumFound(long numFound);

  public long getStart();

  public void setStart(long start);
}
