package org.codice.solr.client.solrj.response;

import java.util.List;

/**
 * A utility class to hold the facet response. It could use the NamedList container, but for JSTL,
 * it is nice to have something that implements List so it can be iterated
 */
public interface FacetField {
  public interface Count {
    public String getName();

    public void setName(String n);

    public long getCount();

    public void setCount(long c);

    public FacetField getFacetField();

    public String getAsFilterQuery();
  }

  /** Insert at the end of the list */
  public void add(String name, long cnt);

  /** Insert at the beginning of the list. */
  public void insert(String name, long cnt);

  public String getName();

  public List<Count> getValues();

  public int getValueCount();

  public FacetField getLimitingFields(long max);
}
