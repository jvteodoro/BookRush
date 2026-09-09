package com.bookrush.catalog;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(controllers=StatusController.class, properties="storage.enabled=false")
@org.springframework.context.annotation.Import(com.bookrush.catalog.asset.AssetSecurity.class)
class StatusControllerTest {
  @Autowired MockMvc mvc;
  @MockBean JdbcTemplate jdbc;

  @Test
  void reportsConnectedDatabase() throws Exception {
    when(jdbc.queryForObject("SELECT 1", Integer.class)).thenReturn(1);
    mvc.perform(get("/api/status"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.service").value("catalog-service"))
        .andExpect(jsonPath("$.database").value("up"));
  }

  @Test
  void reportsUnavailableDatabaseWithoutLeakingDetails() throws Exception {
    when(jdbc.queryForObject("SELECT 1", Integer.class))
        .thenThrow(new DataAccessResourceFailureException("private connection details"));
    mvc.perform(get("/api/status"))
        .andExpect(status().isServiceUnavailable())
        .andExpect(content().json("{\"service\":\"catalog-service\",\"status\":\"error\",\"database\":\"down\"}", true));
  }
}
