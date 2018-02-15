package org.codice.solr.client.solrj.response;

import java.util.List;
import java.util.Map;

/** Encapsulates responses from SpellCheckComponent */
public interface SpellCheckResponse {
  public boolean isCorrectlySpelled();

  public List<Suggestion> getSuggestions();

  public Map<String, Suggestion> getSuggestionMap();

  public Suggestion getSuggestion(String token);

  public String getFirstSuggestion(String token);

  /**
   * Return the first collated query string. For convenience and backwards-compatibility. Use
   * getCollatedResults() for full data.
   *
   * @return first collated query string
   */
  public String getCollatedResult();

  /**
   * Return all collations. Will include # of hits and misspelling-to-correction details if
   * "spellcheck.collateExtendedResults was true.
   *
   * @return all collations
   */
  public List<Collation> getCollatedResults();

  public interface Suggestion {

    public String getToken();

    public int getNumFound();

    public int getStartOffset();

    public int getEndOffset();

    public int getOriginalFrequency();

    /** The list of alternatives */
    public List<String> getAlternatives();

    /**
     * The frequencies of the alternatives in the corpus, or null if this information was not
     * returned
     */
    public List<Integer> getAlternativeFrequencies();
  }

  public interface Collation {
    public long getNumberOfHits();

    public void setNumberOfHits(long numberOfHits);

    public String getCollationQueryString();

    public Collation setCollationQueryString(String collationQueryString);

    public List<Correction> getMisspellingsAndCorrections();

    public Collation addMisspellingsAndCorrection(Correction correction);
  }

  public interface Correction {

    public String getOriginal();

    public void setOriginal(String original);

    public String getCorrection();

    public void setCorrection(String correction);
  }
}
