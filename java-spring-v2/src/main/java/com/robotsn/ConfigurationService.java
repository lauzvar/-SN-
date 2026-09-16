package com.robotsn;

import java.time.*;
import java.util.*;
import org.springframework.stereotype.Service;

@Service
public class ConfigurationService {
  private final ConfigurationServiceRepository repository;
  private final JsonSupport jsonSupport;

  public ConfigurationService(ConfigurationServiceRepository repository, JsonSupport jsonSupport) {
    this.repository = repository;
    this.jsonSupport = jsonSupport;
  }

  public Map<String, Object> setting(String key) {
    return jsonSupport.map(repository.requireSettingsForSetting(key).get("value"));
  }

  public List<?> arraySetting(String key) {
    return (List<?>) jsonSupport.decode(repository.requireSettingsForSetting(key).get("value"));
  }
}
