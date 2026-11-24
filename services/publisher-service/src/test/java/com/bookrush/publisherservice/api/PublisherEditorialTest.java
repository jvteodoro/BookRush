package com.bookrush.publisherservice.api;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import com.bookrush.publisherservice.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.web.server.ResponseStatusException;

class PublisherEditorialTest {
  JdbcTemplate jdbc=mock(JdbcTemplate.class);
  PublisherIngestionClient ingestion=mock(PublisherIngestionClient.class);
  PublisherController controller=new PublisherController(jdbc,ingestion,mock(BehaviorEventPublisher.class));
  UUID id=UUID.randomUUID(), book=UUID.randomUUID();
  UsernamePasswordAuthenticationToken editor=new UsernamePasswordAuthenticationToken("editor",null,List.of(new SimpleGrantedAuthority("ROLE_platform-operators")));
  void ready() {
    when(jdbc.queryForList(contains("FOR UPDATE"),eq(id))).thenReturn(List.of(Map.of("subject_key","owner","catalog_book_id",book,"status","PROCESSING")));
    when(jdbc.queryForObject(contains("rights_attestation"),eq(Integer.class),eq(id),eq("owner"))).thenReturn(1);
  }
  @Test void approvalPromotesCatalogBeforeRecordingPublication() {
    ready();
    controller.review(id,new PublisherController.Review("APPROVED",null,null),()->"editor",editor);
    var sequence=inOrder(ingestion,jdbc);
    sequence.verify(ingestion).publication(eq(id),eq(book),any(UUID.class),eq("editor"),eq("PUBLISH"));
    sequence.verify(jdbc).update(contains("INSERT INTO publisher.published_work"),any(UUID.class),eq(id),eq(book),eq("owner"));
  }
  @Test void missingRightsCannotPromoteTheBook() {
    ready();
    when(jdbc.queryForObject(contains("rights_attestation"),eq(Integer.class),eq(id),eq("owner"))).thenReturn(0);
    assertEquals(409,assertThrows(ResponseStatusException.class,()->controller.review(id,new PublisherController.Review("APPROVED",null,null),()->"editor",editor)).getStatusCode().value());
    verifyNoInteractions(ingestion);
  }
  @Test void nonOperatorCannotApprove() {
    var owner=new UsernamePasswordAuthenticationToken("owner",null,List.of());
    assertEquals(403,assertThrows(ResponseStatusException.class,()->controller.review(id,new PublisherController.Review("APPROVED",null,null),()->"owner",owner)).getStatusCode().value());
    verifyNoInteractions(ingestion,jdbc);
  }
  @Test void remoteFailureCannotRecordLocalApproval() {
    ready();doThrow(new IllegalStateException("fixture failure")).when(ingestion).publication(any(),any(),any(),any(),any());
    assertThrows(IllegalStateException.class,()->controller.review(id,new PublisherController.Review("APPROVED",null,null),()->"editor",editor));
    verify(jdbc,never()).update(contains("published_work"),any(),any(),any(),any());
  }
  @Test void otherOwnerCannotAttestRights() {
    when(jdbc.queryForObject(anyString(),eq(String.class),eq("other"))).thenReturn("ACTIVE");
    when(jdbc.queryForList(anyString(),eq(id),eq("other"))).thenReturn(List.of());
    assertEquals(404,assertThrows(ResponseStatusException.class,()->controller.rights(id,new PublisherController.Rights("DISTRIBUTION","GLOBAL","fixture"),()->"other")).getStatusCode().value());
  }
}
