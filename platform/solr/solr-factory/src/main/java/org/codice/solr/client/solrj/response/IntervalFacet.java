package org.codice.solr.client.solrj.response;

import java.util.List;

public interface IntervalFacet {
  /** @return The field for which interval facets where calculated */
  public String getField();

  /** @return The list of interval facets calculated for {@link #getField} */
  public List<Count> getIntervals();

  /** Holds counts for facet intervals defined in a field */
  public interface Count {

    public String getKey();

    public int getCount();
  }
}
