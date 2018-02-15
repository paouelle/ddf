package org.codice.solr.common;

import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * A concrete representation of a document within a Solr index. Unlike a lucene Document, a
 * SolrDocument may have an Object value matching the type defined in schema.xml
 *
 * <p>For indexing documents, use the SolrInputDocument that contains extra information for document
 * and field boosting.
 */
public interface SolrDocument
    extends SolrDocumentBase<Object, SolrDocument>, Iterable<Map.Entry<String, Object>> {
  /**
   * @return a list of field names defined in this document - this Collection is directly backed by
   *     this SolrDocument.
   * @see #keySet
   */
  @Override
  public Collection<String> getFieldNames();

  /** Remove all fields from the document */
  @Override
  public void clear();

  /** Remove all fields with the name */
  public boolean removeFields(String name);

  /**
   * Set a field with the given object. If the object is an Array, it will set multiple fields with
   * the included contents. This will replace any existing field with the given name
   */
  @Override
  public void setField(String name, Object value);

  /**
   * This will add a field to the document. If fields already exist with this name it will append
   * value to the collection. If the value is Collection, each value will be added independently.
   *
   * <p>The class type of value and the name parameter should match schema.xml. schema.xml can be
   * found in conf directory under the solr home by default.
   *
   * @param name Name of the field, should match one of the field names defined under "fields" tag
   *     in schema.xml.
   * @param value Value of the field, should be of same class type as defined by "type" attribute of
   *     the corresponding field in schema.xml.
   */
  @Override
  public void addField(String name, Object value);

  /** returns the first value for a field */
  public Object getFirstValue(String name);

  /** Get the value or collection of values for a given field. */
  @Override
  public Object getFieldValue(String name);

  /** Get a collection of values for a given field name */
  @Override
  public Collection<Object> getFieldValues(String name);

  /** Iterate of String-&gt;Object keys */
  @Override
  public Iterator<Entry<String, Object>> iterator();

  /** Expose a Map interface to the solr field value collection. */
  public Map<String, Collection<Object>> getFieldValuesMap();

  /** Expose a Map interface to the solr fields. This function is useful for JSTL */
  public Map<String, Object> getFieldValueMap();

  /** Returns the list of child documents, or null if none. */
  @Override
  public List<SolrDocument> getChildDocuments();
}
