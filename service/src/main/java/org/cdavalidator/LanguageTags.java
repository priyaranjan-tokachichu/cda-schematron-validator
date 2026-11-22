package org.cdavalidator;

import java.util.*;

/** BCP 47 syntax plus registered subtags/extensions from operator-pinned IANA files. */
final class LanguageTags {
    final Map<String,Set<String>> subtags=new HashMap<>();
    final Map<String,Set<String>> extlangPrefixes=new HashMap<>();
    final Map<String,List<String>> ranges=new HashMap<>();
    final Set<String> wholeTags=new HashSet<>(), extensions=new HashSet<>();
    LanguageTags(String registry,String extensionRegistry) {
        for(var record:records(registry)) {
            String type=record.get("Type"), subtag=record.get("Subtag"), tag=record.get("Tag");
            if(type==null)continue;
            if(tag!=null)wholeTags.add(tag.toLowerCase(Locale.ROOT));
            if(subtag!=null){
                subtag=subtag.toLowerCase(Locale.ROOT);
                if(subtag.contains(".."))ranges.computeIfAbsent(type,k->new ArrayList<>()).add(subtag);
                else subtags.computeIfAbsent(type,k->new HashSet<>()).add(subtag);
            }
            if("extlang".equals(type)&&subtag!=null&&record.containsKey("Prefix"))extlangPrefixes.put(subtag.toLowerCase(Locale.ROOT),Set.of(record.get("Prefix").toLowerCase(Locale.ROOT).split("\\|")));
        }
        for(var record:records(extensionRegistry))if(record.containsKey("Identifier"))extensions.add(record.get("Identifier").toLowerCase(Locale.ROOT));
        if(subtags.getOrDefault("language",Set.of()).isEmpty()||extensions.isEmpty())throw new IllegalArgumentException("Incomplete IANA registry data");
    }
    static List<Map<String,String>> records(String text) {
        var result=new ArrayList<Map<String,String>>();
        for(String block:text.split("(?m)^%%\\s*$")) {
            var record=new HashMap<String,String>();String previous=null;
            for(String line:block.split("\\R")) {
                if(line.startsWith("  ")&&previous!=null){record.put(previous,record.get(previous)+line.strip());continue;}
                int split=line.indexOf(':');if(split<1)continue;
                String key=line.substring(0,split), value=line.substring(split+1).strip();
                record.merge(key,value,(a,b)->a+"|"+b);previous=key;
            }
            result.add(record);
        }
        return result;
    }
    boolean registered(String type,String value) {
        if(subtags.getOrDefault(type,Set.of()).contains(value))return true;
        for(String entry:ranges.getOrDefault(type,List.of())) {
            int split=entry.indexOf("..");
            if(split>0&&value.length()==split&&value.compareTo(entry.substring(0,split))>=0&&value.compareTo(entry.substring(split+2))<=0)return true;
        }
        return false;
    }
    boolean valid(String input) {
        if(input.isEmpty()||input.length()>255)return false;
        String tag=input.toLowerCase(Locale.ROOT);
        if(wholeTags.contains(tag))return true;
        try{new Locale.Builder().setLanguageTag(tag);}catch(IllformedLocaleException e){return false;}
        String[] p=tag.split("-");if(p[0].equals("x"))return true;
        if(!registered("language",p[0]))return false;
        int i=1;
        if(p[0].length()<=3)while(i<p.length&&p[i].matches("[a-z]{3}")) {
            String prefix=String.join("-",Arrays.copyOf(p,i));
            if(!registered("extlang",p[i])||!extlangPrefixes.getOrDefault(p[i],Set.of()).contains(prefix))return false;i++;
        }
        if(i<p.length&&p[i].matches("[a-z]{4}")){if(!registered("script",p[i++]))return false;}
        if(i<p.length&&p[i].matches("[a-z]{2}|[0-9]{3}")){if(!registered("region",p[i++]))return false;}
        var variants=new HashSet<String>();
        while(i<p.length&&p[i].length()>1) {if(!registered("variant",p[i])||!variants.add(p[i]))return false;i++;}
        var singletons=new HashSet<String>();
        while(i<p.length) {
            String singleton=p[i++];if(singleton.equals("x"))return true;
            if(!extensions.contains(singleton)||!singletons.add(singleton))return false;
            int start=i;while(i<p.length&&p[i].length()>1)i++;
            if(start==i)return false;
        }
        return true;
    }
}
