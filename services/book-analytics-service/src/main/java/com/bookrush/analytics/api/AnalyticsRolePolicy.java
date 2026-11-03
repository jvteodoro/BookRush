package com.bookrush.analytics.api;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Set;

/** Maps Keycloak groups to the roles used by the analytics HTTP policy. */
final class AnalyticsRolePolicy {
  private AnalyticsRolePolicy() {}

  static Set<String> rolesFor(Collection<?> groups) {
    Set<String> roles = new LinkedHashSet<>();
    for (Object value : groups) {
      String group = String.valueOf(value);
      if ("operators".equals(group) || "platform-admins".equals(group)) {
        roles.add("ROLE_ANALYTICS_OPERATOR");
      }
      if ("excerpt-annotators".equals(group) || "developers".equals(group)) {
        roles.add("ROLE_EXCERPT_ANNOTATOR");
      }
      if ("excerpt-reviewers".equals(group) || "platform-admins".equals(group)) {
        roles.add("ROLE_EXCERPT_REVIEWER");
      }
    }
    return Set.copyOf(roles);
  }
}
