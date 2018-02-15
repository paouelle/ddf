package org.codice.solr.client.solrj.response;

import java.util.List;

/** Represents a range facet result */
public interface RangeFacet<B, G> {
  public void addCount(String value, int count);

  public String getName();

  public List<Count> getCounts();

  public B getStart();

  public B getEnd();

  public G getGap();

  public Number getBefore();

  public Number getAfter();

  public Number getBetween();

  public interface Numeric extends RangeFacet<Number, Number> {}

  public interface Date extends RangeFacet<java.util.Date, String> {}

  public interface Count {

    public String getValue();

    public int getCount();

    public RangeFacet getRangeFacet();
  }
}
