package org.cdavalidator;

import com.google.gson.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.security.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class CoverageCatalogTest {
    @TempDir Path directory;
    static Path copyAssets(Path destination) throws Exception {
        Path source=Path.of("assets");
        try(var files=Files.walk(source)){for(Path file:files.toList()){
            Path relative=source.relativize(file);if(relative.startsWith("terminology"))continue;
            Path target=destination.resolve(relative);if(Files.isDirectory(file))Files.createDirectories(target);else Files.copy(file,target);
        }}return destination;
    }
    static String hash(byte[] data) throws Exception{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(data));}
    static void snapshots(Path directory,Map<String,List<String>> values) throws Exception {
        Files.createDirectories(directory);var entries=new ArrayList<Object>();int number=0;
        for(var e:values.entrySet()){
            byte[] data=new Gson().toJson(e.getValue()).getBytes(java.nio.charset.StandardCharsets.UTF_8);String name="snapshot-"+number+++".json";Files.write(directory.resolve(name),data);
            entries.add(Map.of("canonical",e.getKey(),"file",name,"count",e.getValue().size(),"sha256",hash(data),"sourceVersion","synthetic"));
        }Files.writeString(directory.resolve("manifest.json"),new Gson().toJson(Map.of("schemaVersion",1,"valueSets",entries)));
    }
    static String withEntry(String entry) throws Exception {return Files.readString(Path.of("web/sample.xml")).replace("<structuredBody/>","<structuredBody><component><section><entry>"+entry+"</entry></section></component></structuredBody>");}
    @SuppressWarnings("unchecked") static List<Map<String,Object>> findings(Map<String,Object> report){assertEquals(true,report.get("executionComplete"));return (List<Map<String,Object>>)report.get("findings");}
    @Test void connectsRequiredPrimitiveAndPreferredCodedBindingsAtPinnedLocations() throws Exception {
        Path assets=copyAssets(directory.resolve("assets")), terms=directory.resolve("terms");
        snapshots(terms,Map.of("http://cts.nlm.nih.gov/fhir/ValueSet/2.16.840.1.113762.1.4.1021.48",List.of("1.2|TEST"),"http://cts.nlm.nih.gov/fhir/ValueSet/2.16.840.1.113762.1.4.1095.65",List.of("1.2|TEST")));
        var engine=new ValidationEngine(assets,terms);
        for(String code:List.of("TEST","TES")) {
            String required="<observation><templateId root='2.16.840.1.113883.10.20.22.4.305' extension='2019-06-21'/><value code='"+code+"'/></observation>";
            var result=findings(engine.validate(withEntry(required),"ccda-5.0",false));
            assertEquals(!code.equals("TEST"),result.stream().anyMatch(f->((String)f.getOrDefault("ruleId","")).startsWith("local-binding-")&&f.get("severity").equals("error")));
        }
        String preferred="<act><templateId root='2.16.840.1.113883.10.20.22.4.130'/><code code='TEST' codeSystem='9.9'/></act>";
        assertTrue(findings(engine.validate(withEntry(preferred),"ccda-5.0",true)).stream().anyMatch(f->((String)f.getOrDefault("ruleId","")).startsWith("local-binding-")&&f.get("severity").equals("warning")));
        assertFalse(findings(engine.validate(withEntry(preferred),"ccda-5.0",false)).stream().anyMatch(f->((String)f.getOrDefault("ruleId","")).startsWith("local-binding-")&&f.get("stage").equals("schematron")));
        assertTrue(findings(engine.validate(withEntry(preferred),"ccda-5.0",false)).stream().anyMatch(f->f.get("stage").equals("coverage")));
        var profile=engine.profileInfo().get(1);assertEquals(2,profile.get("supplementalBindingChecks").getAsInt());assertFalse(profile.getAsJsonObject("terminologySetup").get("ready").getAsBoolean());
    }
    @Test void locationUnionAcceptsEveryAllowedSetAndRejectsWrongSystem() throws Exception {
        Path assets=copyAssets(directory.resolve("assets")),terms=directory.resolve("terms");var entries=new HashMap<String,List<String>>();
        for(int i=0;i<CoverageCatalog.LOCATION.size();i++)entries.put(CoverageCatalog.LOCATION.get(i),List.of("1.2|TEST"+i));
        snapshots(terms,entries);var engine=new ValidationEngine(assets,terms);
        for(String code:List.of("TEST0","TEST1","TEST2","INVALID")) {
            String role="<participantRole classCode='SDLOC'><templateId root='2.16.840.1.113883.10.20.22.4.32'/><code code='"+code+"' codeSystem='1.2'/><playingEntity classCode='PLC'/></participantRole>";
            var result=findings(engine.validate(withEntry(role),"ccda-5.0",false));
            assertEquals(code.equals("INVALID"),result.stream().anyMatch(f->"shall-use-one-binding".equals(f.get("ruleId"))));
            var wrong=findings(engine.validate(withEntry(role.replace("codeSystem='1.2'","codeSystem='9.9'")),"ccda-5.0",false));
            assertTrue(wrong.stream().anyMatch(f->"shall-use-one-binding".equals(f.get("ruleId"))));
        }
    }
    @Test void publicImageLegacyProfileRequiresAndVerifiesOperatorVocabulary() throws Exception {
        Path assets=copyAssets(directory.resolve("assets")),terms=directory.resolve("terms");Files.createDirectories(terms);
        Path original=directory.resolve("authorized-voc.xml");Files.move(assets.resolve("ccda-2.1/voc.xml"),original);
        byte[] placeholder="<systems xmlns='http://www.lantanagroup.com/voc'/>".getBytes(java.nio.charset.StandardCharsets.UTF_8);Files.write(assets.resolve("ccda-2.1/voc.xml"),placeholder);
        Path inventory=assets.resolve("ccda-2.1/.files.json");var files=JsonParser.parseString(Files.readString(inventory)).getAsJsonObject();files.addProperty("voc.xml",hash(placeholder));Files.writeString(inventory,files.toString());
        Path manifest=assets.resolve("manifest.json");var config=JsonParser.parseString(Files.readString(manifest)).getAsJsonObject();var expected=new JsonObject();expected.addProperty("file","ccda-2.1-voc.xml");expected.addProperty("sha256",hash(Files.readAllBytes(original)));config.getAsJsonArray("profiles").get(0).getAsJsonObject().add("operatorVocabulary",expected);Files.writeString(manifest,config.toString());
        var missing=new ValidationEngine(assets,terms);String fixture=Files.readString(Path.of("../test/VitalSigns.xml"));
        var report=missing.validate(fixture,"ccda-2.1",false);assertEquals(false,report.get("executionComplete"));assertEquals(false,report.get("validationComplete"));
        assertTrue(((List<?>)report.get("findings")).toString().contains("terminology-setup-required"));
        Path tampered=directory.resolve("wrong.xml");Files.writeString(tampered,"<systems/>");assertThrows(Exception.class,()->TerminologyTool.installLegacy(assets,terms,tampered));
        TerminologyTool.installLegacy(assets,terms,original);
        var installed=new ValidationEngine(assets,terms);var complete=installed.validate(fixture,"ccda-2.1",false);assertEquals(true,complete.get("executionComplete"));assertEquals(true,complete.get("validationComplete"));
        assertEquals(true,installed.profileInfo().get(0).getAsJsonObject("terminologySetup").get("ready").getAsBoolean());
    }
}
