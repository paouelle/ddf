/**
 * Copyright (c) Codice Foundation
 *
 * <p>This is free software: you can redistribute it and/or modify it under the terms of the GNU
 * Lesser General Public License as published by the Free Software Foundation, either version 3 of
 * the License, or any later version.
 *
 * <p>This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Lesser General Public License for more details. A copy of the GNU Lesser General Public
 * License is distributed along with this program and can be found at
 * <http://www.gnu.org/licenses/lgpl.html>.
 */
package org.codice.solr.client.solrj;

import java.io.IOException;
import java.util.Date;
import java.util.List;
import org.apache.solr.common.params.FacetParams;
import org.codice.solr.client.solrj.response.QueryResponse;
import org.codice.solr.common.params.ModifiableSolrParams;

public interface SolrQuery extends ModifiableSolrParams {
  public enum ORDER {
    desc,
    asc;

    public ORDER reverse() {
      return (this == asc) ? desc : asc;
    }
  }

  /**
   * Performs the query to the Solr server.
   *
   * @param method specifies the HTTP method to use for the request, such as GET or POST
   * @return a {@link QueryResponse} containing the response from the server
   * @throws IOException If there is a low-level I/O error.
   * @throws SolrServerException if there is an error on the server
   * @throws UnavailableSolrException if the Solr server or the core is unavailable
   */
  public QueryResponse execute(SolrRequest.METHOD method) throws SolrServerException, IOException;

  /**
   * enable/disable terms.
   *
   * @param b flag to indicate terms should be enabled. <br>
   *     if b==false, removes all other terms parameters
   * @return Current reference (<i>this</i>)
   */
  public SolrQuery setTerms(boolean b);

  public boolean getTerms();

  public SolrQuery addTermsField(String field);

  public String[] getTermsFields();

  public SolrQuery setTermsLower(String lower);

  public String getTermsLower();

  public SolrQuery setTermsUpper(String upper);

  public String getTermsUpper();

  public SolrQuery setTermsUpperInclusive(boolean b);

  public boolean getTermsUpperInclusive();

  public SolrQuery setTermsLowerInclusive(boolean b);

  public boolean getTermsLowerInclusive();

  public SolrQuery setTermsLimit(int limit);

  public int getTermsLimit();

  public SolrQuery setTermsMinCount(int cnt);

  public int getTermsMinCount();

  public SolrQuery setTermsMaxCount(int cnt);

  public int getTermsMaxCount();

  public SolrQuery setTermsPrefix(String prefix);

  public String getTermsPrefix();

  public SolrQuery setTermsRaw(boolean b);

  public boolean getTermsRaw();

  public SolrQuery setTermsSortString(String type);

  public String getTermsSortString();

  public SolrQuery setTermsRegex(String regex);

  public String getTermsRegex();

  public SolrQuery setTermsRegexFlag(String flag);

  public String[] getTermsRegexFlags();

  /**
   * Add field(s) for facet computation.
   *
   * @param fields Array of field names from the IndexSchema
   * @return this
   */
  public SolrQuery addFacetField(String... fields);

  /**
   * Add field(s) for pivot computation.
   *
   * <p>pivot fields are comma separated
   *
   * @param fields Array of field names from the IndexSchema
   * @return this
   */
  public SolrQuery addFacetPivotField(String... fields);

  /**
   * Add a numeric range facet.
   *
   * @param field The field
   * @param start The start of range
   * @param end The end of the range
   * @param gap The gap between each count
   * @return this
   */
  public SolrQuery addNumericRangeFacet(String field, Number start, Number end, Number gap);

  /**
   * Add a numeric range facet.
   *
   * @param field The field
   * @param start The start of range
   * @param end The end of the range
   * @param gap The gap between each count
   * @return this
   */
  public SolrQuery addDateRangeFacet(String field, Date start, Date end, String gap);

  /**
   * Add Interval Faceting on a field. All intervals for the same field should be included in the
   * same call to this method. For syntax documentation see <a
   * href="https://wiki.apache.org/solr/SimpleFacetParameters#Interval_Faceting">Solr wiki</a>. <br>
   * Key substitution, filter exclusions or other local params on the field are not supported when
   * using this method, if this is needed, use the lower level {@link #add} method.<br>
   * Key substitution IS supported on intervals when using this method.
   *
   * @param field the field to add facet intervals. Must be an existing field and can't be null
   * @param intervals Intervals to be used for faceting. It can be an empty array, but it can't be
   *     <code>null</code>
   * @return this
   */
  public SolrQuery addIntervalFacets(String field, String[] intervals);

  /**
   * Remove all Interval Facets on a field
   *
   * @param field the field to remove from facet intervals
   * @return Array of current intervals for <code>field</code>
   */
  public String[] removeIntervalFacets(String field);

  /**
   * get the facet fields
   *
   * @return string array of facet fields or null if not set/empty
   */
  public String[] getFacetFields();

  /**
   * remove a facet field
   *
   * @param name Name of the facet field to be removed.
   * @return true, if the item was removed. <br>
   *     false, if the facet field was null or did not exist.
   */
  public boolean removeFacetField(String name);

  /**
   * enable/disable faceting.
   *
   * @param b flag to indicate faceting should be enabled. <br>
   *     if b==false, removes all other faceting parameters
   * @return Current reference (<i>this</i>)
   */
  public SolrQuery setFacet(boolean b);

  public SolrQuery setFacetPrefix(String prefix);

  public SolrQuery setFacetPrefix(String field, String prefix);

  /**
   * add a faceting query
   *
   * @param f facet query
   */
  public SolrQuery addFacetQuery(String f);

  /**
   * get facet queries
   *
   * @return all facet queries or null if not set/empty
   */
  public String[] getFacetQuery();

  /**
   * remove a facet query
   *
   * @param q the facet query to remove
   * @return true if the facet query was removed false otherwise
   */
  public boolean removeFacetQuery(String q);

  /**
   * set the facet limit
   *
   * @param lim number facet items to return
   */
  public SolrQuery setFacetLimit(int lim);

  /**
   * get current facet limit
   *
   * @return facet limit or default of 25
   */
  public int getFacetLimit();

  /**
   * set facet minimum count
   *
   * @param cnt facets having less that cnt hits will be excluded from teh facet list
   */
  public SolrQuery setFacetMinCount(int cnt);

  /**
   * get facet minimum count
   *
   * @return facet minimum count or default of 1
   */
  public int getFacetMinCount();

  /**
   * Sets facet missing boolean flag
   *
   * @param v flag to indicate the field of {@link FacetParams#FACET_MISSING} .
   * @return this
   */
  public SolrQuery setFacetMissing(Boolean v);

  /**
   * get facet sort
   *
   * @return facet sort or default of {@link FacetParams#FACET_SORT_COUNT}
   */
  public String getFacetSortString();

  /**
   * set facet sort
   *
   * @param sort sort facets
   * @return this
   */
  public SolrQuery setFacetSort(String sort);

  /**
   * add highlight field
   *
   * @param f field to enable for highlighting
   */
  public SolrQuery addHighlightField(String f);

  /**
   * remove a field for highlighting
   *
   * @param f field name to not highlight
   * @return <i>true</i>, if removed, <br>
   *     <i>false</i>, otherwise
   */
  public boolean removeHighlightField(String f);

  /**
   * get list of highlighted fields
   *
   * @return Array of highlight fields or null if not set/empty
   */
  public String[] getHighlightFields();

  public SolrQuery setHighlightSnippets(int num);

  public int getHighlightSnippets();

  public SolrQuery setHighlightFragsize(int num);

  public int getHighlightFragsize();

  public SolrQuery setHighlightRequireFieldMatch(boolean flag);

  public boolean getHighlightRequireFieldMatch();

  public SolrQuery setHighlightSimplePre(String f);

  public String getHighlightSimplePre();

  public SolrQuery setHighlightSimplePost(String f);

  public String getHighlightSimplePost();

  /**
   * Gets the raw sort field, as it will be sent to Solr.
   *
   * <p>The returned sort field will always contain a serialized version of the sort string built
   * using {@link #setSort(SolrQuery.SortClause)}, {@link #addSort(SolrQuery.SortClause)}, {@link
   * #addOrUpdateSort(SolrQuery.SortClause)}, {@link #removeSort(SolrQuery.SortClause)}, {@link
   * #clearSorts()} and {@link #setSorts(List)}.
   */
  public String getSortField();

  /**
   * Clears current sort information.
   *
   * @return the modified SolrQuery object, for easy chaining
   * @since 4.2
   */
  public SolrQuery clearSorts();

  /**
   * Replaces the current sort information.
   *
   * @return the modified SolrQuery object, for easy chaining
   * @since 4.2
   */
  public SolrQuery setSorts(List<SolrQuery.SortClause> value);

  /**
   * Gets an a list of current sort clauses.
   *
   * @return an immutable list of current sort clauses
   * @since 4.2
   */
  public List<SolrQuery.SortClause> getSorts();

  /**
   * Replaces the current sort information with a single sort clause
   *
   * @return the modified SolrQuery object, for easy chaining
   * @since 4.2
   */
  public SolrQuery setSort(String field, SolrQuery.ORDER order);

  /**
   * Replaces the current sort information with a single sort clause
   *
   * @return the modified SolrQuery object, for easy chaining
   * @since 4.2
   */
  public SolrQuery setSort(SolrQuery.SortClause sortClause);

  /**
   * Adds a single sort clause to the end of the current sort information.
   *
   * @return the modified SolrQuery object, for easy chaining
   * @since 4.2
   */
  public SolrQuery addSort(String field, SolrQuery.ORDER order);

  /**
   * Adds a single sort clause to the end of the query.
   *
   * @return the modified SolrQuery object, for easy chaining
   * @since 4.2
   */
  public SolrQuery addSort(SolrQuery.SortClause sortClause);

  /**
   * Updates or adds a single sort clause to the query. If the field is already used for sorting,
   * the order of the existing field is modified; otherwise, it is added to the end.
   *
   * <p>
   *
   * @return the modified SolrQuery object, for easy chaining
   * @since 4.2
   */
  public SolrQuery addOrUpdateSort(String field, SolrQuery.ORDER order);

  /**
   * Updates or adds a single sort field specification to the current sort information. If the sort
   * field already exist in the sort information map, its position is unchanged and the sort order
   * is set; if it does not exist, it is appended at the end with the specified order..
   *
   * @return the modified SolrQuery object, for easy chaining
   * @since 4.2
   */
  public SolrQuery addOrUpdateSort(SolrQuery.SortClause sortClause);

  /**
   * Removes a single sort field from the current sort information.
   *
   * @return the modified SolrQuery object, for easy chaining
   * @since 4.2
   */
  public SolrQuery removeSort(SolrQuery.SortClause sortClause);

  /**
   * Removes a single sort field from the current sort information.
   *
   * @return the modified SolrQuery object, for easy chaining
   * @since 4.2
   */
  public SolrQuery removeSort(String itemName);

  public void setGetFieldStatistics(boolean v);

  public void setGetFieldStatistics(String field);

  public void addGetFieldStatistics(String... field);

  public void addStatsFieldFacets(String field, String... facets);

  public void addStatsFieldCalcDistinct(String field, boolean calcDistinct);

  public SolrQuery setFilterQueries(String... fq);

  public SolrQuery addFilterQuery(String... fq);

  public boolean removeFilterQuery(String fq);

  public String[] getFilterQueries();

  public boolean getHighlight();

  public SolrQuery setHighlight(boolean b);

  /**
   * Add field for MoreLikeThis. Automatically enables MoreLikeThis.
   *
   * @param field the names of the field to be added
   * @return this
   */
  public SolrQuery addMoreLikeThisField(String field);

  public SolrQuery setMoreLikeThisFields(String... fields);

  /** @return an array with the fields used to compute similarity. */
  public String[] getMoreLikeThisFields();

  /**
   * Sets the frequency below which terms will be ignored in the source doc
   *
   * @param mintf the minimum term frequency
   * @return this
   */
  public SolrQuery setMoreLikeThisMinTermFreq(int mintf);

  /** Gets the frequency below which terms will be ignored in the source doc */
  public int getMoreLikeThisMinTermFreq();

  /**
   * Sets the frequency at which words will be ignored which do not occur in at least this many
   * docs.
   *
   * @param mindf the minimum document frequency
   * @return this
   */
  public SolrQuery setMoreLikeThisMinDocFreq(int mindf);

  /**
   * Gets the frequency at which words will be ignored which do not occur in at least this many
   * docs.
   */
  public int getMoreLikeThisMinDocFreq();

  /**
   * Sets the minimum word length below which words will be ignored.
   *
   * @param minwl the minimum word length
   * @return this
   */
  public SolrQuery setMoreLikeThisMinWordLen(int minwl);

  /** Gets the minimum word length below which words will be ignored. */
  public int getMoreLikeThisMinWordLen();

  /**
   * Sets the maximum word length above which words will be ignored.
   *
   * @param maxwl the maximum word length
   * @return this
   */
  public SolrQuery setMoreLikeThisMaxWordLen(int maxwl);

  /** Gets the maximum word length above which words will be ignored. */
  public int getMoreLikeThisMaxWordLen();

  /**
   * Sets the maximum number of query terms that will be included in any generated query.
   *
   * @param maxqt the maximum number of query terms
   * @return this
   */
  public SolrQuery setMoreLikeThisMaxQueryTerms(int maxqt);

  /** Gets the maximum number of query terms that will be included in any generated query. */
  public int getMoreLikeThisMaxQueryTerms();

  /**
   * Sets the maximum number of tokens to parse in each example doc field that is not stored with
   * TermVector support.
   *
   * @param maxntp the maximum number of tokens to parse
   * @return this
   */
  public SolrQuery setMoreLikeThisMaxTokensParsed(int maxntp);

  /**
   * Gets the maximum number of tokens to parse in each example doc field that is not stored with
   * TermVector support.
   */
  public int getMoreLikeThisMaxTokensParsed();

  /**
   * Sets if the query will be boosted by the interesting term relevance.
   *
   * @param b set to true to boost the query with the interesting term relevance
   * @return this
   */
  public SolrQuery setMoreLikeThisBoost(boolean b);

  /** Gets if the query will be boosted by the interesting term relevance. */
  public boolean getMoreLikeThisBoost();

  /**
   * Sets the query fields and their boosts using the same format as that used in
   * DisMaxQParserPlugin. These fields must also be added using {@link
   * #addMoreLikeThisField(String)}.
   *
   * @param qf the query fields
   * @return this
   */
  public SolrQuery setMoreLikeThisQF(String qf);

  /** Gets the query fields and their boosts. */
  public String getMoreLikeThisQF();

  /**
   * Sets the number of similar documents to return for each result.
   *
   * @param count the number of similar documents to return for each result
   * @return this
   */
  public SolrQuery setMoreLikeThisCount(int count);

  /** Gets the number of similar documents to return for each result. */
  public int getMoreLikeThisCount();

  /**
   * Enable/Disable MoreLikeThis. After enabling MoreLikeThis, the fields used for computing
   * similarity must be specified calling {@link #addMoreLikeThisField(String)}.
   *
   * @param b flag to indicate if MoreLikeThis should be enabled. if b==false removes all mlt.*
   *     parameters
   * @return this
   */
  public SolrQuery setMoreLikeThis(boolean b);

  /** @return true if MoreLikeThis is enabled, false otherwise */
  public boolean getMoreLikeThis();

  public SolrQuery setFields(String... fields);

  public SolrQuery addField(String field);

  public String getFields();

  public SolrQuery setIncludeScore(boolean includeScore);

  public SolrQuery setQuery(String query);

  public String getQuery();

  public SolrQuery setRows(Integer rows);

  public Integer getRows();

  public SolrQuery setShowDebugInfo(boolean showDebugInfo);

  public void setDistrib(boolean val);

  public SolrQuery setStart(Integer start);

  public Integer getStart();

  /**
   * The Request Handler to use (see the solrconfig.xml), which is stored in the "qt" parameter.
   * Normally it starts with a '/' and if so it will be used by {@link
   * org.apache.solr.client.solrj.request.QueryRequest#getPath()} in the URL instead of the "qt"
   * parameter. If this is left blank, then the default of "/select" is assumed.
   *
   * @param qt The Request Handler name corresponding to one in solrconfig.xml on the server.
   * @return this
   */
  public SolrQuery setRequestHandler(String qt);

  public String getRequestHandler();

  /**
   * @return this
   * @see ModifiableSolrParams#set(String,String[])
   */
  public SolrQuery setParam(String name, String... values);

  /**
   * @return this
   * @see org.codice.solr.common.params.ModifiableSolrParams#set(String, boolean)
   */
  public SolrQuery setParam(String name, boolean value);

  /** get a deep copy of this object * */
  public SolrQuery getCopy();

  /**
   * Set the maximum time allowed for this query. If the query takes more time than the specified
   * milliseconds, a timeout occurs and partial (or no) results may be returned.
   *
   * <p>If given Integer is null, then this parameter is removed from the request
   *
   * @param milliseconds the time in milliseconds allowed for this query
   */
  public SolrQuery setTimeAllowed(Integer milliseconds);

  /** Get the maximum time allowed for this query. */
  public Integer getTimeAllowed();

  /**
   * Creates an ascending SortClause for an item
   *
   * @param item item to sort on
   */
  public SolrQuery.SortClause create(String item, SolrQuery.ORDER order);

  /**
   * Creates a SortClause based on item and order
   *
   * @param item item to sort on
   * @param order string value for direction to sort
   */
  public SolrQuery.SortClause create(String item, String order);

  /**
   * Creates an ascending SortClause for an item
   *
   * @param item item to sort on
   */
  public SolrQuery.SortClause asc(String item);

  /**
   * Creates a decending SortClause for an item
   *
   * @param item item to sort on
   */
  public SolrQuery.SortClause desc(String item);

  /**
   * A single sort clause, encapsulating what to sort and the sort order.
   *
   * <p>The item specified can be "anything sortable" by solr; some examples include a simple field
   * name, the constant string {@code score}, and functions such as {@code sum(x_f, y_f)}.
   *
   * <p>A SortClause can be created through different mechanisms:
   *
   * <PRE><code>
   * new SortClause("product", SolrQuery.ORDER.asc);
   * new SortClause("product", "asc");
   * SortClause.asc("product");
   * SortClause.desc("product");
   * </code></PRE>
   */
  public interface SortClause extends java.io.Serializable {
    /**
     * Gets the item to sort, typically a function or a fieldname
     *
     * @return item to sort
     */
    public String getItem();

    /**
     * Gets the order to sort
     *
     * @return order to sort
     */
    public SolrQuery.ORDER getOrder();
  }
}
