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
    @Test void personNamesMustMeetPatientNameConstraintsOrBePlainText() throws Exception {
        var engine = new ValidationEngine(Path.of("assets"));
        String fixture = Files.readString(Path.of("web/sample.xml"));
        var cases = new LinkedHashMap<String, Boolean>();
        cases.put("<name><given>Alex</given><family>Example</family></name>", true);
        cases.put("<name use='L'><given>Alex</given><given>Robin</given><family>Example</family><suffix>Jr</suffix></name>", true);
        cases.put("<name>Alex Example</name>", true);
        cases.put("<name nullFlavor='UNK'/>", true);
        cases.put("<name><given>Alex</given><family nullFlavor='NA'/></name>", true);
        cases.put("<name/>", false);
        cases.put("<name>   </name>", false);
        cases.put("<name><family>Example</family></name>", false);
        cases.put("<name><given>Alex</given></name>", false);
        cases.put("<name><given>Alex</given><family>One</family><family>Two</family></name>", false);
        cases.put("<name><given>Alex</given><family>Example</family><suffix>Jr</suffix><suffix>MD</suffix></name>", false);
        cases.put("<name use='INVALID_USE'><given>Alex</given><family>Example</family></name>", false);
        cases.put("<name><given qualifier='INVALID_QUALIFIER'>Alex</given><family>Example</family></name>", false);
        for (var entry : cases.entrySet()) {
            String author = "<author><time value='20261116'/><assignedAuthor><id root='1.2.3'/><assignedPerson>" + entry.getKey() + "</assignedPerson></assignedAuthor></author>";
            var report = engine.validate(fixture.replace("  <component>", author + "<component>"), "ccda-5.0", false);
            assertEquals(true, report.get("executionComplete"), entry.getKey());
            boolean failed = ((List<Map<String,Object>>) report.get("findings")).stream().anyMatch(f -> "81-9371".equals(f.get("ruleId")));
            assertEquals(!entry.getValue(), failed, entry.getKey());
            assertEquals(false, report.get("validationComplete"), "Other upstream gaps are still unresolved");
        }
        String mixed = "<author><time value='20261116'/><assignedAuthor><id root='1.2.3'/><assignedPerson><name>Alex<family>Example</family></name></assignedPerson></assignedAuthor></author>";
        var mixedReport = engine.validate(fixture.replace("  <component>", mixed + "<component>"), "ccda-5.0", false);
        assertTrue(((List<Map<String,Object>>) mixedReport.get("findings")).stream().anyMatch(f -> ((String) f.get("message")).contains("81-9372")), "The existing no-mixed-content rule still applies");
    }
    @Test void supplementalNameCheckRejectsUnexpectedUpstreamLayout() throws Exception {
        var file = Path.of("assets/ccda-5.0/CCDA-5.0.sch");
        byte[] original = Files.readAllBytes(file);
        var doc = ValidationEngine.trustedSchematron(file);
        var rules = doc.getElementsByTagNameNS(ValidationEngine.SCH, "rule");
        for (int i = 0; i < rules.getLength(); i++) {
            var rule = (org.w3c.dom.Element) rules.item(i);
            if (rule.getAttribute("id").equals("USRealmPatientNamePTNUSFIELDED-errors-use")) rule.setAttribute("context", "unexpected/name/@use");
        }
        assertThrows(IllegalArgumentException.class, () -> SupplementalRules.addPersonNameInvariant(doc));
        assertArrayEquals(original, Files.readAllBytes(file), "Local layering must never rewrite the upstream asset");
    }
    @Test void coverageCountsOmissionsRatherThanReasonCategories() throws Exception {
        var manifest = com.google.gson.JsonParser.parseString(Files.readString(Path.of("assets/manifest.json"))).getAsJsonObject();
        var profile = manifest.getAsJsonArray("profiles").get(1).getAsJsonObject();
        var counts = profile.getAsJsonObject("generationCoverage");
        assertEquals(4, counts.get("errors").getAsInt());
        assertEquals(6, counts.get("unhandledInvariants").getAsInt());
        assertEquals(28, counts.get("nonLoadedValueSets").getAsInt());
        var report = profile.getAsJsonObject("generationReport");
        assertEquals(4, report.getAsJsonObject("unhandledInvariants").size());
        assertEquals(9, report.getAsJsonObject("nonLoadedValueSets").size());
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
