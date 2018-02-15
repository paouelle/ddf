package org.codice.solr.client.solrj;

import java.io.InputStream;
import java.io.Reader;
import org.codice.solr.common.util.NamedList;

public interface ResponseParser {
  public String getWriterType(); // for example: wt=XML, JSON, etc

  public NamedList<Object> processResponse(InputStream body, String encoding);

  public abstract NamedList<Object> processResponse(Reader reader);

  /**
   * A well behaved ResponseParser will return its content-type.
   *
   * @return the content-type this parser expects to parse
   */
  public String getContentType();

  /** @return the version param passed to solr */
  public String getVersion();
}
