package org.codice.solr.common;

import java.util.Collection;

public interface SolrInputField extends Iterable<Object> {
  /**
   * Set the value for a field. Arrays will be converted to a collection. If a collection is given,
   * then that collection will be used as the backing collection for the values.
   */
  public void setValue(Object v, float b);

  /**
   * Add values to a field. If the added value is a collection, each value will be added
   * individually.
   */
  public void addValue(Object v, float b);

  public Object getFirstValue();

  /**
   * @return the value for this field. If the field has multiple values, this will be a collection.
   */
  public Object getValue();

  /**
   * @return the values for this field. This will return a collection even if the field is not
   *     multi-valued
   */
  public Collection<Object> getValues();

  /** @return the number of values for this field */
  public int getValueCount();

  /**
   * Get the field's boost.
   *
   * @deprecated Index-time boosts are deprecated. You should instead index scoring factors into a
   *     separate field and combine them with the main query's score at search time using function
   *     queries.
   */
  @Deprecated
  public float getBoost();

  /**
   * Set the field's boost.
   *
   * @deprecated Index-time boosts are deprecated. You should instead index scoring factors into a
   *     separate field and combine them with the main query's score at search time using function
   *     queries.
   */
  @Deprecated
  public void setBoost(float boost);

  public String getName();

  public void setName(String name);

  public SolrInputField deepCopy();
}
