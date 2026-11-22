package org.cdavalidator;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class CarePlanRulesTest {
    @TempDir Path directory;
    static String xml(String relation,String child) throws Exception {
        String entry="<act classCode='ACT' moodCode='INT'><templateId root='2.16.840.1.113883.10.20.22.4.518' extension='2026-05-01'/><code code='18776-5' codeSystem='2.16.840.1.113883.6.1'/><statusCode code='active'/><entryRelationship typeCode='"+relation+"'>"+child+"</entryRelationship></act>";
        return CoverageCatalogTest.withEntry(entry);
    }
    @SuppressWarnings("unchecked") static List<Map<String,Object>> local(ValidationEngine engine,String xml) throws Exception {
        var result=engine.validate(xml,"ccda-5.0",false);assertEquals(true,result.get("executionComplete"));
        return ((List<Map<String,Object>>)result.get("findings")).stream().filter(f->((String)f.getOrDefault("ruleId","")).startsWith("careplan-base-")).toList();
    }
    @Test void acceptsUnprofiledBaseAlternativesAndCatchesTheirMissingRequiredCode() throws Exception {
        Path assets=CoverageCatalogTest.copyAssets(directory.resolve("assets"));var engine=new ValidationEngine(assets,directory.resolve("terms"));
        for(String relation:List.of("RSON","COMP")) {
            String child="<act classCode='ACT' moodCode='EVN'><id root='1.2.3'/><code nullFlavor='UNK'/><statusCode code='completed'/></act>";
            assertTrue(local(engine,xml(relation,child)).isEmpty());
            var missing=local(engine,xml(relation,child.replace("<code nullFlavor='UNK'/>","")));
            assertTrue(missing.stream().anyMatch(f->((String)f.get("ruleId")).endsWith("Act-code-cardinality")));
            assertTrue(local(engine,xml(relation,child.replace("<id root='1.2.3'/>","<typeId root='9.9'/><id/>"))).stream().anyMatch(f->((String)f.get("ruleId")).endsWith("Act-typeId-root-fixed")));
            assertTrue(local(engine,xml(relation,child.replace("<id root='1.2.3'/>","<id/>"))).stream().anyMatch(f->((String)f.get("ruleId")).endsWith("Act-id-II-1")));
        }
        String observation="<observation classCode='OBS' moodCode='EVN'><code nullFlavor='UNK'/></observation>";
        assertTrue(local(engine,xml("RSON",observation)).isEmpty());
        assertTrue(local(engine,xml("RSON",observation.replace("<code nullFlavor='UNK'/>",""))).stream().anyMatch(f->((String)f.get("ruleId")).endsWith("Observation-code-cardinality")));
    }
    @Test void enforcesThePinnedBindingsAndDoesNotInventTemplateIds() throws Exception {
        Path assets=CoverageCatalogTest.copyAssets(directory.resolve("assets"));var engine=new ValidationEngine(assets,directory.resolve("terms"));
        String child="<act classCode='ACT' moodCode='EVN'><code nullFlavor='UNK'/><statusCode code='completed'/></act>";
        for(String invalid:List.of(child.replace("classCode='ACT'","classCode='OBS'"),child.replace("moodCode='EVN'","moodCode='GOL'"),child.replace("code='completed'","code='com'")))
            assertTrue(local(engine,xml("RSON",invalid)).stream().anyMatch(f->((String)f.get("ruleId")).endsWith("-binding")&&f.get("severity").equals("error")));
        String language=child.replace("</act>","<languageCode code='en-US'/></act>");
        assertTrue(local(engine,xml("RSON",language)).stream().anyMatch(f->f.get("stage").equals("coverage")));
        // The three union errors never justify requiring a fabricated templateId.
        assertTrue(local(engine,xml("RSON",child)).isEmpty());
    }
}
