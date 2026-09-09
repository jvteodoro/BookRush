package com.bookrush.catalog.storage;

import com.bookrush.catalog.asset.AssetService;
import com.bookrush.catalog.persistence.model.*;
import java.io.*;
import java.net.*;
import java.net.http.*;
import java.nio.file.*;
import java.time.Duration;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import com.fasterxml.jackson.databind.ObjectMapper;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.auth.credentials.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@EnabledIfEnvironmentVariable(named="STORAGE_ENABLED",matches="true")
class StorageIT {
  @Autowired ObjectStorage storage;
  @Autowired S3Client s3;
  @Autowired StorageProperties props;
  @Autowired AssetService service;
  @Autowired JdbcTemplate jdbc;
  @Autowired MockMvc mvc;
  @Autowired ObjectMapper json;
  private final String source="10000000-0000-4000-8000-000000000001";
  private UUID book() {
    UUID id=UUID.randomUUID();jdbc.update("INSERT INTO catalog.book(id,canonical_title,status) VALUES (?,'Storage test','ACTIVE')",id);return id;
  }
  private UUID license() {
    UUID id=UUID.randomUUID();jdbc.update("INSERT INTO catalog.license(id,code,name,redistribution_allowed) VALUES (?,?, 'Test only',true)",id,"TEST_"+id.toString().replace("-","").toUpperCase());return id;
  }
  private HttpResponse<byte[]> get(URI uri) throws Exception {
    return HttpClient.newHttpClient().send(HttpRequest.newBuilder(uri).GET().build(),HttpResponse.BodyHandlers.ofByteArray());
  }
  @Test void realS3RoundTripPrivateBucketsAndConditionalWrite() throws Exception {
    assertTrue(s3.listBuckets().buckets().stream().map(b->b.name()).toList().containsAll(props.buckets().all()));
    Path file=Files.createTempFile("storage-test-",".txt");Files.writeString(file,"abc");
    var loc=new ObjectStorage.Location(props.buckets().source(),"test/"+UUID.randomUUID()+"/a.txt");
    String sha="ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad";
    try {
      storage.upload(loc,file,"text/plain",sha);
      assertEquals(sha,storage.head(loc).orElseThrow().sha256());
      assertEquals(3,storage.head(loc).orElseThrow().size());
      try(var in=storage.download(loc)) {assertArrayEquals("abc".getBytes(),in.readAllBytes());}
      assertArrayEquals("abc".getBytes(),get(storage.downloadUrl(loc,"a.txt",Duration.ofSeconds(60))).body());
      assertEquals(403,get(URI.create(props.endpoint()+"/"+loc.bucket()+"/"+loc.key())).statusCode());
      for(String bucket:props.buckets().all()) assertEquals(403,get(URI.create(props.endpoint()+"/"+bucket)).statusCode());
      assertEquals(StorageFailure.Kind.CONFLICT,assertThrows(StorageFailure.class,()->storage.upload(loc,file,"text/plain",sha)).kind());
      storage.delete(loc);storage.delete(loc);
      assertTrue(storage.head(loc).isEmpty());
    } finally {storage.delete(loc);Files.deleteIfExists(file);}
  }
  @Test void administrativeApiApprovesAndDistributesWithoutProxy() throws Exception {
    UUID book=book(), license=license();
    String base="/api/admin/books/"+book+"/assets", token="Bearer "+props.adminToken();
    var file=new MockMultipartFile("file","a.txt","text/plain","abc".getBytes());
    mvc.perform(getRequest(base)).andExpect(status().isUnauthorized());
    mvc.perform(getRequest(base).header("Authorization","Bearer invalid")).andExpect(status().isUnauthorized());
    mvc.perform(getRequest(base).with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user("reader").roles("READER"))).andExpect(status().isForbidden());
    String result=mvc.perform(multipart(base).file(file).param("sourceId",source).param("licenseId",license.toString()).param("type","TXT").param("role","PUBLIC").header("Authorization",token))
        .andExpect(status().isCreated()).andExpect(jsonPath("$.version.sha256").isNotEmpty()).andReturn().getResponse().getContentAsString();
    UUID asset=UUID.fromString(json.readTree(result).path("asset").path("id").asText());
    String publicUrl="/api/books/"+book+"/assets/"+asset+"/download-url";
    mvc.perform(getRequest(publicUrl)).andExpect(status().isNotFound());
    mvc.perform(post(base+"/"+asset+"/approve").header("Authorization",token)).andExpect(status().isNoContent());
    String link=mvc.perform(getRequest(publicUrl)).andExpect(status().isOk()).andExpect(header().string("Cache-Control","no-store"))
        .andReturn().getResponse().getContentAsString();
    assertArrayEquals("abc".getBytes(),get(URI.create(json.readTree(link).path("url").asText())).body());
    mvc.perform(getRequest("/api/admin/books/"+UUID.randomUUID()+"/assets/"+asset).header("Authorization",token)).andExpect(status().isNotFound());
    mvc.perform(getRequest(base).header("Authorization",token)).andExpect(status().isOk()).andExpect(jsonPath("$[0].id").value(asset.toString()));
    mvc.perform(getRequest("/actuator/metrics/bookrush.storage").header("Authorization",token)).andExpect(status().isOk());
    mvc.perform(delete(base+"/"+asset).header("Authorization",token)).andExpect(status().isNoContent());
    mvc.perform(delete(base+"/"+asset).header("Authorization",token)).andExpect(status().isNoContent());
    mvc.perform(getRequest(publicUrl)).andExpect(status().isNotFound());
    assertEquals("DELETED",jdbc.queryForObject("SELECT status FROM catalog.book_asset WHERE id=?",String.class,asset));
  }
  private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder getRequest(String path) {return org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get(path);}
  @Test void privateRolesNeverBecomePublicAndSourceIsImmutable() throws Exception {
    UUID book=book();
    var uploaded=service.upload(book,null,null,UUID.fromString(source),license(),BookAssetAssetType.TXT,BookAssetAssetRole.SOURCE,
        "source.txt","text/plain",new ByteArrayInputStream("abc".getBytes()));
    assertThrows(org.springframework.web.server.ResponseStatusException.class,()->service.link(book,uploaded.asset().id(),true));
    assertThrows(IllegalArgumentException.class,()->service.upload(book,uploaded.asset().id(),null,null,null,BookAssetAssetType.TXT,BookAssetAssetRole.SOURCE,
        "source.txt","text/plain",new ByteArrayInputStream("abc".getBytes())));
    service.delete(book,uploaded.asset().id());
  }
  @Test void leavesCanaryForHostDownloadAndRecreation() throws Exception {
    Path file=Files.createTempFile("canary-",".txt");Files.writeString(file,"abc");
    var loc=new ObjectStorage.Location(props.buckets().source(),"test/persistence-canary.txt");
    try {storage.upload(loc,file,"text/plain","ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad");}
    finally {Files.deleteIfExists(file);}
    try(var signer=S3Presigner.builder().region(Region.of(props.region()))
        .credentialsProvider(StaticCredentialsProvider.create(AwsBasicCredentials.create(props.accessKey(),props.secretKey())))
        .serviceConfiguration(software.amazon.awssdk.services.s3.S3Configuration.builder().pathStyleAccessEnabled(true).build())
        .endpointOverride(URI.create(System.getenv("STORAGE_HOST_ENDPOINT"))).build()) {
      String url=signer.presignGetObject(r->r.signatureDuration(Duration.ofMinutes(30)).getObjectRequest(g->g.bucket(loc.bucket()).key(loc.key()))).url().toString();
      Files.writeString(Path.of("/workspace/persistence-url"),"url = \""+url+"\"\n");
    }
  }
}
