package org.codice.solr.common;

import java.util.Collection;
import java.util.Iterator;
import java.util.List;

public interface SolrInputDocument
    extends SolrDocumentBase<SolrInputField, org.apache.solr.common.SolrInputDocument>,
        Iterable<SolrInputField> {

  /** Remove all fields and boosts from the document */
  @Override
  public void clear();

  /**
   * Add a field with implied null value for boost.
   *
   * <p>The class type of value and the name parameter should match schema.xml. schema.xml can be
   * found in conf directory under the solr home by default.
   *
   * @param name Name of the field, should match one of the field names defined under "fields" tag
   *     in schema.xml.
   * @param value Value of the field, should be of same class type as defined by "type" attribute of
   *     the corresponding field in schema.xml.
   * @see #addField(String, Object, float)
   */
  @Override
  public void addField(String name, Object value);

  /**
   * Get the first value for a field.
   *
   * @param name name of the field to fetch
   * @return first value of the field or null if not present
   */
  @Override
  public Object getFieldValue(String name);

  /**
   * Get all the values for a field.
   *
   * @param name name of the field to fetch
   * @return value of the field or null if not set
   */
  @Override
  public Collection<Object> getFieldValues(String name);

  /**
   * Get all field names.
   *
   * @return Set of all field names.
   */
  @Override
  public Collection<String> getFieldNames();

  /**
   * Set a field with implied null value for boost.
   *
   * @see #setField(String, Object, float)
   * @param name name of the field to set
   * @param value value of the field
   */
  @Override
  public void setField(String name, Object value);

  /**
   * Set a field value.
   *
   * @deprecated Index-time boosts are deprecated. You should instead index scoring factors into a
   *     separate field and combine them with the main query's score at search time using function
   *     queries. Use {@link #setField(String, Object)} instead.
   */
  @Deprecated
  public void setField(String name, Object value, float boost);

  /**
   * Adds a field with the given name, value and boost. If a field with the name already exists,
   * then the given value is appended to the value of that field, with the new boost. If the value
   * is a collection, then each of its values will be added to the field.
   *
   * <p>The class type of value and the name parameter should match schema.xml. schema.xml can be
   * found in conf directory under the solr home by default.
   *
   * @param name Name of the field, should match one of the field names defined under "fields" tag
   *     in schema.xml.
   * @param value Value of the field, should be of same class type as defined by "type" attribute of
   *     the corresponding field in schema.xml.
   * @param boost Boost value for the field
   * @deprecated Index-time boosts are deprecated. You should instead index scoring factors into a
   *     separate field and combine them with the main query's score at search time using function
   *     queries. Use {@link #addField(String, Object)} instead.
   */
  @Deprecated
  public void addField(String name, Object value, float boost);

  /**
   * Remove a field from the document
   *
   * @param name The field name whose field is to be removed from the document
   * @return the previous field with <tt>name</tt>, or <tt>null</tt> if there was no field for
   *     <tt>key</tt>.
   */
  public SolrInputField removeField(String name);

  public SolrInputField getField(String field);

  @Override
  public Iterator<SolrInputField> iterator();

  /**
   * Get the document boost.
   *
   * @deprecated Index-time boosts are deprecated. You should instead index scoring factors into a
   *     separate field and combine them with the main query's score at search time using function
   *     queries.
   */
  @Deprecated
  public float getDocumentBoost();

  /**
   * Set the document boost.
   *
   * @deprecated Index-time boosts are deprecated. You should instead index scoring factors into a
   *     separate field and combine them with the main query's score at search time using function
   *     queries.
   */
  @Deprecated
  public void setDocumentBoost(float documentBoost);

  public org.apache.solr.common.SolrInputDocument deepCopy();

  /** Returns the list of child documents, or null if none. */
  @Override
  public List<org.apache.solr.common.SolrInputDocument> getChildDocuments();
}
