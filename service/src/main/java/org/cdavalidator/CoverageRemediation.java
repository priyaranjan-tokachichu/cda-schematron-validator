package org.cdavalidator;

import com.google.gson.*;
import java.util.*;
import java.nio.file.*;
import java.security.MessageDigest;

/** Account for reviewed replacements without erasing the original upstream report. */
final class CoverageRemediation {
    static final Set<String> INVARIANTS=Set.of("81-9371","1198-8487","1198-8511","active-high-ts-after-document","ts-value-before-document","shall-use-one-binding");
    static final Set<String> ERRORS=Set.of(
        "CarePlanAct: Profile http://hl7.org/cda/stds/core/StructureDefinition/Act does not have an identifier",
        "CarePlanAct: Profile http://hl7.org/cda/stds/core/StructureDefinition/Observation does not have an identifier",
        "Failed to expand value set v3-ActPriority: Unable to expand dependent value set http://terminology.hl7.org/ValueSet/v3-ActPriorityCallback");
    static final String REVIEWED_REPORT_SHA256="771691165a6d18ae46da5f1c70af17b11dc95a06a4a3d8c184b7fa6dd76217ad";
    static JsonObject review(Path assets,JsonObject report,CoverageCatalog catalog) throws Exception {
        var unresolved=new JsonArray();var replaced=new JsonArray();
        byte[] source=Files.readAllBytes(assets.resolve("ccda-5.0/CCDA-5.0-Results.json"));
        String digest=HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(source));
        if(!digest.equals(REVIEWED_REPORT_SHA256)||!report.equals(JsonParser.parseString(new String(source,java.nio.charset.StandardCharsets.UTF_8))))unresolved.add("Generation report changed; its replacement checks require a new review");
        var bound=new HashSet<String>();for(var binding:catalog.bindings)bound.add(binding.canonical());
        // These two sets occur in invariants rather than ordinary binding entries.
        bound.add(TerminologySnapshots.PROCEDURE);bound.add(CoverageCatalog.CMS);
        for(var entry:report.entrySet()) {
            switch(entry.getKey()) {
                case "errors" -> {for(var error:entry.getValue().getAsJsonArray()){
                    String text=error.getAsString();
                    if(ERRORS.contains(text)&&(text.startsWith("CarePlanAct:")||bound.contains(CoverageCatalog.PRIORITY)))replaced.add(text);else unresolved.add(text);
                }}
                case "unhandledInvariants" -> {for(var group:entry.getValue().getAsJsonObject().entrySet())for(var value:group.getValue().getAsJsonArray()){
                    String key=value.getAsJsonObject().get("key").getAsString();if(INVARIANTS.contains(key))replaced.add(key);else unresolved.add("Unhandled invariant: "+key);
                }}
                case "nonLoadedValueSets" -> {for(var group:entry.getValue().getAsJsonObject().entrySet())for(var value:group.getValue().getAsJsonArray()){
                    String canonical=value.getAsString().split(" ")[0];if(bound.contains(canonical))replaced.add(canonical);else unresolved.add("Unimplemented binding: "+canonical);
                }}
                case "notices" -> { /* informational notices do not assert omitted checks */ }
                case "skippedTemplates" -> {for(var template:entry.getValue().getAsJsonArray())unresolved.add("Skipped template: "+template);}
                default -> unresolved.add("Unreviewed generation report category: "+entry.getKey());
            }
        }
        var result=new JsonObject();result.addProperty("knownGeneratorGapsAddressed",unresolved.isEmpty());result.add("unresolvedGeneratorEntries",unresolved);result.add("reviewedReplacements",replaced);
        result.addProperty("scope","Pinned CDA XSD and C-CDA Schematron with reviewed supplements. This is not full FHIR StructureDefinition validation or clinical certification.");
        result.addProperty("documentPolicy","Missing datasets and indeterminate checks produce coverage findings at their applicable document locations, even when advisory warnings are hidden. Unrelated absent optional bindings do not block document completion.");
        result.addProperty("carePlanReview","The three profile unions include the unprofiled base CDA Act or Observation. No new templateId is required. Pinned base-root cardinalities, fixed values, required bindings and II constraints supplement XSD; identified narrower templates retain their own Schematron checks.");
        return result;
    }
}
