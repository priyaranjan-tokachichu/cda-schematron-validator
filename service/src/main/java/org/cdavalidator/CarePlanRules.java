package org.cdavalidator;

import com.google.gson.*;
import org.w3c.dom.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;

/** Restore the three base-profile alternatives without inventing template identifiers. */
final class CarePlanRules {
    record Scope(String name,String type,String path) {}
    static JsonObject add(Document doc) throws Exception {
        byte[] bytes;
        try(var input=Objects.requireNonNull(CarePlanRules.class.getResourceAsStream("/coverage/careplan-base.json"))){bytes=input.readAllBytes();}
        var model=JsonParser.parseString(new String(bytes,StandardCharsets.UTF_8)).getAsJsonObject();
        String root=null;
        var rules=doc.getElementsByTagNameNS(ValidationEngine.SCH,"rule");
        for(int i=0;i<rules.getLength();i++){
            Element rule=(Element)rules.item(i);
            if(rule.getAttribute("id").equals("CarePlanAct-errors-root"))root=rule.getAttribute("context");
        }
        if(root==null)throw new IllegalArgumentException("Pinned CarePlan root changed; review base-profile supplement");
        Element ns=doc.createElementNS(ValidationEngine.SCH,"ns");ns.setAttribute("prefix","sdtc");ns.setAttribute("uri","urn:hl7-org:sdtc");doc.getDocumentElement().appendChild(ns);
        int assertions=0;
        for(var scope:List.of(new Scope("addresses-act","Act","/cda:entryRelationship[@typeCode='RSON']/cda:act"),
                new Scope("addresses-observation","Observation","/cda:entryRelationship[@typeCode='RSON']/cda:observation"),
                new Scope("activity-act","Act","/cda:entryRelationship[@typeCode='COMP']/cda:act"))) {
            String context=root+scope.path;
            Element pattern=element(doc,"pattern");pattern.setAttribute("id","careplan-base-"+scope.name);doc.getDocumentElement().appendChild(pattern);
            for(var value:model.getAsJsonObject("profiles").getAsJsonArray(scope.type)) {
                var field=value.getAsJsonObject();String path=field.get("path").getAsString(),parent=field.get("parent").getAsString();
                String target=parent.equals(".")?path:path.substring(parent.length()+1);
                String id="careplan-base-"+scope.name+"-"+field.get("id").getAsString().replace('.','-');
                int min=field.get("min").getAsInt();String max=field.get("max").getAsString();
                if(min>0||!max.equals("*")) {
                    String test="count("+target+") >= "+min+(max.equals("*")?"":" and count("+target+") <= "+max);
                    add(doc,pattern,join(context,parent),id+"-cardinality","error",test,"Base "+field.get("id").getAsString()+" cardinality is "+min+".."+max+".");assertions++;
                }
                if(field.has("fixed")){
                    add(doc,pattern,join(context,parent),id+"-fixed","error","not("+target+") or "+target+" = "+literal(field.get("fixed").getAsString()),"Base "+field.get("id").getAsString()+" has a fixed value.");assertions++;
                }
                if(field.has("iiRootOrNullFlavor")){
                    add(doc,pattern,join(context,path),id+"-II-1","error","@root or @nullFlavor","An II instance requires root or nullFlavor.");assertions++;
                }
                if(field.has("binding")){
                    boolean attribute=field.get("attribute").getAsBoolean();String code=attribute?".":"@code";
                    String bypass=attribute?"":"@nullFlavor or not(@code) or ";
                    String bindingContext=join(context,path);
                    if(field.has("grammar")){
                        String status="local-term:grammar-status('http://hl7.org/fhir/ValueSet/all-languages',string("+code+"))";
                        add(doc,pattern,bindingContext,id+"-binding","error",bypass+status+" != 'false'","Language code must be a valid registered BCP 47 tag.");
                        add(doc,pattern,bindingContext,id+"-coverage","coverage",bypass+status+" != 'unknown'","Language grammar is unavailable or indeterminate; this CarePlan base check has incomplete coverage.");assertions+=2;
                    }else{
                        var matches=new ArrayList<String>();
                        for(var member:field.getAsJsonArray("members")){
                            var m=member.getAsJsonObject();String test=code+" = "+literal(m.get("code").getAsString());
                            if(!field.get("primitive").getAsBoolean())test="("+test+" and @codeSystem = "+literal(m.get("system").getAsString())+")";
                            matches.add(test);
                        }
                        add(doc,pattern,bindingContext,id+"-binding","error",bypass+"("+String.join(" or ",matches)+")","Value must belong to the pinned base-profile required binding "+field.get("binding").getAsString()+".");assertions++;
                    }
                }
            }
        }
        var result=new JsonObject();result.addProperty("assertions",assertions);result.addProperty("modelSha256",HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)));
        result.add("sourceFiles",model.get("packageSources"));result.add("scope",model.get("scope"));return result;
    }
    private static String join(String root,String path){return path.equals(".")?root:root+"/"+path;}
    private static String literal(String value){if(value.contains("'"))throw new IllegalArgumentException("Unreviewed XPath literal");return "'"+value+"'";}
    private static Element element(Document doc,String name){return doc.createElementNS(ValidationEngine.SCH,name);}
    private static void add(Document doc,Element pattern,String context,String id,String role,String test,String message){
        // Merge assertions with the same context. Separate rules with identical
        // matches would shadow one another under Schematron rule priority.
        Element rule=null;var rules=pattern.getElementsByTagNameNS(ValidationEngine.SCH,"rule");
        for(int i=0;i<rules.getLength();i++)if(((Element)rules.item(i)).getAttribute("context").equals(context)){rule=(Element)rules.item(i);break;}
        if(rule==null){rule=element(doc,"rule");rule.setAttribute("context",context);pattern.appendChild(rule);}
        Element assertion=element(doc,"assert");assertion.setAttribute("id",id);assertion.setAttribute("role",role);assertion.setAttribute("test",test);assertion.setTextContent(message);rule.appendChild(assertion);
    }
}
