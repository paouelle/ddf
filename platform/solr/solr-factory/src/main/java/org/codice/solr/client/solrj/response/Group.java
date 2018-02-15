package org.codice.solr.client.solrj.response;

import org.codice.solr.common.SolrDocumentList;

/**
 * Represents a group. A group contains a common group value that all documents inside the group
 * share and documents that belong to this group.
 *
 * <p>A group value can be a field value, function result or a query string depending on the {@link
 * org.apache.solr.client.solrj.response.GroupCommand}. In case of a field value or a function
 * result the value is always a indexed value.
 */
public interface Group {
  /**
   * Returns the common group value that all documents share inside this group. This is an indexed
   * value, not a stored value.
   *
   * @return the common group value
   */
  public String getGroupValue();

  /**
   * Returns the documents to be displayed that belong to this group. How many documents are
   * returned depend on the <code>group.offset</code> and <code>group.limit</code> parameters.
   *
   * @return the documents to be displayed that belong to this group
   */
  public SolrDocumentList getResult();
}
