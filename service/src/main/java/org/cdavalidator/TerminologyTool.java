package org.cdavalidator;

import com.google.gson.*;
import java.nio.file.*;
import java.security.*;
import java.util.*;
import java.io.*;

/** Offline operator commands; no credentials, network requests, or license acceptance. */
public final class TerminologyTool {
    public static void main(String[] args) throws Exception {
        Path assets=Path.of(System.getenv().getOrDefault("ASSET_DIR","service/assets"));
        Path directory=Path.of(System.getenv().getOrDefault("TERMINOLOGY_DIR","terminology"));
        if(args.length==1 && args[0].equals("doctor")) {
            var engine=new ValidationEngine(assets,directory);
            var profiles=engine.profileInfo();boolean ready=true;
            for(var p:profiles)if(p.has("terminologySetup")&&!p.getAsJsonObject("terminologySetup").get("ready").getAsBoolean())ready=false;
            System.out.println(new GsonBuilder().setPrettyPrinting().create().toJson(Map.of("terminologyReady",ready,"profiles",profiles)));
            if(!ready)System.exit(2);
            return;
        }
        if(args.length==2 && args[0].equals("install-legacy")) {
            installLegacy(assets,directory,Path.of(args[1]));
            System.out.println("Pinned C-CDA 2.1 vocabulary installed locally. Restart the service to load it.");return;
        }
        System.err.println("Usage: TerminologyTool doctor | install-legacy PATH_TO_AUTHORIZED_VOC_XML");System.exit(2);
    }
    static void installLegacy(Path assets,Path directory,Path source) throws Exception {
        var manifest=JsonParser.parseString(Files.readString(assets.resolve("manifest.json"))).getAsJsonObject();
        JsonObject expected=null;
        for(var p:manifest.getAsJsonArray("profiles"))if(p.getAsJsonObject().has("operatorVocabulary"))expected=p.getAsJsonObject().getAsJsonObject("operatorVocabulary");
        String hash;
        if(expected!=null)hash=expected.get("sha256").getAsString();
        else hash=JsonParser.parseString(Files.readString(assets.resolve("ccda-2.1/.files.json"))).getAsJsonObject().get("voc.xml").getAsString();
        if(Files.size(source)>150L*1024*1024)throw new IOException("Vocabulary exceeds supported size");
        var digest=MessageDigest.getInstance("SHA-256");
        try(var input=new DigestInputStream(Files.newInputStream(source),digest)){input.transferTo(OutputStream.nullOutputStream());}
        if(!HexFormat.of().formatHex(digest.digest()).equals(hash))throw new IOException("Vocabulary does not match the pinned C-CDA 2.1 edition");
        Files.createDirectories(directory);
        Path temporary=Files.createTempFile(directory,"legacy-vocabulary-",".tmp");
        try {Files.copy(source,temporary,StandardCopyOption.REPLACE_EXISTING);
            try{Files.setPosixFilePermissions(temporary,java.nio.file.attribute.PosixFilePermissions.fromString("rw-r--r--"));}catch(UnsupportedOperationException ignored){}
            Files.move(temporary,directory.resolve("ccda-2.1-voc.xml"),StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);}
        finally {Files.deleteIfExists(temporary);}
    }
}
