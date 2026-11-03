package com.bookrush.analytics.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class AnalyticsRolePolicyTest {
  @Test
  void reviewerGroupCanAdjudicateButCannotBeConfusedWithAnnotator() {
    var roles = AnalyticsRolePolicy.rolesFor(List.of("excerpt-reviewers"));

    assertEquals(Set.of("ROLE_EXCERPT_REVIEWER"), roles);
  }

  @Test
  void platformAdminGetsOperatorAndReviewerCapabilities() {
    var roles = AnalyticsRolePolicy.rolesFor(List.of("platform-admins"));

    assertEquals(
        Set.of("ROLE_ANALYTICS_OPERATOR", "ROLE_EXCERPT_REVIEWER"),
        roles);
  }

  @Test
  void developersCanAnnotateButUnrelatedGroupsHaveNoPrivilege() {
    assertEquals(Set.of("ROLE_EXCERPT_ANNOTATOR"),
        AnalyticsRolePolicy.rolesFor(List.of("readers", "developers")));
    assertTrue(AnalyticsRolePolicy.rolesFor(List.of("readers")).isEmpty());
  }

  @Test
  void itemListQueryQualifiesJoinedColumns() {
    var sql = AnalyticsController.itemListSql(true);

    assertTrue(sql.contains("WHERE i.job_id=? AND i.status=?"));
    assertTrue(sql.contains("ORDER BY i.created_at, i.id"));
    assertTrue(sql.contains("i.error_message"));
  }
}
