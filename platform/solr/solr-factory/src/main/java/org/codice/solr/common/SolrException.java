package org.codice.solr.common;

import org.apache.commons.lang.Validate;
import org.codice.solr.common.util.NamedList;

public class SolrException extends RuntimeException {
  public static final String ROOT_ERROR_CLASS = "root-error-class";
  public static final String ERROR_CLASS = "error-class";

  private final NamedList<String> metadata;
  private final int code;

  public SolrException(NamedList<String> metadata, int code, String msg) {
    super(msg);
    Validate.notNull(metadata, "invalid null metadata");
    this.metadata = metadata;
    this.code = code;
  }

  public SolrException(NamedList<String> metadata, int code, String msg, Throwable th) {
    super(msg, th);
    Validate.notNull(metadata, "invalid null metadata");
    this.metadata = metadata;
    this.code = code;
  }

  public SolrException(NamedList<String> metadata, int code, Throwable th) {
    super(th);
    Validate.notNull(metadata, "invalid null metadata");
    this.metadata = metadata;
    this.code = code;
  }

  /**
   * The HTTP Status code associated with this Exception. For SolrExceptions thrown by Solr "Server
   * Side", this should valid {@link org.apache.solr.common.SolrException.ErrorCode}, however client
   * side exceptions may contain an arbitrary error code based on the behavior of the Servlet
   * Container hosting Solr, or any HTTP Proxies that may exist between the client and the server.
   *
   * @return The HTTP Status code associated with this Exception
   */
  public int code() {
    return code;
  }

  public NamedList<String> getMetadata() {
    return metadata;
  }

  public String getMetadata(String key) {
    return (metadata != null && key != null) ? metadata.get(key) : null;
  }

  public void setMetadata(String key, String value) {
    if (key == null || value == null) {
      throw new IllegalArgumentException("Exception metadata cannot be null!");
    }
    metadata.add(key, value);
  }

  public String getThrowable() {
    return getMetadata(ERROR_CLASS);
  }

  public String getRootThrowable() {
    return getMetadata(ROOT_ERROR_CLASS);
  }
}
