package org.codice.solr.factory.impl.proxy;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.stream.Stream;

public class ProxyClass<T> {
  private final ProxyCache cache;
  private final Class<T> codiceClass;
  private final Class<?> solrClass;

  ProxyClass(ProxyCache cache, Class<T> codiceClass, Class<?> solrClass) {
    this.cache = cache;
    this.codiceClass = codiceClass;
    this.solrClass = solrClass;
    // if ()
  }

  public Class<T> getCodiceClass() {
    return codiceClass;
  }

  public Class<?> getSolrClass() {
    return solrClass;
  }

  @SuppressWarnings("unchecked")
  public T newInstance(Object target) {
    return (T)
        Proxy.newProxyInstance(
            target.getClass().getClassLoader(),
            getCodiceInterfaces(),
            new Handler(solrClass.cast(target))); // cast ensures we have a right target object
  }

  /**
   * Determines if this {@code ProxyClass} object represents an array class.
   *
   * @return <code>true</code> if this object represents an array class; <code>false</code>
   *     otherwise
   */
  public boolean isArray() {
    return false;
  }

  /**
   * Returns the {@code Class} representing the component type of an array. If this class does not
   * represent an array class this method returns null.
   *
   * @return the {@code Class} representing the component type of this class if this class is an
   *     array
   * @see java.lang.reflect.Array
   * @since JDK1.1
   */
  public ProxyClass<?> getComponentType() {
    return null;
  }

  public ProxyMethod[] getMethods() {
    return null;
  }

  @Override
  public int hashCode() {
    return codiceClass.hashCode();
  }

  @Override
  public boolean equals(Object obj) {
    if (obj == this) {
      return true;
    } else if (obj instanceof ProxyClass) {
      final ProxyClass pdef = (ProxyClass) obj;

      return codiceClass.equals(pdef.codiceClass) && solrClass.equals(solrClass);
    }
    return super.equals(obj);
  }

  @Override
  public String toString() {
    return codiceClass + " (" + solrClass + ")";
  }

  private void buildMethodMapping() {
    Stream.of(solrClass.getMethods()).map(this::findCodiceMethod);
  }

  private Method findCodiceMethod(Method m) {
    return null;
  }

  private Class<?>[] getCodiceInterfaces() {
    return new Class<?>[] {codiceClass};
  }

  class Handler implements InvocationHandler {
    private final Object target;

    Handler(Object target) {
      this.target = target;
    }

    @Override
    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
      return null;
    }
  }
}
