package org.codice.solr.common.util;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.function.BiConsumer;

import org.codice.solr.common.SolrException;

/**
 * A simple container class for modeling an ordered list of name/value pairs.
 *
 * <p>Unlike Maps:
 *
 * <ul>
 *   <li>Names may be repeated
 *   <li>Order of elements is maintained
 *   <li>Elements may be accessed by numeric index
 *   <li>Names and Values can both be null
 * </ul>
 *
 * <p>A NamedList provides fast access by element number, but not by name.
 */
public interface NamedList<T> extends Cloneable, Iterable<Entry<String, T>> {
  /** The total number of name/value pairs */
  public int size();

  /**
   * The name of the pair at the specified List index
   *
   * @return null if no name exists
   */
  public String getName(int idx);

  /**
   * The value of the pair at the specified List index
   *
   * @return may be null
   */
  public T getVal(int idx);

  /** Adds a name/value pair to the end of the list. */
  public void add(String name, T val);

  /** Modifies the name of the pair at the specified index. */
  public void setName(int idx, String name);

  /**
   * Modifies the value of the pair at the specified index.
   *
   * @return the value that used to be at index
   */
  public T setVal(int idx, T val);

  /**
   * Removes the name/value pair at the specified index.
   *
   * @return the value at the index removed
   */
  public T remove(int idx);

  /**
   * Scans the list sequentially beginning at the specified index and returns the index of the first
   * pair with the specified name.
   *
   * @param name name to look for, may be null
   * @param start index to begin searching from
   * @return The index of the first matching pair, -1 if no match
   */
  public int indexOf(String name, int start);

  /**
   * Gets the value for the first instance of the specified name found.
   *
   * <p>NOTE: this runs in linear time (it scans starting at the beginning of the list until it
   * finds the first pair with the specified name).
   *
   * @return null if not found or if the value stored was null.
   * @see #indexOf
   * @see #get(String,int)
   */
  public T get(String name);

  /**
   * Gets the value for the first instance of the specified name found starting at the specified
   * index.
   *
   * <p>NOTE: this runs in linear time (it scans starting at the specified position until it finds
   * the first pair with the specified name).
   *
   * @return null if not found or if the value stored was null.
   * @see #indexOf
   */
  public T get(String name, int start);

  /**
   * Gets the values for the the specified name
   *
   * @param name Name
   * @return List of values
   */
  public List<T> getAll(String name);

  /**
   * Recursively parses the NamedList structure to arrive at a specific element. As you descend the
   * NamedList tree, the last element can be any type, including NamedList, but the previous
   * elements MUST be NamedList objects themselves. A null value is returned if the indicated
   * hierarchy doesn't exist, but NamedList allows null values so that could be the actual value at
   * the end of the path.
   *
   * <p>This method is particularly useful for parsing the response from Solr's /admin/mbeans
   * handler, but it also works for any complex structure.
   *
   * <p>Explicitly casting the return value is recommended. An even safer option is to accept the
   * return value as an object and then check its type.
   *
   * <p>Usage examples:
   *
   * <p>String coreName = (String) response.findRecursive ("solr-mbeans", "CORE", "core", "stats",
   * "coreName"); long numDoc = (long) response.findRecursive ("solr-mbeans", "CORE", "searcher",
   * "stats", "numDocs");
   *
   * @param args One or more strings specifying the tree to navigate.
   * @return the last entry in the given path hierarchy, null if not found.
   */
  public Object findRecursive(String... args);

  public NamedList<T> getImmutableCopy();

  public Map<String, T> asShallowMap();

  public Map asMap(int maxDepth);

  /** Iterates over the Map and sequentially adds its key/value pairs */
  public boolean addAll(Map<String, T> args);

  /** Appends the elements of the given NamedList to this one. */
  public boolean addAll(NamedList<T> nl);

  /** Makes a <i>shallow copy</i> of the named list. */
  public NamedList<T> clone();

  /**
   * NOTE: this runs in linear time (it scans starting at the beginning of the list until it finds
   * the first pair with the specified name).
   */
  public T remove(String name);

  /**
   * Removes and returns all values for the specified name. Returns null if no matches found. This
   * method will return all matching objects, regardless of data type. If you are parsing Solr
   * config options, the {@link #removeConfigArgs(String)} or {@link #removeBooleanArg(String)}
   * methods will probably work better.
   *
   * @param name Name
   * @return List of values
   */
  public List<T> removeAll(String name);

  /**
   * Used for getting a boolean argument from a NamedList object. If the name is not present,
   * returns null. If there is more than one value with that name, or if the value found is not a
   * Boolean or a String, throws an exception. If there is only one value present and it is a
   * Boolean or a String, the value is removed and returned as a Boolean. If an exception is thrown,
   * the NamedList is not modified. See {@link #removeAll(String)} and {@link
   * #removeConfigArgs(String)} for additional ways of gathering configuration information from a
   * NamedList.
   *
   * @param name The key to look up in the NamedList.
   * @return The boolean value found.
   * @throws SolrException If multiple values are found for the name or the value found is not a
   *     Boolean or a String.
   */
  public Boolean removeBooleanArg(String name);

  /**
   * Used for getting a boolean argument from a NamedList object. If the name is not present,
   * returns null. If there is more than one value with that name, or if the value found is not a
   * Boolean or a String, throws an exception. If there is only one value present and it is a
   * Boolean or a String, the value is returned as a Boolean. The NamedList is not modified. See
   * {@link #remove(String)}, {@link #removeAll(String)} and {@link #removeConfigArgs(String)} for
   * additional ways of gathering configuration information from a NamedList.
   *
   * @param name The key to look up in the NamedList.
   * @return The boolean value found.
   * @throws SolrException If multiple values are found for the name or the value found is not a
   *     Boolean or a String.
   */
  public Boolean getBooleanArg(String name);

  /**
   * Used for getting one or many arguments from NamedList objects that hold configuration
   * parameters. Finds all entries in the NamedList that match the given name. If they are all
   * strings or arrays of strings, remove them from the NamedList and return the individual elements
   * as a {@link Collection}. Parameter order will be preserved if the returned collection is
   * handled as an {@link ArrayList}. Throws SolrException if any of the values associated with the
   * name are not strings or arrays of strings. If exception is thrown, the NamedList is not
   * modified. Returns an empty collection if no matches found. If you need to remove and retrieve
   * all matching items from the NamedList regardless of data type, use {@link #removeAll(String)}
   * instead. The {@link #removeBooleanArg(String)} method can be used for retrieving a boolean
   * argument.
   *
   * @param name The key to look up in the NamedList.
   * @return A collection of the values found.
   * @throws SolrException If values are found for the input key that are not strings or arrays of
   *     strings.
   */
  public Collection<String> removeConfigArgs(String name) throws SolrException;

  public void clear();

  public void forEach(BiConsumer<String, T> action);
}
