package com.bookrush.catalog.processing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

class ProcessingControllerTest {
  @Test
  void replayKeepsExistingChapterProjectionAndItsStableIds() {
    JdbcTemplate jdbc = org.mockito.Mockito.mock(JdbcTemplate.class);
    UUID versionId = UUID.randomUUID();
    when(jdbc.queryForList(anyString(), eq(versionId)))
        .thenReturn(List.of(Map.of("id", UUID.randomUUID())));

    var result =
        new ProcessingController(jdbc)
            .chapters(
                new ProcessingController.ChapterRequest(
                    UUID.randomUUID(),
                    UUID.randomUUID(),
                    versionId,
                    List.of(
                        new ProcessingController.Chapter(
                            "chapter-1", null, 0, "One", 0, 10, "EXPLICIT"))));

    assertThat(result)
        .containsEntry("status", "ALREADY_PROJECTED")
        .containsEntry("chapters", 1)
        .containsEntry("textAssetVersionId", versionId);
    verify(jdbc).queryForList(anyString(), eq(versionId));
    verifyNoMoreInteractions(jdbc);
  }
}
