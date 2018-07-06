/**
 * Copyright (c) Codice Foundation
 *
 * <p>This is free software: you can redistribute it and/or modify it under the terms of the GNU
 * Lesser General Public License as published by the Free Software Foundation, either version 3 of
 * the License, or any later version.
 *
 * <p>This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Lesser General Public License for more details. A copy of the GNU Lesser General Public
 * License is distributed along with this program and can be found at
 * <http://www.gnu.org/licenses/lgpl.html>.
 */
package org.codice.ddf.mapping.bundle;

import java.io.Closeable;
import java.io.IOException;
import java.net.URL;
import java.util.Dictionary;
import java.util.Enumeration;
import java.util.stream.Stream;
import org.codice.ddf.config.mapping.ConfigMappingProvider;
import org.codice.ddf.config.mapping.ConfigMappingService;
import org.codice.ddf.config.mapping.groovy.GroovyConfigMappingReader;
import org.codice.ddf.configuration.DictionaryMap;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;
import org.osgi.framework.BundleEvent;
import org.osgi.framework.Constants;
import org.osgi.framework.FrameworkUtil;
import org.osgi.framework.SynchronousBundleListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Monitors OSGI bundles in order to locate configuration mapping rules registered as resources. */
public class ConfigMappingBundleMonitor implements SynchronousBundleListener, Closeable {
  private static final Logger LOGGER = LoggerFactory.getLogger(ConfigMappingBundleMonitor.class);

  private final GroovyConfigMappingReader reader = new GroovyConfigMappingReader();

  @SuppressWarnings("unused" /* called by blueprint */)
  public void init() {
    LOGGER.debug("ConfigMappingServiceImpl::init()");
    final BundleContext context = getBundleContext();

    // start by registering a bundle listener
    context.addBundleListener(this);
    // then process all existing bundles
    Stream.of(context.getBundles())
        .filter(ConfigMappingBundleMonitor::isActiveOrStarting)
        .forEach(this::loadAndRegisterMappings);
  }

  @Override
  public void close() {
    LOGGER.debug("ConfigMappingServiceImpl::close()");
    final BundleContext context = getBundleContext();

    context.removeBundleListener(this);
  }

  BundleContext getBundleContext() {
    final Bundle bundle = FrameworkUtil.getBundle(ConfigMappingBundleMonitor.class);

    if (bundle != null) {
      return bundle.getBundleContext();
    }
    throw new IllegalStateException("missing bundle for ConfigMappingBundleMonitor");
  }

  @Override
  public void bundleChanged(BundleEvent event) {
    final Bundle bundle = event.getBundle();
    final String location = bundle.getLocation();
    final int state = bundle.getState();
    final int type = event.getType();

    LOGGER.debug(
        "ConfigMappingServiceImpl::bundleChanged() - type = [{}], bundle = [{}], state = [{}]",
        type,
        location,
        state);
    if (type == BundleEvent.STARTING) {
      loadAndRegisterMappings(bundle);
    }
  }

  private void loadAndRegisterMappings(Bundle bundle) {
    final String location = bundle.getLocation();

    LOGGER.debug("ConfigMappingServiceImpl::loadAndRegisterMappings({})", location);
    // check if we can find mappings resources for this bundle
    final Enumeration<URL> urls =
        bundle.findEntries(ConfigMappingService.MAPPINGS_DOCUMENTS_LOCATION, "*.xml", false);

    if ((urls == null) || !urls.hasMoreElements()) {
      LOGGER.debug("no mapping documents found in bundle: {}", bundle.getLocation());
      return;
    }
    final BundleContext context = bundle.getBundleContext();
    final Dictionary<String, String> headers = bundle.getHeaders();
    final String bundleName = headers.get("Bundle-Name");

    while (urls.hasMoreElements()) {
      final DictionaryMap<String, Object> props = new DictionaryMap<>(8);
      final ConfigMappingProvider provider;
      final URL url = urls.nextElement();

      try {
        provider = reader.parse(url);
        props.put(
            Constants.SERVICE_DESCRIPTION,
            String.format("[%s] Groovy Config Mapping Provider", bundleName));
        props.put(Constants.SERVICE_VENDOR, "Codice Foundation");
        props.put(Constants.SERVICE_RANKING, provider.getRank());
      } catch (IOException e) {
        LOGGER.error(
            "failed to load config mapping resource '{}' from bundle '{}': {}",
            url,
            location,
            e.getMessage());
        LOGGER.debug("loading failure: {}", e, e);
        continue;
      }
      try {
        // register the provider as a service of that bundle so it gets automatically deregister
        // when the bundle is stopped
        LOGGER.debug(
            "registering Groovy config mapping provider for resource [{}] from bundle [{}]",
            url,
            location);
        context.registerService(ConfigMappingProvider.class, provider, props);
      } catch (IllegalStateException e) {
        LOGGER.error(
            "failed to register config mapping provider service for resource '{}' in bundle '{}': {}",
            url,
            location,
            e.getMessage());
        LOGGER.debug("registration failure: {}", e, e);
      }
    }
  }

  private static boolean isActiveOrStarting(Bundle bundle) {
    return (bundle.getState() == Bundle.STARTING) || (bundle.getState() == Bundle.ACTIVE);
  }
}
