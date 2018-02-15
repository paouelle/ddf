package org.codice.solr.client.solrj.response;

import java.util.List;

public interface GroupResponse {
  /**
   * Adds a grouping command to the response.
   *
   * @param command The grouping command to add
   */
  public void add(GroupCommand command);

  /**
   * Returns all grouping commands.
   *
   * @return all grouping commands
   */
  public List<GroupCommand> getValues();
}
