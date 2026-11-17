package org.cdavalidator;

import org.w3c.dom.*;
import java.util.*;

/** Reviewed local checks layered on the in-memory copy; original HL7 files stay immutable. */
final class SupplementalRules {
    private SupplementalRules() {}
    static final String NAME_INVARIANT = "81-9371";

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
