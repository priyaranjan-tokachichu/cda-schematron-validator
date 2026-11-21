package org.cdavalidator;

import org.w3c.dom.*;
import java.util.*;
import java.util.regex.*;

/** The pinned generator serializes vocabulary codes as space-delimited literal lets. */
final class VocabularyAssertions {
    private static final Pattern MEMBERSHIP=Pattern.compile("contains\\(\\s*(\\$[A-Za-z_][A-Za-z0-9_.-]*)\\s*,\\s*(\\.|@[A-Za-z_][A-Za-z0-9_:.-]*)\\s*\\)");
    // These pinned finite identifier domains cannot contain whitespace. The
    // first two also occur inside predicates, where dot refers to an inner node.
    private static final Set<String> WHITESPACE_FREE=Set.of("$UspsTwoLetterAlphabeticCodes","$INDRoleclassCodes","$ActStatus");
    static int normalize(Document doc) {
        var variables=new HashMap<String,String>();var lets=doc.getElementsByTagNameNS(ValidationEngine.SCH,"let");
        var tokenLets=new ArrayList<Element>();
        for(int i=0;i<lets.getLength();i++){
            Element let=(Element)lets.item(i);String value=let.getAttribute("value");
            if(value.length()>=2&&value.startsWith("'")&&value.endsWith("'")) {
                String name=let.getAttribute("name"),tokens="local_vocabulary_tokens_"+name;
                variables.put("$"+name,"$"+tokens);
                Element tokenLet=doc.createElementNS(ValidationEngine.SCH,"let");tokenLet.setAttribute("name",tokens);tokenLet.setAttribute("value","tokenize($"+name+", '\\s+')");tokenLets.add(tokenLet);
            }
        }
        for(Element tokenLet:tokenLets)doc.getDocumentElement().appendChild(tokenLet);
        // Snapshot first: extra coverage assertions must not enter this traversal.
        var nodes=doc.getElementsByTagNameNS(ValidationEngine.SCH,"assert");var assertions=new ArrayList<Element>();
        for(int i=0;i<nodes.getLength();i++)assertions.add((Element)nodes.item(i));
        int changed=0;
        for(Element assertion:assertions) {
            Element rule=(Element)assertion.getParentNode();String context=rule.getAttribute("context");
            var matcher=MEMBERSHIP.matcher(assertion.getAttribute("test"));var replacement=new StringBuilder();var ambiguous=new LinkedHashSet<String>();boolean rewritten=false;
            while(matcher.find()) {
                if(!variables.containsKey(matcher.group(1)))continue;
                String variable=variables.get(matcher.group(1)),value=matcher.group(2);
                boolean list=value.equals("@use")||value.equals("@qualifier")||value.equals(".")&&(context.endsWith("/@use")||context.endsWith("/@qualifier"));
                String exact;
                if(list)exact="(normalize-space("+value+") != '' and (every $vocabularyToken in tokenize(normalize-space("+value+"), '\\s+') satisfies $vocabularyToken = "+variable+"))";
                else if(WHITESPACE_FREE.contains(matcher.group(1)))exact="("+value+" = "+variable+")";
                else {
                    if(value.equals(".")&&!assertion.getAttribute("test").strip().equals(matcher.group().strip()))throw new IllegalArgumentException("Unreviewed predicate membership context; review coverage normalization");
                    String whitespace="matches(string("+value+"), '\\s')";
                    // An inline list cannot distinguish a code containing spaces
                    // from adjacent codes. Disclose that case, do not guess a failure.
                    exact="("+whitespace+" or ("+value+" = "+variable+"))";
                    ambiguous.add("not("+whitespace+")");
                }
                matcher.appendReplacement(replacement,Matcher.quoteReplacement(exact));rewritten=true;
            }
            if(!rewritten)continue;
            matcher.appendTail(replacement);assertion.setAttribute("test",replacement.toString());changed++;
            if(!ambiguous.isEmpty()) {
                Element coverage=doc.createElementNS(ValidationEngine.SCH,"assert");coverage.setAttribute("id",assertion.getAttribute("id")+"-inline-vocabulary-coverage");coverage.setAttribute("role","coverage");
                coverage.setAttribute("test","@nullFlavor or ("+String.join(" and ",ambiguous)+")");
                coverage.setTextContent("A whitespace-bearing code cannot be checked unambiguously against the generated inline vocabulary. This check has incomplete coverage.");rule.appendChild(coverage);
            }
        }
        return changed;
    }
}
