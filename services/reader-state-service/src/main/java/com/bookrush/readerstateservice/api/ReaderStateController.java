package com.bookrush.readerstateservice.api;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.security.Principal;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/reader")
public class ReaderStateController {
 private final JdbcTemplate jdbc; public ReaderStateController(JdbcTemplate jdbc){this.jdbc=jdbc;}
 private String subject(Principal p){return p==null?"anonymous":p.getName();}
 public record Progress(@NotNull Long positionCodepoint, @NotNull Double percent){}
 @GetMapping("/library") public Object library(Principal p){return jdbc.queryForList("select book_id,status,created_at from reader_state.library where subject_key=? order by created_at desc",subject(p));}
 @PutMapping("/library/{bookId}") public Map<String,Object> save(@PathVariable UUID bookId,Principal p){jdbc.update("insert into reader_state.library(subject_key,book_id) values(?,?) on conflict do nothing",subject(p),bookId);return Map.of("bookId",bookId,"saved",true);}
 @DeleteMapping("/library/{bookId}") public void remove(@PathVariable UUID bookId,Principal p){jdbc.update("delete from reader_state.library where subject_key=? and book_id=?",subject(p),bookId);}
 @PutMapping("/books/{bookId}/progress") public Map<String,Object> progress(@PathVariable UUID bookId,@Valid @RequestBody Progress v,Principal p){jdbc.update("insert into reader_state.progress(subject_key,book_id,position_codepoint,percent) values(?,?,?,?,?) on conflict(subject_key,book_id) do update set position_codepoint=excluded.position_codepoint,percent=excluded.percent,updated_at=now()",subject(p),bookId,v.positionCodepoint(),v.percent());return Map.of("bookId",bookId,"percent",v.percent());}
 @GetMapping("/books/{bookId}/progress") public Object getProgress(@PathVariable UUID bookId,Principal p){return jdbc.queryForList("select position_codepoint,percent,updated_at from reader_state.progress where subject_key=? and book_id=?",subject(p),bookId);}
}
