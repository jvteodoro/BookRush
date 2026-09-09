package com.bookrush.ingestion;

import static org.junit.jupiter.api.Assertions.*;

import com.bookrush.ingestion.source.OpenLibraryDumpParser;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.BufferedReader;
import java.io.StringReader;
import org.junit.jupiter.api.Test;

class OpenLibraryDumpParserTest {
  @Test void splitsExactlyFiveFieldsAndRetainsDeletePayload() throws Exception {
    var text = "/works/OL1W\t1\t2024\tignored\t{\"type\":{\"key\":\"/type/work\"},\"title\":\"A\\tB\"}\n"
        + "/works/OL9W\t2\t2024\tignored\t{\"type\":{\"key\":\"/type/delete\"}}\n";
    var rows = new OpenLibraryDumpParser(new ObjectMapper(), 1024).parse(new BufferedReader(new StringReader(text)), 10);
    assertEquals(2, rows.size());
    assertFalse(rows.get(0).isDelete());
    assertTrue(rows.get(1).isDelete());
  }
}
