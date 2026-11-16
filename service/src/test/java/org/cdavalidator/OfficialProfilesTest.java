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
}
