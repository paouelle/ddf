package org.codice.solr.client.solrj;

import java.io.IOException;
import java.util.Collection;
import java.util.Set;
import org.codice.solr.common.params.SolrParams;
import org.codice.solr.common.util.ContentStream;

public interface SolrRequest<T extends SolrResponse> {
  public enum METHOD {
    GET,
    POST,
    PUT,
    DELETE
  }

  public SolrRequest setBasicAuthCredentials(String user, String password);

  public String getBasicAuthUser();

  public String getBasicAuthPassword();

  public METHOD getMethod();

  public void setMethod(METHOD method);

  public String getPath();

  public void setPath(String path);

  /** @return The {@link ResponseParser} */
  public ResponseParser getResponseParser();

  /**
   * Optionally specify how the Response should be parsed. Not all server implementations require a
   * ResponseParser to be specified.
   *
   * @param responseParser The {@link ResponseParser}
   */
  public void setResponseParser(ResponseParser responseParser);

  public StreamingResponseCallback getStreamingResponseCallback();

  public void setStreamingResponseCallback(StreamingResponseCallback callback);

  /** Parameter keys that are sent via the query string */
  public Set<String> getQueryParams();

  public void setQueryParams(Set<String> queryParams);

  public SolrParams getParams();

  public Collection<ContentStream> getContentStreams() throws IOException;

  /**
   * Send this request to a {@link org.apache.solr.client.solrj.SolrClient} and return the response
   *
   * @param client the SolrClient to communicate with
   * @param collection the collection to execute the request against
   * @return the response
   * @throws SolrServerException if there is an error on the Solr server
   * @throws IOException if there is a communication error
   */
  public T process(SolrClient client, String collection) throws SolrServerException, IOException;

  /**
   * Send this request to a {@link org.apache.solr.client.solrj.SolrClient} and return the response
   *
   * @param client the SolrClient to communicate with
   * @return the response
   * @throws SolrServerException if there is an error on the Solr server
   * @throws IOException if there is a communication error
   */
  public T process(SolrClient client) throws SolrServerException, IOException;
}
