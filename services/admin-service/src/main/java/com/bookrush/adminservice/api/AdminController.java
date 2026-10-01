package com.bookrush.adminservice.api;
import java.security.Principal; import java.util.*; import org.springframework.jdbc.core.JdbcTemplate; import org.springframework.web.bind.annotation.*;
@RestController @RequestMapping("/api/v1/admin") public class AdminController { private final JdbcTemplate jdbc; public AdminController(JdbcTemplate jdbc){this.jdbc=jdbc;} private String actor(Principal p){return p==null?"unknown":p.getName();}
 @PostMapping("/audit") public Map<String,Object> audit(@RequestBody Map<String,Object> body,Principal p){UUID id=UUID.randomUUID();String action=String.valueOf(body.getOrDefault("action","UNKNOWN"));String target=String.valueOf(body.getOrDefault("target",""));jdbc.update("insert into admin.audit_log(id,actor_subject,action,target,details) values(?,?,?,?,?::jsonb)",id,actor(p),action,target,"{}");return Map.of("id",id,"recorded",true);}
 @GetMapping("/audit") public Object list(){return jdbc.queryForList("select id,actor_subject,action,target,created_at from admin.audit_log order by created_at desc limit 100");}
}
