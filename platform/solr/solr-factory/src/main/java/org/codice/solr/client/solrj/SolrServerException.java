package org.codice.solr.client.solrj;

/**
 * Exception to catch all types of communication / parsing issues associated with talking to SOLR
 */
public class SolrServerException extends Exception {
  private static final long serialVersionUID = -1235633521777740288L;

  public SolrServerException(String message, Throwable cause) {
    super(message, cause);
  }

  public SolrServerException(String message) {
    super(message);
  }

  public SolrServerException(Throwable cause) {
    super(cause);
  }
}
