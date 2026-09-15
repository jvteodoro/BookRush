package com.bookrush.analytics.models;
import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.*;
import java.security.*;
import java.util.HexFormat;
import org.junit.jupiter.api.Test;
class ModelArtifactManagerTest {
 @Test void verifiesChecksumAndStreamsFile() throws Exception { Path d=Files.createTempDirectory("models"); Path p=d.resolve("model.bin"); Files.writeString(p,"fixture"); String h=HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest("fixture".getBytes())); assertEquals(h,new ModelArtifactManager(d,true).require("fixture","model.bin",h).sha256()); assertThrows(IllegalStateException.class,()->new ModelArtifactManager(d,true).require("fixture","model.bin","0".repeat(64))); }
 @Test void missingOfflineArtifactIsExplicit() throws Exception { assertTrue(assertThrows(IllegalStateException.class,()->new ModelArtifactManager(Files.createTempDirectory("models"),true).require("bge","model.bin",null)).getMessage().contains("ANALYTICS_OFFLINE")); }
}
