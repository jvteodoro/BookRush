package com.bookrush.family;

import com.bookrush.platform.security.ProductAccessRoutes.Action;
import com.bookrush.platform.security.ProductAccessRoutes.Type;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
final class ProductAccessWorkloads {
  private record Permission(Action action, Type type) {}
  private final Map<String, Set<Permission>> grants;

  ProductAccessWorkloads(@Value("${family.product-access-workloads}") String config) {
    var configured = new HashMap<String, Set<Permission>>();
    if (!config.isBlank()) {
      for (String entry : config.split(";", -1)) {
        var parts = entry.strip().split("=", -1);
        if (parts.length != 2 || !parts[0].matches("[a-zA-Z0-9._-]+")) {
          throw new IllegalArgumentException("Invalid product access workload configuration");
        }
        var permissions = new HashSet<Permission>();
        for (String raw : parts[1].split(",", -1)) {
          String[] pair = raw.strip().split(":", -1);
          if (pair.length != 2 || !permissions.add(new Permission(Action.valueOf(pair[0]), Type.valueOf(pair[1])))) {
            throw new IllegalArgumentException("Invalid product access workload permission");
          }
        }
        if (configured.putIfAbsent(parts[0], Set.copyOf(permissions)) != null) {
          throw new IllegalArgumentException("Duplicate product access workload");
        }
      }
    }
    grants = Map.copyOf(configured);
  }

  boolean contains(String client) {
    return client != null && grants.containsKey(client);
  }

  boolean permits(String client, Action action, Type type) {
    return grants.getOrDefault(client, Set.of()).contains(new Permission(action, type));
  }
}
