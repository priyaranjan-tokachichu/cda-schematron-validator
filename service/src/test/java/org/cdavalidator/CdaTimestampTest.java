package org.cdavalidator;

import org.junit.jupiter.api.Test;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class CdaTimestampTest {
    @Test void comparesCalendarValuesAndOffsetsRatherThanStrings() {
        assertEquals("true",CdaTimestamp.compare("202501010100+0200","202412312330+0000","le"));
        assertEquals("false",CdaTimestamp.compare("202412312330-0200","202501010100+0000","le"));
        assertEquals("false",CdaTimestamp.compare("202501010100+0200","202412312300+0000","gt"));
        assertEquals("true",CdaTimestamp.compare("20240229","20240301","le"));
        assertEquals("true",CdaTimestamp.compare("20250101000000.12345678901Z".replace("Z","+0000"),"20250101000000.12345678902+0000","le"));
        assertEquals("false",CdaTimestamp.compare("20250101000000.1","20250101000000.10","gt"));
    }
    @Test void partialOrInvalidTimestampsNeverBecomeAssumedPasses() {
        assertEquals("unknown",CdaTimestamp.compare("2025","202501","le"));
        assertEquals("true",CdaTimestamp.compare("2024","202501","le"));
        assertEquals("false",CdaTimestamp.compare("2026","202501","le"));
        assertEquals("unknown",CdaTimestamp.compare("2025010110+0530","202501011050+0530","le"));
        assertEquals("unknown",CdaTimestamp.compare("2025010110+0530","2025010105+0000","le"));
        assertEquals("unknown",CdaTimestamp.compare("202501010000","202501010000+0000","le"));
        for(String invalid:List.of("","20250229","20241301","202501012400","0000","20250101000000+1900","2025.1"))assertEquals("unknown",CdaTimestamp.compare(invalid,"20250101","le"),invalid);
    }
    @SuppressWarnings("unchecked")
    @Test void procedureDatesProduceFindingsIncludingUncertaintyWhenWarningsAreHidden() throws Exception {
        var engine=new ValidationEngine(Path.of("assets"));String fixture=Files.readString(Path.of("web/sample.xml"));
        for(String value:List.of("20270101","2026","20250101")) {
            String procedure="<procedure classCode='PROC' moodCode='EVN'><templateId root='2.16.840.1.113883.10.20.22.4.14' extension='2024-05-01'/><id root='1.2.3'/><code nullFlavor='UNK'/><statusCode code='active'/><effectiveTime><low value='"+value+"'/><high value='20240101'/></effectiveTime></procedure>";
            String xml=fixture.replace("<structuredBody/>","<structuredBody><component><section><entry>"+procedure+"</entry></section></component></structuredBody>").replaceAll("<effectiveTime value=\"[^\"]+\"/>","<effectiveTime value=\"20260101\"/>");
            var report=engine.validate(xml,"ccda-5.0",false);assertEquals(true,report.get("executionComplete"));
            var findings=(List<Map<String,Object>>)report.get("findings");
            assertTrue(findings.stream().anyMatch(f->"active-high-ts-after-document".equals(f.get("ruleId"))));
            assertEquals(value.equals("20270101"),findings.stream().anyMatch(f->"ts-value-before-document".equals(f.get("ruleId"))),value);
            assertEquals(value.equals("2026"),findings.stream().anyMatch(f->"ts-value-before-document-comparison-coverage".equals(f.get("ruleId"))&&"coverage".equals(f.get("stage"))),value);
        }
    }
}
