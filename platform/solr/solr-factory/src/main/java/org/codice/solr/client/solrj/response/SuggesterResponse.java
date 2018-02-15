package org.codice.solr.client.solrj.response;

import java.util.List;
import java.util.Map;
import org.codice.solr.client.solrj.response.SpellCheckResponse.Suggestion;

/** Encapsulates responses from the Suggester Component */
public interface SuggesterResponse {
  /**
   * get the suggestions provided by each
   *
   * @return a Map dictionary name : List of Suggestion
   */
  public Map<String, List<Suggestion>> getSuggestions();

  /**
   * This getter is lazily initialized and returns a simplified map dictionary : List of suggested
   * terms This is useful for simple use cases when you simply need the suggested terms and no
   * weight or payload
   *
   * @return a Map dictionary name : List of suggested terms
   */
  public Map<String, List<String>> getSuggestedTerms();
}
