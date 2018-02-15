package org.codice.solr.client.solrj.response;

import java.util.List;

/**
 * This class represents the result of a group command. This can be the result of the following
 * parameter:
 *
 * <ul>
 *   <li>group.field
 *   <li>group.func
 *   <li>group.query
 * </ul>
 *
 * An instance of this class contains:
 *
 * <ul>
 *   <li>The name of this command. This can be the field, function or query grouped by.
 *   <li>The total number of documents that have matched.
 *   <li>The total number of groups that have matched.
 *   <li>The groups to be displayed. Depending on the start and rows parameter.
 * </ul>
 *
 * In case of <code>group.query</code> only one group is present and ngroups is always <code>null
 * </code>.
 */
public interface GroupCommand {
  /**
   * Returns the name of this command. This can be the field, function or query grouped by.
   *
   * @return the name of this command
   */
  public String getName();

  /**
   * Adds a group to this command.
   *
   * @param group A group to be added
   */
  public void add(Group group);

  /**
   * Returns the groups to be displayed. The number of groups returned depend on the <code>start
   * </code> and <code>rows</code> parameters.
   *
   * @return the groups to be displayed.
   */
  public List<Group> getValues();

  /**
   * Returns the total number of documents found for this command.
   *
   * @return the total number of documents found for this command.
   */
  public int getMatches();

  /**
   * Returns the total number of groups found for this command. Returns <code>null</code> if the
   * <code>group.ngroups</code> parameter is unset or <code>false</code> or if this is a group
   * command query (parameter = <code>group.query</code>).
   *
   * @return the total number of groups found for this command.
   */
  public Integer getNGroups();
}
