package com.bookrush.readerprofileservice.api;
import java.security.Principal; import java.util.Map; import org.springframework.jdbc.core.JdbcTemplate; import org.springframework.web.bind.annotation.*;
@RestController @RequestMapping("/api/v1/profile") public class ProfileController {
 private final JdbcTemplate jdbc; public ProfileController(JdbcTemplate jdbc){this.jdbc=jdbc;} private String s(Principal p){return p==null?"anonymous":p.getName();}
 @GetMapping public Object get(Principal p){var rows=jdbc.queryForList("select subject_key,display_name,bio,is_public,updated_at from reader_profile.profile where subject_key=?",s(p)); return rows.isEmpty()?Map.of("subject",s(p),"displayName", ""):rows.getFirst();}
 @GetMapping("/public") public Object publicProfile(@RequestParam String subject){var rows=jdbc.queryForList("select subject_key,display_name as \"displayName\" from reader_profile.profile where subject_key=? and is_public=true",subject); return rows.isEmpty()?Map.of("subject",subject,"displayName", ""):rows.getFirst();}
 public record Update(String displayName,String bio,Boolean isPublic){}
 @PutMapping public Object update(@RequestBody Update u,Principal p){jdbc.update("insert into reader_profile.profile(subject_key,display_name,bio,is_public) values(?,?,?,?) on conflict(subject_key) do update set display_name=excluded.display_name,bio=excluded.bio,is_public=excluded.is_public,updated_at=now()",s(p),u.displayName(),u.bio(),u.isPublic()==null||u.isPublic()); return get(p);}
}
