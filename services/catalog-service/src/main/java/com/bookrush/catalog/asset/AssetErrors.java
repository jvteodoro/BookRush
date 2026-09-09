package com.bookrush.catalog.asset;

import com.bookrush.catalog.storage.StorageFailure;
import java.io.IOException;
import org.springframework.dao.DataAccessException;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

@RestControllerAdvice(assignableTypes={AssetController.class,DistributionController.class})
public class AssetErrors {
  @ExceptionHandler(IllegalArgumentException.class)
  ResponseEntity<ProblemDetail> invalid(IllegalArgumentException e) {return problem(400,"Invalid asset request");}
  @ExceptionHandler(MaxUploadSizeExceededException.class)
  ResponseEntity<ProblemDetail> tooLarge(Exception e) {return problem(413,"Upload exceeds size limit");}
  @ExceptionHandler({IOException.class,DataAccessException.class})
  ResponseEntity<ProblemDetail> unavailable(Exception e) {return problem(503,"Asset operation unavailable");}
  @ExceptionHandler(StorageFailure.class)
  ResponseEntity<ProblemDetail> storage(StorageFailure e) {
    return problem(e.kind()==StorageFailure.Kind.CONFLICT?409:e.kind()==StorageFailure.Kind.NOT_FOUND?404:503,"Storage operation could not complete");
  }
  private ResponseEntity<ProblemDetail> problem(int status,String detail) {
    return ResponseEntity.status(status).cacheControl(CacheControl.noStore()).body(ProblemDetail.forStatusAndDetail(HttpStatusCode.valueOf(status),detail));
  }
}
