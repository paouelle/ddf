package org.codice.solr.common.params;

import java.util.Iterator;

/**
 * This is a simple wrapper to SolrParams that will throw a 400 exception if you ask for a parameter
 * that does not exist. Fields specified with
 *
 * <p>In short, any value you for from a <code>RequiredSolrParams</code> will return a valid
 * non-null value or throw a 400 exception. (If you pass in <code>null</code> as the default value,
 * you can get a null return value)
 */
public interface RequiredSolrParams extends SolrParams {
  /** get the param from params, fail if not found * */
  @Override
  public String get(String param);

  /** returns an Iterator over the parameter names */
  @Override
  public Iterator<String> getParameterNamesIterator();

  public void check(String... params);
}
