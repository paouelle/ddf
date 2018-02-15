package org.codice.solr.client.solrj.response;

import java.util.Collection;
import java.util.List;
import java.util.Map;

public interface FieldStatsInfo {
  public String getName();

  public Object getMin();

  public Object getMax();

  public Object getSum();

  public Long getCount();

  public Long getCountDistinct();

  public Collection<Object> getDistinctValues();

  public Long getMissing();

  public Object getMean();

  public Double getStddev();

  public Double getSumOfSquares();

  public Map<String, List<FieldStatsInfo>> getFacets();

  /**
   * The percentiles requested if any, otherwise null. If non-null then the iteration order will
   * match the order the percentiles were originally specified in.
   */
  public Map<Double, Double> getPercentiles();
  /** The cardinality of of the set of values if requested, otherwise null. */
  public Long getCardinality();
}
