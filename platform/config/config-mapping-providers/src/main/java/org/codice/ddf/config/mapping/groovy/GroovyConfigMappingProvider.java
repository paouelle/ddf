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
package org.codice.ddf.config.mapping.groovy;

import groovy.lang.Binding;
import groovy.lang.GroovyRuntimeException;
import groovy.lang.GroovyShell;
import groovy.lang.Script;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import javax.annotation.Nullable;
import org.boon.core.Value;
import org.codehaus.groovy.control.CompilerConfiguration;
import org.codehaus.groovy.control.customizers.ImportCustomizer;
import org.codice.ddf.config.Config;
import org.codice.ddf.config.ConfigService;
import org.codice.ddf.config.mapping.ConfigMapping;
import org.codice.ddf.config.mapping.ConfigMappingException;
import org.codice.ddf.config.mapping.ConfigMappingProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

class GroovyConfigMappingProvider implements ConfigMappingProvider {
  private static final Logger LOGGER = LoggerFactory.getLogger(GroovyConfigMappingProvider.class);

  // marker to indicate that a provider can provide for any instances/ids
  private static final String ANY = "*";

  /** Set of all mapping names this provider can provide for. */
  private final Set<String> names;

  /**
   * Optional set of all instances supported. Will be empty if this is not a mapping that supports
   * instances or if this provider supports all instances of that mapping.
   */
  private final Set<String> instances;

  /** Optional ranking for this provider. Defaults to 0. */
  private final int ranking;

  /** Optional set of imports to add to Groovy before compiling scripts. */
  private final Set<String> imports;

  /** Optional groovy script to evaluate first before any rules. */
  @Nullable private final String setup;

  /**
   * Set of rules. Each entry will correspond to a given entry in the provided mapped dictionary.
   * Strings are assumed to be Groovy scripts and will be evaluated whenever the mapped dictionary
   * is requested.
   */
  private final Map<String, Object> rules;

  /** Pre-compiled setup script. */
  @Nullable private GroovyScript setupScript = null;

  /** Pre-compiled cache for all rules. */
  @Nullable private Map<String, Object> cache = null;

  /**
   * Constructs a default groovy config mapping provider.
   *
   * <p><i>Note:</i> This constructor is primarly defined for Json deserialization such that we end
   * up initializing the lists with empty collections. This will be helpful, for example, in case
   * where no instance ids were serialized in which case Boon would not be setting this attribute.
   */
  GroovyConfigMappingProvider() {
    this.names = new HashSet<>();
    this.instances = new HashSet<>();
    this.ranking = 0;
    this.imports = new HashSet<>();
    this.setup = null;
    this.rules = new HashMap<>();
  }

  @Override
  public int getRanking() {
    return ranking;
  }

  @Override
  public boolean canProvideFor(ConfigMapping mapping) {
    return canProvideFor(mapping.getId());
  }

  @Override
  public boolean canProvideFor(ConfigMapping.Id id) {
    final String name = id.getName();

    if (!names.contains(name)) {
      return false;
    }
    final String instance = id.getInstance().orElse(null);

    if (instance == null) {
      // no instance defined in the id or we are looking for a provider that can support any
      if (instances.isEmpty()) {
        return true;
      } // else - we only support instances
    } else if (instances.contains(instance)
        || instances.contains(GroovyConfigMappingProvider.ANY)) {
      // we either provide any instances or the specified one
      return true;
    }
    return false;
  }

  @Override
  public Map<String, Object> provide(ConfigMapping.Id id, ConfigService config)
      throws ConfigMappingException {
    LOGGER.debug("GroovyConfigMappingProvider::provide({}, {})", id, config);
    final Binding binding = new Binding();

    binding.setVariable("name", id.getName());
    id.getInstance().ifPresent(i -> binding.setVariable("instance", i));
    binding.setVariable("config", new GroovyConfigService(config));
    binding.setVariable("env", System.getenv());
    binding.setVariable("system", System.getProperties());
    final GroovyScript setupScript;
    final Map<String, Object> properties;

    synchronized (rules) {
      if (cache == null) {
        // we are forced to pre-compile here as boon doesn't call constructors or setters to set the
        // rules; it goes through the field directly
        precompile();
      }
      properties = new HashMap<>(cache);
      setupScript = this.setupScript;
    }
    if (setupScript != null) {
      setupScript.run(id, binding);
    }
    for (Iterator<Entry<String, Object>> i = properties.entrySet().iterator(); i.hasNext(); ) {
      final Map.Entry<String, Object> e = i.next();
      Object value = e.getValue();

      if (value instanceof GroovyScript) {
        e.setValue(((GroovyScript) value).run(id, binding));
      }
    }
    LOGGER.debug("provided properties for config mapping [{}] = {}", id, properties);
    return properties;
  }

  @Override
  public int compareTo(ConfigMappingProvider provider) {
    return Integer.compare(getRanking(), provider.getRanking());
  }

  @Override
  public String toString() {
    return "GroovyConfigMappingProvider[names="
        + names
        + ", instances="
        + instances
        + ", ranking="
        + ranking
        + ", rules="
        + rules
        + "]";
  }

  private void precompile() {
    LOGGER.debug("GroovyConfigMappingProvider::precompile({})", rules);
    synchronized (rules) {
      final CompilerConfiguration compilerCfg = new CompilerConfiguration();
      final ImportCustomizer importsCustomizer = new ImportCustomizer();

      importsCustomizer.addStarImports(Config.class.getPackage().getName());
      importsCustomizer.addImports(imports.toArray(new String[imports.size()]));
      compilerCfg.addCompilationCustomizers(importsCustomizer);
      this.setupScript = precompile(setup, compilerCfg);
      this.cache = new HashMap<>(rules);
      cache.entrySet().stream().forEach(e -> e.setValue(precompile(e.getValue(), compilerCfg)));
    }
  }

  private Object precompile(@Nullable Object value, CompilerConfiguration compilerCfg) {
    // No idea why Boon serves us a Map of strings to their internal Value class instead of
    // converting those properly
    if (value instanceof Value) {
      value = ((Value) value).toValue();
    }
    if (value instanceof String) {
      return precompile((String) value, compilerCfg);
    }
    return value;
  }

  private GroovyScript precompile(@Nullable String script, CompilerConfiguration compilerCfg) {
    if (script == null) {
      return null;
    }
    try {
      return new GroovyScript(script, new GroovyShell(compilerCfg).parse(script));
    } catch (GroovyRuntimeException e) {
      LOGGER.error("failed to compile groovy script [{}]: {}", script, e.getMessage());
      LOGGER.debug("groovy script compilation failure: {}", e, e);
      throw new ConfigMappingException("invalid groovy script: " + script, e);
    }
  }

  private static class GroovyScript {
    private final String scriptString;
    private final Script script;

    GroovyScript(String scriptString, Script script) {
      this.scriptString = scriptString;
      this.script = script;
    }

    public Object run(ConfigMapping.Id id, Binding binding) {
      try {
        script.setBinding(binding);
        return script.run();
      } catch (VirtualMachineError e) {
        throw e;
      } catch (Throwable t) {
        throw new ConfigMappingException(
            "error while evaluating groovy script: " + scriptString, t);
      }
    }

    @Override
    public String toString() {
      return scriptString;
    }
  }
}
