package org.codice.solr.factory.impl.proxy;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;

public class ProxyHandler implements InvocationHandler {
  ProxyHandler(Class<?> proxyClass, Class<?> solrJClass, Object target) {}

  @Override
  public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
    return null;
  }
}
