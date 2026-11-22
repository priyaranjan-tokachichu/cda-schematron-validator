package org.cdavalidator;

import com.google.gson.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class CoverageRemediationTest {
    @TempDir Path directory;
    @Test void preservesHistoricalCountsAndNeverClearsAnUnreviewedReport() throws Exception {
        Path assets=Path.of("assets");var catalog=CoverageCatalog.load(assets);
        var original=JsonParser.parseString(Files.readString(assets.resolve("ccda-5.0/CCDA-5.0-Results.json"))).getAsJsonObject();
        var current=CoverageRemediation.review(assets,original,catalog);
        assertTrue(current.get("knownGeneratorGapsAddressed").getAsBoolean());
        assertEquals(4,original.getAsJsonArray("errors").size());
        var changed=original.deepCopy();changed.getAsJsonArray("errors").add("Unreviewed model conversion failure");
        assertFalse(CoverageRemediation.review(assets,changed,catalog).get("knownGeneratorGapsAddressed").getAsBoolean());
    }
    @Test void completionDependsOnApplicableChecksInsteadOfAnAbsentOptionalDataset() throws Exception {
        Path assets=CoverageCatalogTest.copyAssets(directory.resolve("assets"));var engine=new ValidationEngine(assets,directory.resolve("terms"));
        // The synthetic CCD has structural errors but no terminology-bearing
        // values requiring an uninstalled dataset after language is removed.
        String xml=Files.readString(Path.of("web/sample.xml")).replace("  <languageCode code=\"en-US\"/>\n","");
        var result=engine.validate(xml,"ccda-5.0",false);
        assertEquals("invalid",result.get("status"));assertEquals(true,result.get("executionComplete"));assertEquals(true,result.get("validationComplete"));
        String entry="<encounter xmlns:sdtc='urn:hl7-org:sdtc'><templateId root='2.16.840.1.113883.10.20.22.4.49' extension='2015-08-01'/><sdtc:dischargeDispositionCode code='SYNTHETIC' codeSystem='2.16.840.1.113883.6.301.5'/></encounter>";
        String applicable=CoverageCatalogTest.withEntry(entry).replace("  <languageCode code=\"en-US\"/>\n","");
        var missing=engine.validate(applicable,"ccda-5.0",false);
        assertEquals(false,missing.get("validationComplete"));
        assertTrue(((List<?>)missing.get("findings")).toString().contains("USEncounterDischargeDisposition"));
        assertFalse(engine.profileInfo().get(1).getAsJsonObject("terminologySetup").get("ready").getAsBoolean());
    }
}
