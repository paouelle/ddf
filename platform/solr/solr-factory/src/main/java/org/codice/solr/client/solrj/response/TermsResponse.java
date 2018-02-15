package org.codice.solr.client.solrj.response;

import java.util.List;
import java.util.Map;

/** Encapsulates responses from TermsComponent */
public interface TermsResponse {
  /**
   * Get's the term list for a given field
   *
   * @return the term list or null if no terms for the given field exist
   */
  public List<Term> getTerms(String field);

  public Map<String, List<Term>> getTermMap();

  public interface Term {
    public String getTerm();

    public void setTerm(String term);

    public long getFrequency();

    public void setFrequency(long frequency);

    public void addFrequency(long frequency);

    public long getTotalTermFreq();

    public void setTotalTermFreq(long totalTermFreq);

    public void addTotalTermFreq(long totalTermFreq);
  }
}
