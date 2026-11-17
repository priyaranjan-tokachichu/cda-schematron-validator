package org.cdavalidator;

import org.junit.jupiter.api.Test;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class VocabularyAssertionsTest {
    @Test void patientNameListsUseWholeTokensInOriginalGeneratedChecks() throws Exception {
        var engine=new ValidationEngine(Path.of("assets"));String fixture=Files.readString(Path.of("web/sample.xml"));
        for(String use:List.of("L P","L INVALID")) {
            String patient="<recordTarget><patientRole><id root='1.2.3'/><patient><name use='"+use+"'><given qualifier='BR IN'>Alex</given><family>Example</family></name></patient></patientRole></recordTarget>";
            var report=engine.validate(fixture.replace("  <component>",patient+"<component>"),"ccda-5.0",false);
            var findings=CoverageCatalogTest.findings(report);
            assertEquals(use.contains("INVALID"),findings.stream().anyMatch(f->((String)f.get("message")).contains("ValueSet CDAEntityNameUse")));
            assertFalse(findings.stream().anyMatch(f->((String)f.get("message")).contains("ValueSet EntityPersonNamePartQualifier")));
        }
        assertTrue(engine.profileInfo().get(1).get("vocabularyAssertionsNormalized").getAsInt()>400);
    }
    @Test void partialVocabularyCodesCannotPassAndAmbiguousWhitespaceIsDisclosed() throws Exception {
        var engine=new ValidationEngine(Path.of("assets"));
        for(String status:List.of("active","act","active completed")) {
            String procedure="<procedure classCode='PROC' moodCode='EVN'><templateId root='2.16.840.1.113883.10.20.22.4.14' extension='2024-05-01'/><id root='1.2.3'/><code nullFlavor='UNK'/><statusCode code='"+status+"'/><effectiveTime nullFlavor='UNK'/></procedure>";
            var findings=CoverageCatalogTest.findings(engine.validate(CoverageCatalogTest.withEntry(procedure),"ccda-5.0",false));
            assertEquals(status.equals("act"),findings.stream().anyMatch(f->((String)f.get("message")).contains("ValueSet ActStatus")&&f.get("severity").equals("error")));
            assertEquals(status.contains(" "),findings.stream().anyMatch(f->((String)f.getOrDefault("ruleId","")).endsWith("-inline-vocabulary-coverage")));
        }
    }
}
