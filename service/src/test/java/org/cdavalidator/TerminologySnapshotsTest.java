package org.cdavalidator;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import com.google.gson.*;
import net.sf.saxon.s9api.*;
import java.nio.file.*;
import java.security.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class TerminologySnapshotsTest {
    @TempDir Path assets;
    static void snapshot(Path assets, List<String> codes) throws Exception {
        Path dir=assets.resolve("terminology");Files.createDirectories(dir);
        String text=new Gson().toJson(codes);Path file=dir.resolve("procedure.json");Files.writeString(file,text);
        String hash=HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(text.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        Files.writeString(dir.resolve("manifest.json"),new Gson().toJson(Map.of("schemaVersion",1,"valueSets",List.of(Map.of("canonical",TerminologySnapshots.PROCEDURE,"file","procedure.json","count",codes.size(),"sha256",hash,"source","Synthetic test fixture")))));
    }
    @Test void indexesExactPairsWithoutXPathLiteralEscaping() throws Exception {
        snapshot(assets,List.of("1.2|12345","1.2|a'b","1.2|a\"b","1.2|a|b"));
        var data=TerminologySnapshots.load(assets);
        assertTrue(data.contains(TerminologySnapshots.PROCEDURE,"1.2","a'b"));
        assertTrue(data.contains(TerminologySnapshots.PROCEDURE,"1.2","a\"b"));
        assertTrue(data.contains(TerminologySnapshots.PROCEDURE,"1.2","a|b"));
        assertFalse(data.contains(TerminologySnapshots.PROCEDURE,"1.2","123"));
        assertFalse(data.contains(TerminologySnapshots.PROCEDURE,"9.9","12345"));
        var processor=new Processor(false);data.register(processor);
        var xpath=processor.newXPathCompiler();xpath.declareNamespace("t",TerminologySnapshots.NAMESPACE);
        assertTrue(xpath.compile("t:member-of('"+TerminologySnapshots.PROCEDURE+"','1.2','12345')").load().effectiveBooleanValue());
    }
    @Test void rejectsTamperedOrIncompletePacks() throws Exception {
        snapshot(assets,List.of("1.2|12345"));
        Files.writeString(assets.resolve("terminology/procedure.json"),"[]");
        assertThrows(Exception.class,()->TerminologySnapshots.load(assets));
        snapshot(assets,List.of("1.2|12345","1.2|12345"));
        assertThrows(Exception.class,()->TerminologySnapshots.load(assets));
        snapshot(assets,List.of());
        assertThrows(Exception.class,()->TerminologySnapshots.load(assets));
    }
    @Test void rejectsEscapingPaths() throws Exception {
        snapshot(assets,List.of("1.2|12345"));
        Path manifest=assets.resolve("terminology/manifest.json");
        var info=JsonParser.parseString(Files.readString(manifest)).getAsJsonObject();
        info.getAsJsonArray("valueSets").get(0).getAsJsonObject().addProperty("file","../outside.json");
        Files.writeString(assets.resolve("outside.json"),"[]");Files.writeString(manifest,info.toString());
        assertThrows(Exception.class,()->TerminologySnapshots.load(assets));
    }
    @Test void distinguishesMissingDataAndUnsupportedExpressionsFromDefiniteNonmembership() throws Exception {
        snapshot(assets,List.of("2.16.840.1.113883.6.96|12345"));
        var data=TerminologySnapshots.load(assets);
        assertEquals("true",data.status(TerminologySnapshots.PROCEDURE,"2.16.840.1.113883.6.96","12345"));
        assertEquals("false",data.status(TerminologySnapshots.PROCEDURE,"2.16.840.1.113883.6.96","123"));
        assertEquals("false",data.status(TerminologySnapshots.PROCEDURE,"9.9","12345"));
        assertEquals("unknown",data.status("https://example.test/missing","1.2","12345"));
        assertEquals("unknown",data.status(TerminologySnapshots.PROCEDURE,"2.16.840.1.113883.6.96","12345:67890=12345"));
        Path manifest=assets.resolve("terminology/manifest.json");var config=JsonParser.parseString(Files.readString(manifest)).getAsJsonObject();
        var info=config.getAsJsonArray("valueSets").get(0).getAsJsonObject();
        info.add("codeSystemEditions",new Gson().toJsonTree(Map.of("2.16.840.1.113883.6.96",List.of("test-edition"))));
        Files.writeString(manifest,config.toString());data=TerminologySnapshots.load(assets);
        assertEquals("true",data.status(TerminologySnapshots.PROCEDURE,"2.16.840.1.113883.6.96","12345","test-edition"));
        assertEquals("unknown",data.status(TerminologySnapshots.PROCEDURE,"2.16.840.1.113883.6.96","12345","another-edition"));
        assertEquals("false",data.status(TerminologySnapshots.PROCEDURE,"9.9","12345","another-edition"));
    }
}
