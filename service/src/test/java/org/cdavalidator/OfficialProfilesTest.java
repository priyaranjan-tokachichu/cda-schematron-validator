package org.cdavalidator;

import org.junit.jupiter.api.*;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class OfficialProfilesTest {
    @Test void fullOfficialPackagesCompileAndExecuteWithoutIgnoredAssertions() throws Exception {
        var dir=Path.of("assets");
        Assumptions.assumeTrue(Files.exists(dir.resolve("manifest.json")),"Run scripts/prepare-assets.py for official asset integration testing");
        var engine=new ValidationEngine(dir);
        for(String profile: List.of("ccda-2.1","ccda-5.0")) {
            String xml=Files.readString(profile.equals("ccda-2.1")?Path.of("../test/VitalSigns.xml"):Path.of("web/sample.xml"));
            var report=engine.validate(xml,profile,true);
            assertEquals(true,report.get("executionComplete"),profile);
            assertTrue(((Number)((Map<?,?>)report.get("summary")).get("firedRules")).intValue()>0,profile);
            assertTrue(((List<Map<String,Object>>)report.get("findings")).stream().noneMatch(f->"engine".equals(f.get("stage"))),profile);
            if(profile.equals("ccda-5.0")) assertEquals(false,report.get("validationComplete"),"Upstream conversion gaps must be visible");
        }
    }
    @Test void nestedTemplatesCannotEstablishDocumentCoverage() throws Exception {
        var engine = new ValidationEngine(Path.of("assets"));
        String original = Files.readString(Path.of("../test/VitalSigns.xml"));
        // Only strip immediate document template IDs; preserve all nested section/entry rules.
        int boundary = original.indexOf("<id ");
        assertTrue(boundary > 0);
        String stripped = original.substring(0, boundary).replaceAll("<templateId[^>]*/>", "") + original.substring(boundary);
        for (String xml : List.of(stripped, stripped.replaceFirst("<id ", "<templateId root='9.9.9' extension='2099-01-01'/><id "))) {
            var report = engine.validate(xml, "ccda-2.1", false);
            assertTrue(((Number)((Map<?,?>)report.get("summary")).get("firedRules")).intValue() > 0);
            assertEquals(true, report.get("executionComplete"));
            assertEquals(false, report.get("validationComplete"));
            assertNotEquals("valid", report.get("status"));
            assertTrue(((List<Map<String,Object>>)report.get("findings")).stream().anyMatch(f -> "coverage".equals(f.get("stage"))));
        }
        assertEquals(true, engine.validate(original,"ccda-2.1",false).get("validationComplete"));
    }

}
