package org.cdavalidator;

import com.google.gson.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class GrammarChecksTest {
    @TempDir Path directory;
    static final String LANGUAGES="""
        File-Date: 2026-01-01
        %%
        Type: language
        Subtag: en
        %%
        Type: language
        Subtag: zh
        %%
        Type: language
        Subtag: qaa..qtz
        %%
        Type: extlang
        Subtag: cmn
        Prefix: zh
        %%
        Type: script
        Subtag: Latn
        %%
        Type: region
        Subtag: US
        %%
        Type: variant
        Subtag: fonipa
        %%
        Type: grandfathered
        Tag: i-default
        """;
    static final String EXTENSIONS="Identifier: t\n%%\nIdentifier: u\n";
    @Test void validatesRegisteredBcp47WithExtensionsAndPrivateUse() {
        var languages=new LanguageTags(LANGUAGES,EXTENSIONS);
        for(String code:List.of("en","en-US","en-Latn-US","EN-us-u-ca-gregory","zh-cmn","en-fonipa","i-default","x-local-test","qaa","en-x-local"))assertTrue(languages.valid(code),code);
        for(String code:List.of("","en_US","en-UNKNOWN","zz","en-Qwer","en-XX","en-cmn","en-fonipa-fonipa","en-a-foo","en-u-ca-gregory-u-nu-latn","en-","x","en-x"))assertFalse(languages.valid(code),code);
    }
    JsonObject definition(String filename,String content) throws Exception {
        byte[] bytes=content.getBytes(java.nio.charset.StandardCharsets.UTF_8);Files.write(directory.resolve(filename),bytes);
        var info=new JsonObject();info.addProperty("file",filename);info.addProperty("sha256",CoverageCatalogTest.hash(bytes));info.addProperty("version","synthetic");return info;
    }
    @Test void validatesCompositeUcumUsingPinnedDefinitionsAndRejectsTampering() throws Exception {
        String xml="""
          <root version="synthetic" revision="test" revision-date="2026-01-01">
          <base-unit Code="foo" CODE="FOO" dim="L"><name>synthetic length</name><printSymbol>foo</printSymbol><property>length</property></base-unit>
          <base-unit Code="bar" CODE="BAR" dim="T"><name>synthetic time</name><printSymbol>bar</printSymbol><property>time</property></base-unit>
          </root>
          """;
        var info=definition("essence.xml",xml);var config=new JsonObject();config.addProperty("schemaVersion",1);config.add("valueSets",new JsonArray());var grammars=new JsonObject();grammars.add("ucum",info);config.add("grammars",grammars);Files.writeString(directory.resolve("manifest.json"),config.toString());
        var grammar=GrammarChecks.load(directory);assertTrue(grammar.supports(GrammarChecks.UCUM));assertTrue(grammar.valid(GrammarChecks.UCUM,"foo/bar2"));assertFalse(grammar.valid(GrammarChecks.UCUM,"FOO/bar"));assertFalse(grammar.valid(GrammarChecks.UCUM,"nonexistent"));
        assertEquals("unknown",grammar.status(GrammarChecks.UCUM,"(".repeat(65)+"foo"+")".repeat(65)));
        assertEquals("true",grammar.status(GrammarChecks.UCUM,"foo{an unmatched ( in an annotation}"));
        info.addProperty("version","wrong-edition");Files.writeString(directory.resolve("manifest.json"),config.toString());
        assertTrue(assertThrows(Exception.class,()->GrammarChecks.load(directory)).getMessage().contains("edition"));
        info.addProperty("version","synthetic");Files.writeString(directory.resolve("manifest.json"),config.toString());
        Files.writeString(directory.resolve("essence.xml"),xml+" ");assertThrows(Exception.class,()->GrammarChecks.load(directory));
        info=definition("essence.xml","<!DOCTYPE root [<!ENTITY x SYSTEM 'file:///nonexistent'>]>"+xml);grammars.add("ucum",info);Files.writeString(directory.resolve("manifest.json"),config.toString());assertThrows(Exception.class,()->GrammarChecks.load(directory));
    }
    @Test void finiteListsCannotSubstituteForLanguageOrUcumGrammar() throws Exception {
        CoverageCatalogTest.snapshots(directory,Map.of(GrammarChecks.LANGUAGE,List.of("1.2|en"),GrammarChecks.UCUM,List.of("1.3|foo")));
        var active=new HashSet<>(Set.of(GrammarChecks.LANGUAGE,GrammarChecks.UCUM));active.removeAll(GrammarChecks.CANONICALS);
        var data=TerminologySnapshots.loadDirectory(directory,active);assertTrue(data.members.isEmpty());
        assertFalse(GrammarChecks.load(directory).supports(GrammarChecks.LANGUAGE));
    }
    @Test void excessiveButWellFormedGrammarProducesCoverageInsteadOfFalseInvalidity() throws Exception {
        Path assets=CoverageCatalogTest.copyAssets(directory.resolve("assets"));
        var language=definition("subtags.txt",LANGUAGES);language.addProperty("version","2026-01-01");
        var extension=definition("extensions.txt",EXTENSIONS);language.addProperty("extensionsFile","extensions.txt");language.addProperty("extensionsSha256",extension.get("sha256").getAsString());
        Files.writeString(directory.resolve("manifest.json"),new Gson().toJson(Map.of("schemaVersion",1,"valueSets",List.of(),"grammars",Map.of("language",language))));
        var engine=new ValidationEngine(assets,directory);String sample=Files.readString(Path.of("web/sample.xml"));
        for(String code:List.of("en-Latn-US","zz-XX","en-x-"+String.join("-",Collections.nCopies(40,"abcdefgh")))) {
            var report=engine.validate(sample.replace("en-US",code),"ccda-5.0",false);
            var findings=CoverageCatalogTest.findings(report);
            assertEquals(code.equals("zz-XX"),findings.stream().anyMatch(f->((String)f.getOrDefault("ruleId","")).startsWith("local-binding-")&&f.get("severity").equals("error")));
            assertEquals(code.length()>255,findings.stream().anyMatch(f->((String)f.getOrDefault("ruleId","")).startsWith("local-binding-")&&f.get("stage").equals("coverage")));
        }
    }
}
