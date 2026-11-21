package org.cdavalidator;

import com.google.gson.*;
import com.google.gson.stream.*;
import net.sf.saxon.s9api.*;
import java.io.*;
import java.nio.file.*;
import java.security.*;
import java.util.*;

/** Optional local, checksum-pinned membership indexes; no network or uploaded executable rules. */
final class TerminologySnapshots {
    static final String NAMESPACE = "urn:cdavalidator:terminology";
    static final String PROCEDURE = "http://cts.nlm.nih.gov/fhir/ValueSet/2.16.840.1.113883.3.88.12.80.28";
    final Map<String, Set<String>> members;
    final Map<String,Set<String>> codeMembers;
    final JsonArray metadata;
    final Map<String,Set<String>> systemMembers;
    final Map<String,Map<String,Set<String>>> editions = new HashMap<>();
    final Map<String,Set<String>> expressionSystems = new HashMap<>();
    final Set<String> modifierScopes = new HashSet<>();
    private TerminologySnapshots(Map<String,Set<String>> members, JsonArray metadata) {
        this.members = Map.copyOf(members); this.metadata = metadata;
        var codeIndex=new HashMap<String,Set<String>>();var systemIndex=new HashMap<String,Set<String>>();
        members.forEach((canonical,values)->{var codes=new HashSet<String>();var systems=new HashSet<String>();for(String key:values){int split=key.indexOf('|');codes.add(key.substring(split+1));systems.add(key.substring(0,split));}codeIndex.put(canonical,Set.copyOf(codes));systemIndex.put(canonical,Set.copyOf(systems));});
        systemMembers=Map.copyOf(systemIndex);
        codeMembers=Map.copyOf(codeIndex);
        for(var entry:metadata) {
            var info=entry.getAsJsonObject();
            if(info.has("standaloneModifiersIndeterminate")&&info.get("standaloneModifiersIndeterminate").getAsBoolean())modifierScopes.add(info.get("canonical").getAsString());
            var bySystem=new HashMap<String,Set<String>>();
            JsonObject declared=info.has("codeSystemEditions")?info.getAsJsonObject("codeSystemEditions"):info.has("codeSystems")?info.getAsJsonObject("codeSystems"):new JsonObject();
            for(var system:declared.entrySet())if(system.getKey().matches("[0-9]+(?:\\.[0-9]+)+")){
                var versions=new HashSet<String>();for(var version:system.getValue().getAsJsonArray())if(!version.getAsString().equals("unspecified"))versions.add(version.getAsString());bySystem.put(system.getKey(),Set.copyOf(versions));
            }
            editions.put(info.get("canonical").getAsString(),Map.copyOf(bySystem));
            if(info.has("unsupportedExpressions")) {
                var systems=new HashSet<String>();for(var system:info.getAsJsonArray("unsupportedExpressions"))systems.add(system.getAsString());
                expressionSystems.put(info.get("canonical").getAsString(),Set.copyOf(systems));
            }
        }
    }
    static TerminologySnapshots load(Path assets) throws Exception {
        return load(assets,Set.of(PROCEDURE));
    }
    static TerminologySnapshots load(Path assets,Set<String> activeCanonicals) throws Exception {
        return loadDirectory(assets.resolve("terminology"),activeCanonicals);
    }
    static TerminologySnapshots loadDirectory(Path directory,Set<String> activeCanonicals) throws Exception {
        Path manifest = directory.resolve("manifest.json");
        if (!Files.exists(manifest)) return new TerminologySnapshots(Map.of(), new JsonArray());
        directory = directory.toRealPath();
        if (!manifest.toRealPath().startsWith(directory) || Files.size(manifest)>2*1024*1024) throw new IOException("Invalid terminology manifest");
        var config = JsonParser.parseString(Files.readString(manifest)).getAsJsonObject();
        if(config.get("schemaVersion").getAsInt()!=1)throw new IOException("Unsupported terminology snapshot format");
        var result = new LinkedHashMap<String,Set<String>>();
        var metadata = new JsonArray();
        var seen=new HashSet<String>();
        long bytes=0, concepts=0;
        for(var entry:config.getAsJsonArray("valueSets")) {
            var info=entry.getAsJsonObject();
            String canonical=info.get("canonical").getAsString();
            if(!canonical.startsWith("http://") && !canonical.startsWith("https://")) throw new IOException("Invalid terminology canonical");
            if(!seen.add(canonical)) throw new IOException("Duplicate terminology snapshot canonical");
            Path file=directory.resolve(info.get("file").getAsString()).toRealPath();
            if(!file.startsWith(directory))throw new IOException("Invalid terminology snapshot path");
            bytes+=Files.size(file);
            int count=info.get("count").getAsInt(); concepts+=count;
            if(bytes>64L*1024*1024 || count<=0 || concepts>1_250_000)throw new IOException("Terminology pack exceeds supported bounds or is empty");
            if(concepts*256L>Runtime.getRuntime().maxMemory()/3)throw new IOException("Terminology indexes require a larger Java heap; increase heap and container memory before startup");
            var digest=MessageDigest.getInstance("SHA-256");
            try(var input=new DigestInputStream(Files.newInputStream(file),digest)){input.transferTo(OutputStream.nullOutputStream());}
            if(!HexFormat.of().formatHex(digest.digest()).equals(info.get("sha256").getAsString()))throw new IOException("Terminology snapshot checksum mismatch");
            var codes=new HashSet<String>();
            try(var reader=new JsonReader(Files.newBufferedReader(file))) {
                reader.setStrictness(Strictness.STRICT); reader.beginArray();
                while(reader.hasNext()) {
                    String key=reader.nextString(); int split=key.indexOf('|');
                    if(key.length()>1024 || split<=0 || split==key.length()-1 || !key.substring(0,split).matches("[0-9]+(?:\\.[0-9]+)+") || key.chars().anyMatch(Character::isISOControl)) throw new IOException("Invalid terminology membership key");
                    if(!codes.add(key) || codes.size()>count)throw new IOException("Duplicate or excessive terminology concepts");
                }
                reader.endArray();
                if(reader.peek()!=JsonToken.END_DOCUMENT)throw new IOException("Trailing terminology snapshot content");
            }
            if(codes.size()!=count)throw new IOException("Terminology concept count mismatch");
            // Only retain indexes with implemented, reviewed checks. Other imported
            // snapshots are verified and disclosed, but their presence is not coverage.
            boolean active = activeCanonicals.contains(canonical);
            if (active) result.put(canonical,Set.copyOf(codes));
            var publicInfo=info.deepCopy();publicInfo.remove("file");publicInfo.addProperty("activeForValidation",active);publicInfo.addProperty("distribution","Local terminology pack; not included in the public image");metadata.add(publicInfo);
        }
        return new TerminologySnapshots(result,metadata);
    }
    boolean contains(String canonical,String system,String code) {
        return members.getOrDefault(canonical,Set.of()).contains(system+'|'+code);
    }
    String status(String canonical,String system,String code) {
        if(!members.containsKey(canonical))return "unknown";
        if(modifierScopes.contains(canonical)&&systemMembers.getOrDefault(canonical,Set.of()).contains(system)&&Set.of("2.16.840.1.113883.6.12","2.16.840.1.113883.6.285").contains(system)&&code.matches("[A-Z0-9]{2}"))return "unknown";
        if(contains(canonical,system,code))return "true";
        // A pre-coordinated hierarchy index cannot decide subsumption of a
        // SNOMED compositional expression. Never turn that into a false failure.
        if(systemMembers.getOrDefault(canonical,Set.of()).contains(system)&&(system.equals("2.16.840.1.113883.6.96")||expressionSystems.getOrDefault(canonical,Set.of()).contains(system))&&code.matches("(?s).*[:+={}|].*"))return "unknown";
        return "false";
    }
    String status(String canonical,String system,String code,String version) {
        String result=status(canonical,system,code);
        if(result.equals("unknown")||version.isBlank()||!systemMembers.getOrDefault(canonical,Set.of()).contains(system))return result;
        return editions.getOrDefault(canonical,Map.of()).getOrDefault(system,Set.of()).contains(version)?result:"unknown";
    }
    String codeStatus(String canonical,String code) {
        if(!members.containsKey(canonical))return "unknown";
        if(modifierScopes.contains(canonical)&&!Collections.disjoint(systemMembers.getOrDefault(canonical,Set.of()),Set.of("2.16.840.1.113883.6.12","2.16.840.1.113883.6.285"))&&code.matches("[A-Z0-9]{2}"))return "unknown";
        if(codeMembers.get(canonical).contains(code))return "true";
        if(!expressionSystems.getOrDefault(canonical,Set.of()).isEmpty()&&code.matches("(?s).*[:+={}|].*"))return "unknown";
        return "false";
    }
    void register(Processor processor) {
        for(String name:List.of("membership-status","code-membership-status","versioned-membership-status"))processor.registerExtensionFunction(new ExtensionFunction() {
            public QName getName(){return new QName("local-term",NAMESPACE,name);}
            public SequenceType getResultType(){return SequenceType.makeSequenceType(ItemType.STRING,OccurrenceIndicator.ONE);}
            public SequenceType[] getArgumentTypes(){var s=SequenceType.makeSequenceType(ItemType.STRING,OccurrenceIndicator.ONE);return name.equals("versioned-membership-status")?new SequenceType[]{s,s,s,s}:name.equals("membership-status")?new SequenceType[]{s,s,s}:new SequenceType[]{s,s};}
            public XdmValue call(XdmValue[] args){String canonical=args[0].itemAt(0).getStringValue();return new XdmAtomicValue(name.equals("versioned-membership-status")?status(canonical,args[1].itemAt(0).getStringValue(),args[2].itemAt(0).getStringValue(),args[3].itemAt(0).getStringValue()):name.equals("membership-status")?status(canonical,args[1].itemAt(0).getStringValue(),args[2].itemAt(0).getStringValue()):codeStatus(canonical,args[1].itemAt(0).getStringValue()));}
        });
        processor.registerExtensionFunction(new ExtensionFunction() {
            public QName getName(){return new QName("local-term",NAMESPACE,"member-of");}
            public SequenceType getResultType(){return SequenceType.makeSequenceType(ItemType.BOOLEAN,OccurrenceIndicator.ONE);}
            public SequenceType[] getArgumentTypes(){var string=SequenceType.makeSequenceType(ItemType.STRING,OccurrenceIndicator.ONE);return new SequenceType[]{string,string,string};}
            public XdmValue call(XdmValue[] args){return new XdmAtomicValue(contains(args[0].itemAt(0).getStringValue(),args[1].itemAt(0).getStringValue(),args[2].itemAt(0).getStringValue()));}
        });
        processor.registerExtensionFunction(new ExtensionFunction() {
            public QName getName(){return new QName("local-term",NAMESPACE,"code-member-of");}
            public SequenceType getResultType(){return SequenceType.makeSequenceType(ItemType.BOOLEAN,OccurrenceIndicator.ONE);}
            public SequenceType[] getArgumentTypes(){var s=SequenceType.makeSequenceType(ItemType.STRING,OccurrenceIndicator.ONE);return new SequenceType[]{s,s};}
            public XdmValue call(XdmValue[] args){return new XdmAtomicValue(codeMembers.getOrDefault(args[0].itemAt(0).getStringValue(),Set.of()).contains(args[1].itemAt(0).getStringValue()));}
        });
    }
}
