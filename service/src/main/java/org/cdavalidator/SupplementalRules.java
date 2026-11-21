package org.cdavalidator;

import org.w3c.dom.*;
import java.util.*;

/** Reviewed local checks layered on the in-memory copy; original HL7 files stay immutable. */
final class SupplementalRules {
    private SupplementalRules() {}
    static final String NAME_INVARIANT = "81-9371";

    static void addTimestampInvariants(Document doc) {
        Element ns=doc.createElementNS(ValidationEngine.SCH,"ns");
        ns.setAttribute("prefix","local-ts");ns.setAttribute("uri",CdaTimestamp.NS);doc.getDocumentElement().appendChild(ns);
        Element root=rule(pattern(doc,"ProcedureActivityProcedure-errors").getElementsByTagNameNS(ValidationEngine.SCH,"rule"),"ProcedureActivityProcedure-errors-root");
        String context=root.getAttribute("context");
        addTimeCheck(doc,context,"active-high-ts-after-document",
            "not(cda:statusCode/@code = 'active') or not(cda:effectiveTime/cda:high/@value)",
            "cda:effectiveTime/cda:high/@value","gt");
        addTimeCheck(doc,context+"/cda:effectiveTime","ts-value-before-document","not(@value)","@value","le");
        addTimeCheck(doc,context+"/cda:effectiveTime/cda:low","ts-value-before-document","not(@value)","@value","le");
    }
    private static void addTimeCheck(Document doc,String context,String id,String inactive,String value,String operator) {
        Element pattern=doc.createElementNS(ValidationEngine.SCH,"pattern");
        pattern.setAttribute("id","local-time-"+id+"-"+operator+"-"+(context.endsWith("/cda:low")?"low":"value"));
        Element rule=doc.createElementNS(ValidationEngine.SCH,"rule");rule.setAttribute("context",context);
        Element assertion=doc.createElementNS(ValidationEngine.SCH,"assert");assertion.setAttribute("id",id);assertion.setAttribute("role","error");
        assertion.setAttribute("test",inactive+" or (every $time in ("+value+") satisfies local-ts:compare(string($time), string(/cda:ClinicalDocument/cda:effectiveTime/@value), '"+operator+"') != 'false')");
        assertion.setTextContent(operator.equals("gt")?"Active procedure high time SHALL be after the document time.":"Procedure time SHALL be equal to or before the document time.");
        rule.appendChild(assertion);
        Element coverage=doc.createElementNS(ValidationEngine.SCH,"assert");coverage.setAttribute("id",id+"-comparison-coverage");coverage.setAttribute("role","coverage");
        coverage.setAttribute("test",inactive+" or (every $time in ("+value+") satisfies local-ts:compare(string($time), string(/cda:ClinicalDocument/cda:effectiveTime/@value), '"+operator+"') != 'unknown')");
        coverage.setTextContent("Timestamp comparison is indeterminate because precision, timezone information, or a usable timestamp is missing. This document has incomplete validation coverage.");
        rule.appendChild(coverage);pattern.appendChild(rule);doc.getDocumentElement().appendChild(pattern);
    }

    static void addPersonNameInvariant(Document doc) {
        Element patient = pattern(doc, "USRealmPatientNamePTNUSFIELDED-errors");
        Element person = pattern(doc, "USRealmPersonNamePNUSFIELDED-errors");
        var patientRules = patient.getElementsByTagNameNS(ValidationEngine.SCH, "rule");
        var personRules = person.getElementsByTagNameNS(ValidationEngine.SCH, "rule");
        Element patientRoot = rule(patientRules, "USRealmPatientNamePTNUSFIELDED-errors-root");
        Element personRoot = rule(personRules, "USRealmPersonNamePNUSFIELDED-errors-root");
        String rootContext = patientRoot.getAttribute("context");
        var tests = new ArrayList<String>();
        int assertions = 0;
        // These exact patterns are reviewed against the pinned 2026JUL artifact. Fail
        // closed if an upstream update changes the profile layout: do not guess paths.
        if (patientRules.getLength() != 6) throw new IllegalArgumentException("Patient-name profile changed; review supplemental rule");
        for (int i = 0; i < patientRules.getLength(); i++) {
            Element rule = (Element) patientRules.item(i);
            String context = rule.getAttribute("context");
            boolean root = context.equals(rootContext);
            if (!root && !context.startsWith(rootContext + "/")) throw new IllegalArgumentException("Unexpected patient-name context");
            String relative = root ? "." : context.substring(rootContext.length() + 1);
            var checks = rule.getElementsByTagNameNS(ValidationEngine.SCH, "assert");
            for (int j = 0; j < checks.getLength(); j++) {
                String test = ((Element) checks.item(j)).getAttribute("test");
                if (test.isBlank()) throw new IllegalArgumentException("Empty patient-name assertion");
                // CDA name use/qualifier attributes are whitespace-separated lists.
                // Compare complete tokens, never substrings of the vocabulary string.
                if (context.endsWith("/@use") || context.endsWith("/@qualifier")) {
                    String vocabulary = context.endsWith("/@use") ? "$CDAEntityNameUse" : "$EntityPersonNamePartQualifier";
                    if (!test.equals("contains(" + vocabulary + ", .)"))
                        throw new IllegalArgumentException("Name vocabulary constraint changed; review supplemental rule");
                    test = "normalize-space(.) != '' and (every $nameToken in tokenize(normalize-space(.), '\\s+') satisfies $nameToken = tokenize(" + vocabulary + ", '\\s+'))";
                } else if (test.equals("not(((cda:delimiter | cda:family | cda:given | cda:prefix | cda:suffix))[@qualifier[self::node() = 'LS']])")) {
                    test = "not((cda:delimiter | cda:family | cda:given | cda:prefix | cda:suffix)[tokenize(normalize-space(@qualifier), '\\s+') = 'LS'])";
                    ((Element)checks.item(j)).setAttribute("test",test);
                }
                tests.add(root ? "(" + test + ")" :
                    "(every $patientNameNode in (" + relative + ") satisfies exists($patientNameNode[(" + test + ")]))");
                assertions++;
            }
        }
        if (assertions != 11) throw new IllegalArgumentException("Patient-name constraints changed; review supplemental rule");
        Element assertion = doc.createElementNS(ValidationEngine.SCH, "assert");
        assertion.setAttribute("id", NAME_INVARIANT);
        assertion.setAttribute("role", "error");
        assertion.setAttribute("test", "text()[normalize-space()] or (" + String.join(" and ", tests) + ")");
        assertion.setTextContent("The content of name SHALL be either a conformant Patient Name (PTN.US.FIELDED), or a string (CONF:81-9371). Local supplemental check using the pinned patient-name profile.");
        personRoot.appendChild(assertion);
    }

    static void addProcedureInvariants(Document doc) {
        var namespace = doc.createElementNS(ValidationEngine.SCH, "ns");
        namespace.setAttribute("prefix", "local-term"); namespace.setAttribute("uri", TerminologySnapshots.NAMESPACE);
        doc.getDocumentElement().appendChild(namespace);
        for (String template : List.of("OperativeNote", "ProcedureNote")) {
            Element pattern = pattern(doc, template + "-errors");
            Element root = rule(pattern.getElementsByTagNameNS(ValidationEngine.SCH, "rule"), template + "-errors-root");
            Element rule = doc.createElementNS(ValidationEngine.SCH, "rule");
            rule.setAttribute("context", root.getAttribute("context") + "/cda:documentationOf/cda:serviceEvent/cda:code");
            rule.setAttribute("id", "local-" + template + "-procedure-code");
            Element assertion = doc.createElementNS(ValidationEngine.SCH, "assert");
            assertion.setAttribute("id", template.equals("OperativeNote") ? "1198-8487" : "1198-8511");
            assertion.setAttribute("role", "error");
            String alternatives = "@codeSystem = '2.16.840.1.113883.6.104' or @codeSystem = '2.16.840.1.113883.6.4' or @codeSystem = '2.16.840.1.113883.6.12'";
            if (template.equals("ProcedureNote")) alternatives += " or @codeSystem = '2.16.840.1.113883.6.285'";
            String membership="local-term:versioned-membership-status('"+TerminologySnapshots.PROCEDURE+"',string(@codeSystem),string(@code),string(@codeSystemVersion))";
            assertion.setAttribute("test", alternatives + " or (@codeSystem = '2.16.840.1.113883.6.96' and "+membership+" != 'false')");
            Element coverage=doc.createElementNS(ValidationEngine.SCH,"assert");coverage.setAttribute("id",assertion.getAttribute("id")+"-coverage");coverage.setAttribute("role","coverage");
            coverage.setAttribute("test",alternatives+" or not(@codeSystem = '2.16.840.1.113883.6.96') or "+membership+" != 'unknown'");
            coverage.setTextContent("Procedure membership could not be determined: install the Procedure dataset or use a supported pre-coordinated code. This check has incomplete coverage.");rule.appendChild(coverage);
            assertion.setTextContent("Service-event code SHALL use an allowed procedure code system; SNOMED CT codes SHALL belong to the pinned Procedure value-set expansion (CONF:" + assertion.getAttribute("id") + "). Local supplemental terminology check.");
            rule.appendChild(assertion); pattern.appendChild(rule);
        }
    }

    static void addLocationInvariant(Document doc) {
        Element original=pattern(doc,"ServiceDeliveryLocation-errors");
        Element root=rule(original.getElementsByTagNameNS(ValidationEngine.SCH,"rule"),"ServiceDeliveryLocation-errors-root");
        Element parent=doc.createElementNS(ValidationEngine.SCH,"pattern");parent.setAttribute("id","local-service-location");doc.getDocumentElement().appendChild(parent);
        Element rule=doc.createElementNS(ValidationEngine.SCH,"rule");
        rule.setAttribute("id","local-service-location-code");rule.setAttribute("context",root.getAttribute("context")+"/cda:code");
        Element assertion=doc.createElementNS(ValidationEngine.SCH,"assert");assertion.setAttribute("id","shall-use-one-binding");assertion.setAttribute("role","error");
        var alternatives=new ArrayList<String>();
        for(String canonical:CoverageCatalog.LOCATION)alternatives.add("local-term:versioned-membership-status('"+canonical+"',string(@codeSystem),string(@code),string(@codeSystemVersion))");
        String statuses="("+String.join(",",alternatives)+")";
        assertion.setAttribute("test","not(@code) or (some $membership in "+statuses+" satisfies $membership != 'false')");assertion.setTextContent("The location code SHALL belong to one of the three specified value sets.");
        Element coverage=doc.createElementNS(ValidationEngine.SCH,"assert");coverage.setAttribute("id","shall-use-one-binding-coverage");coverage.setAttribute("role","coverage");
        coverage.setAttribute("test","not(@code) or ("+statuses+" = 'true') or not("+statuses+" = 'unknown')");coverage.setTextContent("Location membership could not be determined because a required union dataset is missing. This check has incomplete coverage.");rule.appendChild(coverage);
        rule.appendChild(assertion);parent.appendChild(rule);
    }

    private static Element pattern(Document doc, String id) {
        return rule(doc.getElementsByTagNameNS(ValidationEngine.SCH, "pattern"), id);
    }
    private static Element rule(NodeList nodes, String id) {
        for (int i = 0; i < nodes.getLength(); i++) {
            var element = (Element) nodes.item(i);
            if (id.equals(element.getAttribute("id"))) return element;
        }
        throw new IllegalArgumentException("Missing pinned profile component: " + id);
    }
}
