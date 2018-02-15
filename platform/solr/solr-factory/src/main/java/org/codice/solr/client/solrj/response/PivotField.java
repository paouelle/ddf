package org.codice.solr.client.solrj.response;

import java.io.PrintStream;
import java.util.List;
import java.util.Map;
import org.apache.solr.client.solrj.response.FieldStatsInfo;
import org.apache.solr.client.solrj.response.RangeFacet;

public interface PivotField {
  public String getField();

  public Object getValue();

  public int getCount();

  public List<PivotField> getPivot();

  public Map<String, FieldStatsInfo> getFieldStatsInfo();

  public Map<String, Integer> getFacetQuery();

  public List<RangeFacet> getFacetRanges();

  public void write(PrintStream out, int indent);
}
