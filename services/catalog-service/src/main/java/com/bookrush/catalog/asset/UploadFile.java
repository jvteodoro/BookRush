package com.bookrush.catalog.asset;

import com.bookrush.catalog.persistence.model.BookAssetAssetType;
import com.bookrush.catalog.storage.ObjectKeyBuilder;
import com.fasterxml.jackson.core.JsonFactory;
import java.io.*;
import java.nio.charset.*;
import java.nio.file.*;
import java.security.*;
import java.util.*;
import java.util.zip.ZipFile;

/** Bounded temporary spool, content-derived MIME and server-calculated SHA-256. Caller closes to erase spool. */
public record UploadFile(Path path,String filename,String contentType,long size,String sha256) implements AutoCloseable {
  public static UploadFile read(InputStream input,String filename,String declared,BookAssetAssetType type,long limit) throws IOException {
    ObjectKeyBuilder.filename(filename);
    Path path=Files.createTempFile("bookrush-upload-",".tmp");
    try {
      MessageDigest digest;
      try { digest=MessageDigest.getInstance("SHA-256"); } catch(NoSuchAlgorithmException e) { throw new IllegalStateException(e); }
      long size=0;
      try(OutputStream out=Files.newOutputStream(path)) {
        byte[] buf=new byte[8192]; int count;
        while((count=input.read(buf))!=-1) {
          size+=count; if(size>limit) throw new IllegalArgumentException("File exceeds configured limit");
          digest.update(buf,0,count); out.write(buf,0,count);
        }
      }
      if(size==0) throw new IllegalArgumentException("Empty file");
      String mime=detect(path,type);
      if(declared!=null && !declared.isBlank() && !declared.split(";")[0].trim().equalsIgnoreCase(mime))
        throw new IllegalArgumentException("Declared MIME does not match file content");
      return new UploadFile(path,filename,mime,size,HexFormat.of().formatHex(digest.digest()));
    } catch(Exception e) { Files.deleteIfExists(path); throw e; }
  }
  private static String detect(Path path,BookAssetAssetType type) throws IOException {
    byte[] header; try(var in=Files.newInputStream(path)) { header=in.readNBytes(32); }
    String ascii=new String(header,StandardCharsets.ISO_8859_1);
    return switch(type) {
      case EPUB -> {
        try(ZipFile zip=new ZipFile(path.toFile())) {
          var entry=zip.getEntry("mimetype");
          if(entry==null) throw new IllegalArgumentException("Invalid EPUB container");
          try(var in=zip.getInputStream(entry)) {
            if(!new String(in.readNBytes(64),StandardCharsets.US_ASCII).equals("application/epub+zip"))
              throw new IllegalArgumentException("Invalid EPUB mimetype");
          }
          if(zip.getEntry("META-INF/container.xml")==null) throw new IllegalArgumentException("Invalid EPUB container");
        }
        yield "application/epub+zip";
      }
      case PDF -> { require(ascii.startsWith("%PDF-")); yield "application/pdf"; }
      case TXT, HTML -> {
        var decoder=StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT);
        try(var reader=new InputStreamReader(Files.newInputStream(path),decoder)) {
          char[] chars=new char[8192]; int count;
          while((count=reader.read(chars))!=-1) for(int i=0;i<count;i++)
            if(chars[i]==0) throw new IllegalArgumentException("Binary content is not text");
        }
        if(type==BookAssetAssetType.HTML) require(ascii.stripLeading().toLowerCase(Locale.ROOT).startsWith("<"));
        yield type==BookAssetAssetType.TXT ? "text/plain" : "text/html";
      }
      case JSON -> {
        try(var parser=new JsonFactory().createParser(path.toFile())) {
          if(parser.nextToken()==null) throw new IllegalArgumentException("Empty JSON");
          parser.skipChildren();
          if(parser.nextToken()!=null) throw new IllegalArgumentException("Multiple JSON roots");
        }
        yield "application/json";
      }
      case COVER, THUMBNAIL -> {
        if(header.length>=8 && Arrays.equals(Arrays.copyOf(header,8),new byte[]{(byte)137,80,78,71,13,10,26,10})) yield "image/png";
        if(header.length>=3 && header[0]==(byte)255 && header[1]==(byte)216 && header[2]==(byte)255) yield "image/jpeg";
        if(ascii.startsWith("RIFF") && ascii.length()>=12 && ascii.substring(8,12).equals("WEBP")) yield "image/webp";
        throw new IllegalArgumentException("Unsupported image signature");
      }
      case PARQUET -> {
        require(ascii.startsWith("PAR1"));
        try(var file=new RandomAccessFile(path.toFile(),"r")) {
          require(file.length()>=8); file.seek(file.length()-4);
          require(file.readInt()==0x50415231);
        }
        yield "application/vnd.apache.parquet";
      }
      default -> throw new IllegalArgumentException("Upload type not yet supported");
    };
  }
  private static void require(boolean valid) { if(!valid) throw new IllegalArgumentException("Invalid content signature"); }
  public void close() throws IOException { Files.deleteIfExists(path); }
}
