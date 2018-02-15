package org.codice.solr.common.params;

import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import org.codice.solr.common.MapSerializable;
import org.codice.solr.common.util.NamedList;

/** SolrParams hold request parameters. */
public interface SolrParams extends MapSerializable {

  /** returns the String value of a param, or null if not set */
  public String get(String param);

  /** returns an array of the String values of a param, or null if none */
  public String[] getParams(String param);

  /** returns an Iterator over the parameter names */
  public Iterator<String> getParameterNamesIterator();

  /** returns the value of the param, or def if not set */
  public String get(String param, String def);

  /** returns a RequiredSolrParams wrapping this */
  public RequiredSolrParams required();

  /**
   * returns the String value of the field parameter, "f.field.param", or the value for "param" if
   * that is not set.
   */
  public String getFieldParam(String field, String param);

  /**
   * returns the String value of the field parameter, "f.field.param", or the value for "param" if
   * that is not set. If that is not set, def
   */
  public String getFieldParam(String field, String param, String def);

  /**
   * returns the String values of the field parameter, "f.field.param", or the values for "param" if
   * that is not set.
   */
  public String[] getFieldParams(String field, String param);

  /**
   * Returns the Boolean value of the param, or null if not set. Use this method only when you want
   * to be explicit about absence of a value (<code>null</code>) vs the default value <code>false
   * </code>.
   *
   * @see #getBool(String, boolean)
   * @see #getPrimitiveBool(String)
   */
  public Boolean getBool(String param);

  /** Returns the boolean value of the param, or <code>false</code> if not set */
  public boolean getPrimitiveBool(String param);

  /** Returns the boolean value of the param, or def if not set */
  public boolean getBool(String param, boolean def);

  /**
   * Returns the Boolean value of the field param, or the value for param, or null if neither is
   * set. Use this method only when you want to be explicit about absence of a value (<code>null
   * </code>) vs the default value <code>false</code>.
   *
   * @see #getFieldBool(String, String, boolean)
   * @see #getPrimitiveFieldBool(String, String)
   */
  public Boolean getFieldBool(String field, String param);

  /**
   * Returns the boolean value of the field param, or the value for param or the default value of
   * boolean - <code>false</code>
   */
  public boolean getPrimitiveFieldBool(String field, String param);

  /**
   * Returns the boolean value of the field param, or the value for param, or def if neither is set.
   */
  public boolean getFieldBool(String field, String param, boolean def);

  /**
   * Returns the Integer value of the param, or null if not set Use this method only when you want
   * to be explicit about absence of a value (<code>null</code>) vs the default value for int - zero
   * (<code>0</code>).
   *
   * @see #getInt(String, int)
   * @see #getPrimitiveInt(String)
   */
  public Integer getInt(String param);

  /**
   * Returns int value of the the param or default value for int - zero (<code>0</code>) if not set.
   */
  public int getPrimitiveInt(String param);

  /** Returns the int value of the param, or def if not set */
  public int getInt(String param, int def);

  /**
   * Returns the Long value of the param, or null if not set Use this method only when you want to
   * be explicit about absence of a value (<code>null</code>) vs the default value zero (<code>0
   * </code>).
   *
   * @see #getLong(String, long)
   */
  public Long getLong(String param);

  /** Returns the long value of the param, or def if not set */
  public long getLong(String param, long def);

  /**
   * Use this method only when you want to be explicit about absence of a value (<code>null</code>)
   * vs the default value zero (<code>0</code>).
   *
   * @return The int value of the field param, or the value for param or <code>null</code> if
   *     neither is set.
   * @see #getFieldInt(String, String, int)
   */
  public Integer getFieldInt(String field, String param);

  /** Returns the int value of the field param, or the value for param, or def if neither is set. */
  public int getFieldInt(String field, String param, int def);

  /**
   * Returns the Float value of the param, or null if not set Use this method only when you want to
   * be explicit about absence of a value (<code>null</code>) vs the default value zero (<code>0.0f
   * </code>).
   *
   * @see #getFloat(String, float)
   */
  public Float getFloat(String param);

  /** Returns the float value of the param, or def if not set */
  public float getFloat(String param, float def);

  /**
   * Returns the Float value of the param, or null if not set Use this method only when you want to
   * be explicit about absence of a value (<code>null</code>) vs the default value zero (<code>0.0d
   * </code>).
   *
   * @see #getDouble(String, double)
   */
  public Double getDouble(String param);

  /** Returns the float value of the param, or def if not set */
  public double getDouble(String param, double def);

  /**
   * Returns the float value of the field param. Use this method only when you want to be explicit
   * about absence of a value (<code>null</code>) vs the default value zero (<code>0.0f</code>).
   *
   * @see #getFieldFloat(String, String, float)
   * @see #getPrimitiveFieldFloat(String, String)
   */
  public Float getFieldFloat(String field, String param);

  /**
   * Returns the float value of the field param or the value for param or the default value for
   * float - zero (<code>0.0f</code>)
   */
  public float getPrimitiveFieldFloat(String field, String param);

  /**
   * Returns the float value of the field param, or the value for param, or def if neither is set.
   */
  public float getFieldFloat(String field, String param, float def);

  /**
   * Returns the float value of the field param. Use this method only when you want to be explicit
   * about absence of a value (<code>null</code>) vs the default value zero (<code>0.0d</code>).
   *
   * @see #getDouble(String, double)
   */
  public Double getFieldDouble(String field, String param);

  /**
   * Returns the float value of the field param, or the value for param, or def if neither is set.
   */
  public double getFieldDouble(String field, String param, double def);
  /** Create filtered SolrParams. */
  public SolrParams toFilteredSolrParams(List<String> names);

  /** Convert this to a NamedList */
  public NamedList<Object> toNamedList();

  public Map<String, Object> getAll(Map<String, Object> sink, Collection<String> params);

  /** Copy all params to the given map or if the given map is null create a new one */
  public Map<String, Object> getAll(Map<String, Object> sink, String... params);

  /**
   * Returns this SolrParams as a properly URL encoded string, starting with {@code "?"}, if not
   * empty.
   */
  public String toQueryString();

  /**
   * Generates a local-params string of the form
   *
   * <pre>{! name=value name2=value2}</pre>
   *
   * .
   */
  public String toLocalParamsString();

  /**
   * Like {@link #toQueryString()}, but only replacing enough chars so that the URL may be
   * unambiguously pasted back into a browser. This method can be used to properly log query
   * parameters without making them unreadable.
   *
   * <p>Characters with a numeric value less than 32 are encoded. &amp;,=,%,+,space are encoded.
   */
  @Override
  public String toString();
}
