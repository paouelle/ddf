package org.codice.solr.common;

import java.util.Collection;
import java.util.List;
import java.util.Map;

public interface SolrDocumentBase<T, K> extends Map<String, T> {
  /** Get all field names. */
  public abstract Collection<String> getFieldNames();

  /**
   * Set a field with implied null value for boost.
   *
   * @param name name of the field to set
   * @param value value of the field
   */
  public abstract void setField(String name, Object value);

  /**
   * Add a field to the document.
   *
   * @param name Name of the field, should match one of the field names defined under "fields" tag
   *     in schema.xml.
   * @param value Value of the field, should be of same class type as defined by "type" attribute of
   *     the corresponding field in schema.xml.
   */
  public abstract void addField(String name, Object value);

  /** Get the first value or collection of values for a given field. */
  public abstract Object getFieldValue(String name);

  /** Get a collection of values for a given field name */
  public abstract Collection getFieldValues(String name);

  public abstract void addChildDocument(K child);

  public abstract void addChildDocuments(Collection<K> children);

  public abstract List<K> getChildDocuments();

  public abstract boolean hasChildDocuments();

  public abstract int getChildDocumentCount();
}
