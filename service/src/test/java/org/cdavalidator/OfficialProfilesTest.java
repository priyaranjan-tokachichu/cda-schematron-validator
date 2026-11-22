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
    @Test void localProcedureSnapshotRestoresBothOmittedInvariants(@org.junit.jupiter.api.io.TempDir Path localAssets) throws Exception {
        Path upstream = Path.of("assets");
        try (var files = Files.walk(upstream)) {
            for (var source : files.toList()) {
                Path relative = upstream.relativize(source);
                if (relative.startsWith("terminology")) continue;
                Path target = localAssets.resolve(relative);
                if (Files.isDirectory(source)) Files.createDirectories(target); else Files.copy(source, target);
            }
        }
        TerminologySnapshotsTest.snapshot(localAssets, List.of("2.16.840.1.113883.6.96|12345"));
        var engine = new ValidationEngine(localAssets);
        String sample = Files.readString(Path.of("web/sample.xml"));
        for (String note : List.of("7", "6")) {
            String template = sample.replace("10.20.22.1.2", "10.20.22.1." + note);
            String id = note.equals("7") ? "1198-8487" : "1198-8511";
            var cases = new LinkedHashMap<String, Boolean>();
            cases.put("code='12345' codeSystem='2.16.840.1.113883.6.96'", true);
            cases.put("code='1234' codeSystem='2.16.840.1.113883.6.96'", false);
            cases.put("code='12345' codeSystem='9.9'", false);
            cases.put("code='99' codeSystem='2.16.840.1.113883.6.104'", true);
            cases.put("code='99' codeSystem='2.16.840.1.113883.6.4'", true);
            cases.put("code='99' codeSystem='2.16.840.1.113883.6.12'", true);
            cases.put("code='99' codeSystem='2.16.840.1.113883.6.285'", note.equals("6"));
            cases.put("codeSystem='2.16.840.1.113883.6.96'", false);
            for (var entry : cases.entrySet()) {
                String event = "<documentationOf><serviceEvent classCode='PROC'><code " + entry.getKey() + "/></serviceEvent></documentationOf>";
                var result = engine.validate(template.replace("  <component>", event + "<component>"), "ccda-5.0", false);
                assertEquals(true, result.get("executionComplete"), entry.getKey());
                boolean failed = ((List<Map<String,Object>>) result.get("findings")).stream().anyMatch(f -> id.equals(f.get("ruleId")));
                assertEquals(!entry.getValue(), failed, note + ": " + entry.getKey());
                assertEquals(false, result.get("validationComplete"), "Other gaps must remain visible");
            }
        }
        var info = engine.profileInfo().stream().filter(p -> "ccda-5.0".equals(p.get("id").getAsString())).findFirst().orElseThrow();
        assertEquals(6, info.getAsJsonArray("supplementalChecks").size());
        assertEquals(1, info.getAsJsonArray("terminologySnapshots").size());
    }
    @Test void personNamesMustMeetPatientNameConstraintsOrBePlainText() throws Exception {
        var engine = new ValidationEngine(Path.of("assets"),Path.of("target/test-no-terminology"));
        String fixture = Files.readString(Path.of("web/sample.xml"));
        var cases = new LinkedHashMap<String, Boolean>();
        cases.put("<name><given>Alex</given><family>Example</family></name>", true);
        cases.put("<name use='L'><given>Alex</given><given>Robin</given><family>Example</family><suffix>Jr</suffix></name>", true);
        cases.put("<name use='L P'><given qualifier='BR IN'>Alex</given><family qualifier='BR IN'>Example</family></name>", true);
        cases.put("<name use='  P   L '><prefix qualifier='AC TITLE'>Dr</prefix><given>Alex</given><family>Example</family><suffix qualifier='AC TITLE'>MD</suffix></name>", true);
        cases.put("<name use='L INVALID_USE'><given>Alex</given><family>Example</family></name>", false);
        cases.put("<name><given qualifier='BR INVALID'>Alex</given><family>Example</family></name>", false);
        cases.put("<name use='S'><given>Alex</given><family>Example</family></name>", false);
        cases.put("<name><given qualifier='B'>Alex</given><family>Example</family></name>", false);
        cases.put("<name><given qualifier='BR LS'>Alex</given><family>Example</family></name>", false);
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
        var engine = new ValidationEngine(Path.of("assets"),Path.of("target/test-no-terminology"));
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
