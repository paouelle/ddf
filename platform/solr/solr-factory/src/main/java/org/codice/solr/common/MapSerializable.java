package org.codice.solr.common;

import java.util.Map;

/** This is to facilitate just in time creation of objects before writing it to the response. */
public interface MapSerializable {
  /**
   * Use the passed map to minimize object creation. Do not keep a reference to the passed map and
   * reuse it. it may be reused by the framework
   */
  public Map toMap(Map<String, Object> map);
}
