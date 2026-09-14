package com.bookrush.analytics.llm;
import static org.junit.jupiter.api.Assertions.*; import org.junit.jupiter.api.Test;
class DisabledTeacherLabelProviderTest { @Test void isOffByDefaultAndCachesByTextPrompt(){ var p=new TeacherLabelProvider.Prompt("p1","label"); var a=new DisabledTeacherLabelProvider().label("texto",p); var b=new DisabledTeacherLabelProvider().label("texto",p); assertFalse(a.enabled()); assertEquals(a.cacheKey(),b.cacheKey()); assertNotEquals(a.cacheKey(),new DisabledTeacherLabelProvider().label("outro",p).cacheKey()); } }
