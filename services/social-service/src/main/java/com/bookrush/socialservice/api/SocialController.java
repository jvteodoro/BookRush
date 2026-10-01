package com.bookrush.socialservice.api;
import java.security.Principal; import java.util.*; import org.springframework.jdbc.core.JdbcTemplate; import org.springframework.web.bind.annotation.*;
@RestController @RequestMapping("/api/v1/social") public class SocialController {
 private final JdbcTemplate jdbc; public SocialController(JdbcTemplate jdbc){this.jdbc=jdbc;} private String s(Principal p){return p==null?"anonymous":p.getName();}
 @PutMapping("/books/{bookId}/like") public Map<String,Object> like(@PathVariable UUID bookId,Principal p){jdbc.update("insert into social.likes(subject_key,book_id) values(?,?) on conflict do nothing",s(p),bookId);return Map.of("bookId",bookId,"liked",true);}
 @DeleteMapping("/books/{bookId}/like") public void unlike(@PathVariable UUID bookId,Principal p){jdbc.update("delete from social.likes where subject_key=? and book_id=?",s(p),bookId);}
 @PutMapping("/users/{user}/follow") public Map<String,Object> follow(@PathVariable String user,Principal p){jdbc.update("insert into social.follows(follower,followed) values(?,?) on conflict do nothing",s(p),user);return Map.of("user",user,"following",true);}
 @DeleteMapping("/users/{user}/follow") public void unfollow(@PathVariable String user,Principal p){jdbc.update("delete from social.follows where follower=? and followed=?",s(p),user);}
 @PostMapping("/reports") public Object report(@RequestBody Map<String,Object> body,Principal p){UUID id=UUID.randomUUID();UUID book=body.get("bookId")==null?null:UUID.fromString(String.valueOf(body.get("bookId")));jdbc.update("insert into social.reports(id,reporter,book_id,reason) values(?,?,?,?)",id,s(p),book,String.valueOf(body.getOrDefault("reason","OTHER")));return Map.of("id",id,"status","OPEN");}
 @GetMapping("/books/{bookId}/likes") public Map<String,Object> likes(@PathVariable UUID bookId){return Map.of("bookId",bookId,"count",jdbc.queryForObject("select count(*) from social.likes where book_id=?",Long.class));}
 public record Comment(String body){}
 @PostMapping("/books/{bookId}/comments") public Object comment(@PathVariable UUID bookId,@RequestBody Comment c,Principal p){UUID id=UUID.randomUUID();jdbc.update("insert into social.comments(id,subject_key,book_id,body) values(?,?,?,?)",id,s(p),bookId,c.body());return Map.of("id",id,"bookId",bookId,"body",c.body());}
 @GetMapping("/books/{bookId}/comments") public Object comments(@PathVariable UUID bookId){return jdbc.queryForList("select id,subject_key,body,created_at from social.comments where book_id=? order by created_at",bookId);}
}
