package org.codice.solr.client.solrj.response;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.codice.solr.common.SolrDocumentList;
import org.codice.solr.common.util.NamedList;

public interface QueryResponse extends SolrResponseBase {
  /** Remove the field facet info */
  public void removeFacets();

  public NamedList<Object> getHeader();

  public SolrDocumentList getResults();

  public NamedList<ArrayList> getSortValues();

  public Map<String, Object> getDebugMap();

  public Map<String, String> getExplainMap();

  public Map<String, Integer> getFacetQuery();

  /**
   * @return map with each group value as key and the expanded documents that belong to the group as
   *     value. There is no guarantee on the order of the keys obtained via an iterator.
   */
  public Map<String, SolrDocumentList> getExpandedResults();

  /**
   * Returns the {@link GroupResponse} containing the group commands. A group command can be the
   * result of one of the following parameters:
   *
   * <ul>
   *   <li>group.field
   *   <li>group.func
   *   <li>group.query
   * </ul>
   *
   * @return the {@link GroupResponse} containing the group commands
   */
  public GroupResponse getGroupResponse();

  public Map<String, Map<String, List<String>>> getHighlighting();

  public SpellCheckResponse getSpellCheckResponse();

  public ClusteringResponse getClusteringResponse();

  public SuggesterResponse getSuggesterResponse();

  public TermsResponse getTermsResponse();

  public NamedList<SolrDocumentList> getMoreLikeThis();

  /** See also: {@link #getLimitingFacets()} */
  public List<FacetField> getFacetFields();

  public List<FacetField> getFacetDates();

  public List<RangeFacet> getFacetRanges();

  public NamedList<List<PivotField>> getFacetPivot();

  public List<IntervalFacet> getIntervalFacets();

  /**
   * get
   *
   * @param name the name of the
   * @return the FacetField by name or null if it does not exist
   */
  public FacetField getFacetField(String name);

  public FacetField getFacetDate(String name);

  /**
   * @return a list of FacetFields where the count is less then then #getResults() {@link
   *     SolrDocumentList#getNumFound()}
   *     <p>If you want all results exactly as returned by solr, use: {@link #getFacetFields()}
   */
  public List<FacetField> getLimitingFacets();

  public <T> List<T> getBeans(Class<T> type);

  public Map<String, FieldStatsInfo> getFieldStatsInfo();

  public String getNextCursorMark();
}
