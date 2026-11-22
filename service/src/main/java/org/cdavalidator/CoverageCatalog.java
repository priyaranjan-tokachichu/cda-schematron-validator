package org.cdavalidator;

import com.google.gson.*;
import org.w3c.dom.*;
import java.nio.file.*;
import java.util.*;

/** Locations come from the exact checksum-verified HL7 binding catalog, never guessed paths. */
final class CoverageCatalog {
    static final String PRIORITY="http://terminology.hl7.org/ValueSet/v3-ActPriority";
    static final String CMS="http://hl7.org/cda/us/ccda/ValueSet/CMSPlaceOfServiceCodes";
    static final List<String> LOCATION=List.of("http://cts.nlm.nih.gov/fhir/ValueSet/2.16.840.1.113883.1.11.20275","http://cts.nlm.nih.gov/fhir/ValueSet/2.16.840.1.113762.1.4.1267.26",CMS);
    record Binding(String xpath,String canonical,String strength) {}
    final Set<String> required;
    final List<Binding> bindings;
    private CoverageCatalog(Set<String> required,List<Binding> bindings){this.required=Set.copyOf(required);this.bindings=List.copyOf(new LinkedHashSet<>(bindings));}
    static CoverageCatalog empty(){return new CoverageCatalog(Set.of(),List.of());}
    static CoverageCatalog load(Path assets) throws Exception {
        var needed=new TreeSet<String>();
        var report=JsonParser.parseString(Files.readString(assets.resolve("ccda-5.0/CCDA-5.0-Results.json"))).getAsJsonObject();
        for(var group:report.getAsJsonObject("nonLoadedValueSets").entrySet())for(var value:group.getValue().getAsJsonArray())needed.add(value.getAsString().split(" ")[0]);
        needed.add(PRIORITY);needed.add(TerminologySnapshots.PROCEDURE);needed.addAll(LOCATION);
        var bindings=new ArrayList<Binding>();
        var catalog=JsonParser.parseString(Files.readString(assets.resolve("ccda-5.0/CCDA-5.0-Bindings.json"))).getAsJsonObject();
        for(var group:catalog.entrySet())for(var item:group.getValue().getAsJsonArray()){
            var b=item.getAsJsonObject();String canonical=b.get("valueSetId").getAsString();
            if(needed.contains(canonical))bindings.add(new Binding(b.get("xpath").getAsString(),canonical,b.get("strength").getAsString()));
        }
        return new CoverageCatalog(needed,bindings);
    }
    JsonObject readiness(TerminologySnapshots data,GrammarChecks grammar) {
        var result=new JsonObject();var missing=new JsonArray();var installed=new JsonArray();
        for(String canonical:new TreeSet<>(required)){if(data.members.containsKey(canonical)||grammar.supports(canonical))installed.add(canonical);else missing.add(canonical);}
        result.addProperty("ready",missing.isEmpty());result.add("missingValueSets",missing);result.add("installedValueSets",installed);
        result.addProperty("setupGuide","/terminology.html");
        result.addProperty("distribution","Terminology is operator-supplied. Dataset installation is separate from validation coverage verification.");return result;
    }
    int addBindings(Document doc,TerminologySnapshots data,GrammarChecks grammar) {
        Element ns=doc.createElementNS(ValidationEngine.SCH,"ns");ns.setAttribute("prefix","local-term");ns.setAttribute("uri",TerminologySnapshots.NAMESPACE);doc.getDocumentElement().appendChild(ns);
        int count=0;
        for(var b:bindings){
            if(data.members.containsKey(b.canonical)||grammar.supports(b.canonical))count++;
            String id="local-binding-"+UUID.nameUUIDFromBytes((b.canonical+"\n"+b.xpath+"\n"+b.strength).getBytes(java.nio.charset.StandardCharsets.UTF_8));
            Element pattern=doc.createElementNS(ValidationEngine.SCH,"pattern");pattern.setAttribute("id",id);
            Element rule=doc.createElementNS(ValidationEngine.SCH,"rule");rule.setAttribute("context",b.xpath);
            Element assertion=doc.createElementNS(ValidationEngine.SCH,"assert");assertion.setAttribute("id",id);assertion.setAttribute("role",b.strength.equals("required")?"error":"warning");
            boolean attribute=b.xpath.matches("(?s).*/@[^/]+$");
            String status=attribute?"local-term:code-membership-status('"+b.canonical+"',string(.))":"local-term:versioned-membership-status('"+b.canonical+"',string(@codeSystem),string(@code),string(@codeSystemVersion))";
            if(grammar.supports(b.canonical))status="local-term:grammar-status('"+b.canonical+"',string("+(attribute?".":"@code")+"))";
            String bypass=attribute?"":"@nullFlavor or not(@code) or ";
            assertion.setAttribute("test",bypass+status+" != 'false'");
            assertion.setTextContent((b.strength.equals("required")?"SHALL":"SHOULD")+" be selected from the installed, pinned terminology of "+b.canonical+" ("+b.strength+" binding).");
            rule.appendChild(assertion);
            Element coverage=doc.createElementNS(ValidationEngine.SCH,"assert");coverage.setAttribute("id",id+"-coverage");coverage.setAttribute("role","coverage");
            coverage.setAttribute("test",bypass+status+" != 'unknown'");
            coverage.setTextContent("This binding could not be checked: its dataset is missing, the declared edition is unavailable, or the value needs unsupported expression/grammar evaluation. Install the named dataset or review this coverage gap: "+b.canonical);rule.appendChild(coverage);
            pattern.appendChild(rule);doc.getDocumentElement().appendChild(pattern);
        }
        return count;
    }
}
