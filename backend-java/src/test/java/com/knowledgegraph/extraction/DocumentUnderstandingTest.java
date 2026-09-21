package com.knowledgegraph.extraction;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.knowledgegraph.common.ApiException;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class DocumentUnderstandingTest {
    @Test void pagesBelongToTheirSourceInsteadOfBecomingSeparateBooks() {
        assertEquals("资料原文",DocumentUnderstandingService.sourceName("第 15 页"));
        assertEquals("资料原文",DocumentUnderstandingService.sourceName("15"));
        assertEquals("第一版.pdf",DocumentUnderstandingService.sourceName("第一版.pdf 第 15 页"));
    }
    final ObjectMapper mapper=new ObjectMapper();
    @Test void supportsThirteenIndependentCoursesWithoutTruncatingSources() throws Exception {
        var samples=new ArrayList<DocumentUnderstandingService.Fragment>();
        var courses=new ArrayList<DocumentUnderstandingService.Course>();
        for(int i=1;i<=13;i++) {
            samples.add(new DocumentUnderstandingService.Fragment(i,"来源"+i,"课程正文"));
            courses.add(new DocumentUnderstandingService.Course("c"+i,"课程"+i,"独立学科",List.of(),List.of("来源"+i),List.of((long)i)));
        }
        var raw=mapper.writeValueAsString(new DocumentUnderstandingService.Overview("综合课程",courses));
        assertEquals(13,DocumentUnderstandingService.parse(mapper,raw,samples).courses().size());
    }
    @Test void rejectsInventedEvidenceAndUncoveredSources() throws Exception {
        var samples=List.of(new DocumentUnderstandingService.Fragment(1,"第一版","正文"),new DocumentUnderstandingService.Fragment(2,"第二版","正文"));
        for(var course:List.of(
                new DocumentUnderstandingService.Course("a","课程","简介",List.of(),List.of("第一版"),List.of(99L)),
                new DocumentUnderstandingService.Course("a","课程","简介",List.of(),List.of("第一版"),List.of(1L)),
                new DocumentUnderstandingService.Course("a","课程","简介",List.of(),List.of("第一版","第二版"),List.of(1L)))) {
            var raw=mapper.writeValueAsString(new DocumentUnderstandingService.Overview("课程",List.of(course)));
            assertThrows(ApiException.class,()->DocumentUnderstandingService.parse(mapper,raw,samples));
        }
    }
    @Test void samplesEveryVersionAndLimitsTextBudget() {
        List<DocumentUnderstandingService.Fragment> rows=new ArrayList<>();
        for(int source=0;source<30;source++) for(int page=0;page<100;page++) rows.add(new DocumentUnderstandingService.Fragment(source*100+page,"版本"+source,"正文".repeat(3000)));
        var sample=DocumentUnderstandingService.sample(rows);
        assertEquals(30,sample.stream().map(DocumentUnderstandingService.Fragment::source).distinct().count());
        assertTrue(sample.stream().mapToInt(f->f.text().length()).sum()<=48000);
    }
}
