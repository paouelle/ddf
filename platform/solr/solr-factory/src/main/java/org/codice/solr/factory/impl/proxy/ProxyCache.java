package org.codice.solr.factory.impl.proxy;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.WeakHashMap;
import javax.annotation.Nullable;
import org.apache.commons.lang.ClassUtils;
import org.codice.solr.client.solrj.SolrServerException;
import org.codice.solr.common.SolrException;
import org.codice.solr.common.util.NamedList;

public class ProxyCache {
  private static final String CODICE_BASE_PACKAGE = "org.codice.solr";
  private static final int CODICE_BASE_PACKAGE_LENGTH = CODICE_BASE_PACKAGE.length();
  private static final String SOLR_BASE_PACKAGE = "org.apache.solr";
  private static final int SOLR_BASE_PACKAGE_LENGTH = SOLR_BASE_PACKAGE.length();

  private final Map<Object, Object> proxyCache = Collections.synchronizedMap(new WeakHashMap<>());
  private final Map<Class<?>, ProxyClass> classCache = Collections.synchronizedMap(new HashMap<>());

  /**
   * Generates or retrieves a suitable proxy of the given Solr class for the provided object.
   *
   * @param <T> the type for the expected class for the proxy to be returned
   * @param clazz the expected class for the proxy to be returned
   * @param obj the object for which to return a proxy
   * @return a corresponding proxy or <code>null</code> if <code>obj</code> is <code>null</code>
   * @throws IllegalArgumentException if <code>obj</code> is not a valid Solr object (i.e. defined
   *     in package org.apache.solr)
   */
  @Nullable
  @SuppressWarnings("unchecked")
  public <T> T proxy(Class<T> clazz, @Nullable Object obj) {
    if (obj == null) {
      return null;
    } else if (!isSolrObject(obj)) {
      throw new IllegalArgumentException("invalid non-Solr object");
    }
    final Object pobj = handleSpecialProxyCase(obj);

    if (pobj != null) {
      return (T) pobj;
    }
    return (T) proxyCache.computeIfAbsent(obj, getProxyClassForCodiceClass(clazz)::newInstance);
  }

  /**
   * Generates or retrieves a suitable proxy for the provided object if it is defined as a Solr
   * object otherwise returns the object as is.
   *
   * @param obj the object to proxy if it is a Solr object
   * @return <code>obj</code> or a proxy version for it if it is a Solr object
   */
  @Nullable
  public Object proxy(@Nullable Object obj) {
    if (obj == null) {
      return null;
    }
    final Object pobj = handleSpecialProxyCase(obj);

    if (pobj != null) {
      return pobj;
    }
    final ProxyClass pclass = getProxyClassNoException(obj.getClass());

    if (pclass != null) {
      return pclass.newInstance(obj);
    }
    return obj;
  }

  private Object handleSpecialProxyCase(Object obj) {
    if (obj instanceof org.apache.solr.common.SolrException) {
      final org.apache.solr.common.SolrException e = (org.apache.solr.common.SolrException) obj;

      return new SolrException(
          proxy(NamedList.class, e.getMetadata()), e.code(), e.getMessage(), e);
    } else if (obj instanceof org.apache.solr.client.solrj.SolrServerException) {
      final org.apache.solr.client.solrj.SolrServerException e =
          (org.apache.solr.client.solrj.SolrServerException) obj;

      return new SolrServerException(e.getMessage(), e);
    }
    return null;
  }

  public boolean isSolrObject(Object obj) {
    return (obj == null) || isSolrClass(obj.getClass());
  }

  public boolean isSolrClass(Class<?> clazz) {
    return ClassUtils.getPackageName(clazz).startsWith(ProxyCache.SOLR_BASE_PACKAGE);
  }

  public ProxyClass<?> getProxyClass(Class<?> clazz) throws ClassNotFoundException {
    final ProxyClass<?> pclass = getProxyClassNoException(clazz);

    if (pclass == null) {
      throw new ClassNotFoundException("missing api interface/class for: " + clazz.getName());
    }
    return pclass;
  }

  @Nullable
  private ProxyClass<?> getProxyClassNoException(Class<?> clazz) {
    synchronized (classCache) {
      ProxyClass pclass = classCache.get(clazz);

      if (pclass == null) {
        final Class<?> codiceClass = computeCodiceClass(clazz);

        if (codiceClass != null) {
          pclass = new ProxyClass(this, codiceClass, clazz);
          classCache.put(codiceClass, pclass);
        } else { // continue and walkup the hierarchy
          // check all its interfaces first
          pclass = getProxyClassFromInterfaces(clazz);
          if (pclass == null) {
            // now check its base class
            pclass = getProxyClassFromSuperclass(clazz);
          }
        }
        if (pclass != null) {
          classCache.put(clazz, pclass);
        }
      }
      return pclass;
    }
  }

  private ProxyClass<?> getProxyClassFromSuperclass(Class<?> clazz) {
    final Class<?> bclass = clazz.getSuperclass();

    if (bclass != null) {
      try {
        return getProxyClass(bclass);
      } catch (ClassNotFoundException e) { // ignore and continue with next
      }
    }
    return null;
  }

  @Nullable
  private ProxyClass<?> getProxyClassFromInterfaces(Class<?> clazz) {
    for (final Class<?> iclazz : clazz.getInterfaces()) {
      try {
        final ProxyClass<?> pclass = getProxyClass(iclazz);

        if (pclass != null) {
          classCache.put(iclazz, pclass);
          return pclass;
        }
      } catch (ClassNotFoundException e) { // ignore and continue with next
      }
    }
    return null;
  }

  //  private ProxyClass<?> getProxyClass(Set<Class<?>> subclassesAndInterfaces, Class<?> clazz) {
  //    subclassesAndInterfaces.add(clazz);
  //    // check all its interfaces first
  //    Stream.of(clazz.getInterfaces())
  //
  //  }

  public <T> ProxyClass<T> getProxyClassForCodiceClass(Class<T> clazz) {
    synchronized (classCache) {
      ProxyClass pclass = classCache.get(clazz);

      if (pclass == null) {
        pclass = new ProxyClass(this, clazz, computeSolrClass(clazz));
        classCache.put(clazz, pclass);
        classCache.put(pclass.getSolrClass(), pclass);
      }
      return pclass;
    }
  }

  @Nullable
  private Class<?> computeCodiceClass(Class<?> clazz) {
    final String cname = clazz.getName();

    if (cname.startsWith(ProxyCache.SOLR_BASE_PACKAGE)) {
      try {
        return Class.forName(
            ProxyCache.CODICE_BASE_PACKAGE + cname.substring(ProxyCache.SOLR_BASE_PACKAGE_LENGTH));
      } catch (ClassNotFoundException e) {
        return null;
      }
    }
    return null;
  }

  private Class<?> computeSolrClass(Class<?> clazz) {
    final String cname = clazz.getName();

    try {
      if (cname.startsWith(ProxyCache.CODICE_BASE_PACKAGE)) {
        return Class.forName(
            ProxyCache.SOLR_BASE_PACKAGE + cname.substring(ProxyCache.CODICE_BASE_PACKAGE_LENGTH));
      } else {
        return clazz;
      }
    } catch (ClassNotFoundException e) {
      throw new IllegalStateException("non-existent Solr class for: " + cname, e);
    }
  }
}
