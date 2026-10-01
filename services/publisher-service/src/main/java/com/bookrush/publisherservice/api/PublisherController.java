package com.bookrush.publisherservice.api;
import java.security.Principal; import java.util.*; import org.springframework.jdbc.core.JdbcTemplate; import org.springframework.web.bind.annotation.*;
@RestController @RequestMapping("/api/v1/publisher") public class PublisherController { private final JdbcTemplate jdbc; public PublisherController(JdbcTemplate jdbc){this.jdbc=jdbc;} private String s(Principal p){return p==null?"anonymous":p.getName();} public record Submission(String title){}
 @PostMapping("/submissions") public Object create(@RequestBody Submission req,Principal p){UUID id=UUID.randomUUID();jdbc.update("insert into publisher.submission(id,subject_key,title) values(?,?,?)",id,s(p),req.title());return Map.of("id",id,"title",req.title(),"status","DRAFT");}
 @GetMapping("/submissions") public Object list(Principal p){return jdbc.queryForList("select id,title,status,created_at,updated_at from publisher.submission where subject_key=? order by created_at desc",s(p));}
}
