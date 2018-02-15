package org.codice.solr.client.solrj.request;

import org.apache.solr.common.params.ModifiableSolrParams;
import org.codice.solr.client.solrj.SolrRequest;
import org.codice.solr.client.solrj.response.UpdateResponse;

public interface AbstractUpdateRequest extends SolrRequest<UpdateResponse>, IsUpdateRequest {
  public enum ACTION {
    COMMIT,
    OPTIMIZE
  }

  /** Sets appropriate parameters for the given ACTION */
  public AbstractUpdateRequest setAction(
      AbstractUpdateRequest.ACTION action, boolean waitFlush, boolean waitSearcher);

  public AbstractUpdateRequest setAction(
      AbstractUpdateRequest.ACTION action,
      boolean waitFlush,
      boolean waitSearcher,
      boolean softCommit);

  public AbstractUpdateRequest setAction(
      AbstractUpdateRequest.ACTION action,
      boolean waitFlush,
      boolean waitSearcher,
      int maxSegments);

  public AbstractUpdateRequest setAction(
      AbstractUpdateRequest.ACTION action,
      boolean waitFlush,
      boolean waitSearcher,
      boolean softCommit,
      int maxSegments);

  public AbstractUpdateRequest setAction(
      AbstractUpdateRequest.ACTION action,
      boolean waitFlush,
      boolean waitSearcher,
      int maxSegments,
      boolean softCommit,
      boolean expungeDeletes);

  public AbstractUpdateRequest setAction(
      AbstractUpdateRequest.ACTION action,
      boolean waitFlush,
      boolean waitSearcher,
      int maxSegments,
      boolean expungeDeletes);

  public AbstractUpdateRequest setAction(
      AbstractUpdateRequest.ACTION action,
      boolean waitFlush,
      boolean waitSearcher,
      int maxSegments,
      boolean softCommit,
      boolean expungeDeletes,
      boolean openSearcher);

  public AbstractUpdateRequest rollback();

  public void setParam(String param, String value);

  /** Sets the parameters for this update request, overwriting any previous */
  public void setParams(ModifiableSolrParams params);

  public boolean isWaitSearcher();

  public AbstractUpdateRequest.ACTION getAction();

  public void setWaitSearcher(boolean waitSearcher);

  public int getCommitWithin();

  public void setCommitWithin(int commitWithin);
}
