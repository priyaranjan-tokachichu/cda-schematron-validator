package org.cdavalidator;

import com.google.gson.*;
import net.sf.saxon.s9api.*;
import org.fhir.ucum.UcumEssenceService;
import java.io.*;
import java.nio.file.*;
import java.security.*;
import java.util.*;

final class GrammarChecks {
    static final String LANGUAGE="http://hl7.org/fhir/ValueSet/all-languages";
    static final String UCUM="http://terminology.hl7.org/ValueSet/v3-UnitsOfMeasureCaseSensitive";
    static final Set<String> CANONICALS=Set.of(LANGUAGE,UCUM);
    final UcumEssenceService ucum;
    final LanguageTags languages;
    final JsonObject metadata;
    private GrammarChecks(UcumEssenceService ucum,LanguageTags languages,JsonObject metadata){this.ucum=ucum;this.languages=languages;this.metadata=metadata;}
    static Path verified(Path directory,JsonObject info,String filename,String checksum) throws Exception {
        Path file=directory.resolve(info.get(filename).getAsString()).toRealPath();
        if(!file.startsWith(directory.toRealPath())||Files.size(file)>5*1024*1024)throw new IOException("Invalid grammar definition path or size");
        var digest=MessageDigest.getInstance("SHA-256");
        try(var input=new DigestInputStream(Files.newInputStream(file),digest)){input.transferTo(OutputStream.nullOutputStream());}
        if(!HexFormat.of().formatHex(digest.digest()).equals(info.get(checksum).getAsString()))throw new IOException("Grammar definition checksum mismatch");
        if(!info.has("version")||info.get("version").getAsString().isBlank())throw new IOException("Grammar edition is required");
        return file;
    }
    static GrammarChecks load(Path directory) throws Exception {
        Path file=directory.resolve("manifest.json");
        if(!Files.exists(file))return new GrammarChecks(null,null,new JsonObject());
        if(Files.size(file)>2*1024*1024)throw new IOException("Invalid grammar manifest size");
        var config=JsonParser.parseString(Files.readString(file)).getAsJsonObject();
        var grammars=config.has("grammars")?config.getAsJsonObject("grammars"):new JsonObject();
        UcumEssenceService ucum=null;LanguageTags languages=null;var metadata=new JsonObject();
        if(grammars.has("ucum")) {
            var info=grammars.getAsJsonObject("ucum");Path definition=verified(directory,info,"file","sha256");
            ValidationEngine.trustedSchematron(definition); // Explicitly prohibit DTDs before the UCUM parser sees the file.
            try(var input=Files.newInputStream(definition)){ucum=new UcumEssenceService(input);}
            if(!info.get("version").getAsString().equals(ucum.ucumIdentification().getVersion()))throw new IOException("UCUM edition does not match its manifest");
            var visible=info.deepCopy();visible.remove("file");metadata.add("ucum",visible);
        }
        if(grammars.has("language")) {
            var info=grammars.getAsJsonObject("language");Path subtags=verified(directory,info,"file","sha256"),extensions=verified(directory,info,"extensionsFile","extensionsSha256");
            languages=new LanguageTags(Files.readString(subtags),Files.readString(extensions));
            String date=LanguageTags.records(Files.readString(subtags)).stream().map(r->r.get("File-Date")).filter(Objects::nonNull).findFirst().orElse("");
            if(!info.get("version").getAsString().equals(date))throw new IOException("IANA edition does not match its manifest");
            var visible=info.deepCopy();visible.remove("file");visible.remove("extensionsFile");metadata.add("language",visible);
        }
        return new GrammarChecks(ucum,languages,metadata);
    }
    boolean supports(String canonical){return canonical.equals(UCUM)?ucum!=null:canonical.equals(LANGUAGE)&&languages!=null;}
    boolean valid(String canonical,String code) {
        return status(canonical,code).equals("true");
    }
    String status(String canonical,String code) {
        if(code.isEmpty())return "false";
        if(!supports(canonical)||code.length()>1024||canonical.equals(LANGUAGE)&&code.length()>255)return "unknown";
        try {
            if(canonical.equals(UCUM)) {
                int depth=0;boolean annotation=false;
                for(char c:code.toCharArray()){
                    if(annotation){if(c=='}')annotation=false;continue;}
                    if(c=='{'){annotation=true;continue;}
                    if(c=='('&&++depth>64)return "unknown";
                    if(c==')'&&--depth<0)return "false";
                }
                return Boolean.toString(ucum.validate(code)==null);
            }
            if(!languages.valid(code))return "false";
            // IANA registers extension singletons, not their individual key/type values.
            // Without each extension authority's data, do not assert semantic validity.
            String[] parts=code.split("-");
            for(int i=1;i<parts.length;i++) {
                if(parts[i].equalsIgnoreCase("x"))break;
                if(parts[i].length()==1)return "unknown";
            }
            return "true";
        } catch(RuntimeException|StackOverflowError e){return "unknown";}
    }
    void register(Processor processor) {
        processor.registerExtensionFunction(new ExtensionFunction(){
            public QName getName(){return new QName("local-term",TerminologySnapshots.NAMESPACE,"grammar-status");}
            public SequenceType getResultType(){return SequenceType.makeSequenceType(ItemType.STRING,OccurrenceIndicator.ONE);}
            public SequenceType[] getArgumentTypes(){var s=SequenceType.makeSequenceType(ItemType.STRING,OccurrenceIndicator.ONE);return new SequenceType[]{s,s};}
            public XdmValue call(XdmValue[] args){return new XdmAtomicValue(status(args[0].itemAt(0).getStringValue(),args[1].itemAt(0).getStringValue()));}
        });
    }
}
