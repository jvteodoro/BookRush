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
 @PostMapping("/books/{bookId}/opened") public Map<String,Object> opened(@PathVariable UUID bookId,Principal p){jdbc.update("insert into reader_state.recent(subject_key,book_id) values(?,?,) on conflict(subject_key,book_id) do update set last_opened_at=now()",subject(p),bookId);return Map.of("bookId",bookId,"recorded",true);}
 @GetMapping("/recent") public Object recent(Principal p){return jdbc.queryForList("select book_id,last_opened_at from reader_state.recent where subject_key=? order by last_opened_at desc limit 50",subject(p));}
 @PostMapping("/sessions") public Object start(Principal p){UUID id=UUID.randomUUID();jdbc.update("insert into reader_state.session(id,subject_key,started_at) values(?,?,now())",id,subject(p));return Map.of("id",id,"startedAt",java.time.Instant.now());}
 @PostMapping("/sessions/{id}/end") public Object end(@PathVariable UUID id,@RequestBody Map<String,Object> body,Principal p){int seconds=((Number)body.getOrDefault("activeSeconds",0)).intValue();jdbc.update("update reader_state.session set ended_at=now(),active_seconds=? where id=? and subject_key=?",seconds,id,subject(p));return Map.of("id",id,"activeSeconds",seconds);}
 @GetMapping("/library") public Object library(Principal p){return jdbc.queryForList("select book_id,status,created_at from reader_state.library where subject_key=? order by created_at desc",subject(p));}
 @PutMapping("/library/{bookId}") public Map<String,Object> save(@PathVariable UUID bookId,Principal p){jdbc.update("insert into reader_state.library(subject_key,book_id) values(?,?) on conflict do nothing",subject(p),bookId);return Map.of("bookId",bookId,"saved",true);}
 @DeleteMapping("/library/{bookId}") public void remove(@PathVariable UUID bookId,Principal p){jdbc.update("delete from reader_state.library where subject_key=? and book_id=?",subject(p),bookId);}
 @PutMapping("/books/{bookId}/progress") public Map<String,Object> progress(@PathVariable UUID bookId,@Valid @RequestBody Progress v,Principal p){jdbc.update("insert into reader_state.progress(subject_key,book_id,position_codepoint,percent) values(?,?,?,?,?) on conflict(subject_key,book_id) do update set position_codepoint=excluded.position_codepoint,percent=excluded.percent,updated_at=now()",subject(p),bookId,v.positionCodepoint(),v.percent());return Map.of("bookId",bookId,"percent",v.percent());}
 @PostMapping("/books/{bookId}/bookmarks") public Map<String,Object> bookmark(@PathVariable UUID bookId,@RequestBody Map<String,Object> body,Principal p){long pos=((Number)body.getOrDefault("positionCodepoint",0)).longValue();String note=String.valueOf(body.getOrDefault("note",""));UUID id=UUID.randomUUID();jdbc.update("insert into reader_state.bookmarks(id,subject_key,book_id,position_codepoint,note) values(?,?,?,?,?) on conflict(subject_key,book_id,position_codepoint) do update set note=excluded.note",id,subject(p),bookId,pos,note);return Map.of("id",id,"bookId",bookId,"positionCodepoint",pos,"note",note);}
 @GetMapping("/books/{bookId}/bookmarks") public Object bookmarks(@PathVariable UUID bookId,Principal p){return jdbc.queryForList("select id,position_codepoint,note,created_at from reader_state.bookmarks where subject_key=? and book_id=? order by position_codepoint",subject(p),bookId);}
 @GetMapping("/books/{bookId}/progress") public Object getProgress(@PathVariable UUID bookId,Principal p){return jdbc.queryForList("select position_codepoint,percent,updated_at from reader_state.progress where subject_key=? and book_id=?",subject(p),bookId);}
}
