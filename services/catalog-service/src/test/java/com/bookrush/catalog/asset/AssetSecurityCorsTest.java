package com.bookrush.catalog.asset;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

class AssetSecurityCorsTest {
  @Test
  void exposesOnlyTheBackstageOriginForInternalCatalogReads() {
    var source = new AssetSecurity().catalogCorsConfigurationSource(
        new CatalogCorsProperties(List.of("https://docs-bookrush.jteodoro.tec.br")));

    var request = new MockHttpServletRequest("OPTIONS", "/api/internal/v1/catalog/books");
    var configuration = source.getCorsConfiguration(request);

    assertThat(configuration).isNotNull();
    assertThat(configuration.getAllowedOrigins()).containsExactly("https://docs-bookrush.jteodoro.tec.br");
    assertThat(configuration.getAllowedMethods()).containsExactly("GET", "OPTIONS");
    assertThat(configuration.getAllowedHeaders()).containsExactly("Authorization", "Accept", "Content-Type");
    assertThat(configuration.getAllowCredentials()).isFalse();
  }
}
