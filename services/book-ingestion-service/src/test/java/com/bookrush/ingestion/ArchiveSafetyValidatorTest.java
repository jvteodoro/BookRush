package com.bookrush.ingestion;

import static org.junit.jupiter.api.Assertions.*;

import com.bookrush.ingestion.validation.ArchiveSafetyValidator;
import java.nio.file.Files;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.junit.jupiter.api.Test;

class ArchiveSafetyValidatorTest {
  @Test void rejectsZipSlipAndUnsafeHtml() throws Exception {
    var file = Files.createTempFile("zip-slip", ".zip");
    try (var out = new ZipOutputStream(Files.newOutputStream(file))) {
      out.putNextEntry(new ZipEntry("../outside.txt")); out.write("x".getBytes()); out.closeEntry();
    }
    assertThrows(IllegalArgumentException.class, () -> new ArchiveSafetyValidator(1000, 10, 100).validateZip(file));
    assertThrows(IllegalArgumentException.class, () -> ArchiveSafetyValidator.validateHtml("<script>x</script>"));
    Files.deleteIfExists(file);
  }
}
