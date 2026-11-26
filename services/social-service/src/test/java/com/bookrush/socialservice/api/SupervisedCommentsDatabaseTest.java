package com.bookrush.socialservice.api;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.bookrush.platform.security.SupervisedProjection;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

@EnabledIfEnvironmentVariable(named = "SOCIAL_TEST_JDBC_URL", matches = ".+")
class SupervisedCommentsDatabaseTest {
  @Test
  void upgradesExistingCommentsAndPrunesOnlyExpiredAudit() {
    var dataSource = new DriverManagerDataSource(System.getenv("SOCIAL_TEST_JDBC_URL"), "postgres", "bookrush-test");
    Flyway.configure().dataSource(dataSource).schemas("social").target("11").load().migrate();
    var db = new JdbcTemplate(dataSource);
    UUID comment = UUID.randomUUID();
    db.update("INSERT INTO social.comments(id,subject_key,book_id,body) VALUES (?,?,?,?)",
        comment, "legacy-subject", UUID.randomUUID(), "unchanged");
    var flyway = Flyway.configure().dataSource(dataSource).schemas("social").load();
    flyway.migrate();
    flyway.validate();
    flyway.migrate();
    assertEquals("unchanged", db.queryForObject("SELECT body FROM social.comments WHERE id=?", String.class, comment));

    db.update("""
        INSERT INTO social.supervised_comments_audit
            (guardian_key,target_key,member_id,scope,outcome,correlation_id,accessed_at)
        VALUES ('actor','target',?,'COMMENTS',200,'old',clock_timestamp()-interval '181 days'),
               ('actor','target',?,'COMMENTS',403,'current',clock_timestamp()-interval '179 days')
        """, UUID.randomUUID(), UUID.randomUUID());
    var projection = new SupervisedProjection(db, null, new SimpleMeterRegistry(), SupervisedProjection.Owner.SOCIAL,
        "http://localhost:1/jwks", "http://localhost:1/realm");
    projection.prune();
    assertEquals("current", db.queryForObject("SELECT correlation_id FROM social.supervised_comments_audit", String.class));
  }
}
