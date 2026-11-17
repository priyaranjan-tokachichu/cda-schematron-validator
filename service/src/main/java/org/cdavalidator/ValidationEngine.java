package org.cdavalidator;

import com.google.gson.*;
import net.sf.saxon.s9api.*;
import org.w3c.dom.*;
import org.xml.sax.*;
import org.xml.sax.helpers.XMLFilterImpl;
import org.xml.sax.helpers.DefaultHandler;
import javax.xml.XMLConstants;
import javax.xml.parsers.*;
import javax.xml.transform.*;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.sax.SAXSource;
import javax.xml.transform.stream.StreamSource;
import javax.xml.validation.*;
import java.io.*;
import java.net.URI;
import java.nio.file.*;
import java.time.Instant;
import java.util.*;

/** Immutable compiled profiles are shared; every document owns its parse, transformer and results. */
public final class ValidationEngine {
    public static final int MAX_BYTES = xmlLimit(System.getenv().getOrDefault("MAX_XML_MIB", "20"));
    static int xmlLimit(String value) {
        try {
            int bytes = Math.multiplyExact(Integer.parseInt(value), 1024 * 1024);
            if (bytes <= 0) throw new IllegalArgumentException();
            Math.addExact(Math.multiplyExact(bytes, 6), 64 * 1024 + 1);
            return bytes;
        } catch (RuntimeException e) {
            throw new IllegalArgumentException("MAX_XML_MIB must be a positive whole MiB value whose JSON envelope fits the supported integer byte range", e);
        }
    }
    public static final int MAX_FINDINGS = 2000;
    static final String SCH = "http://purl.oclc.org/dsdl/schematron";
    static final String SVRL = "http://purl.oclc.org/dsdl/svrl";
    static final Gson JSON = new GsonBuilder().disableHtmlEscaping().create();
    final Processor processor = new Processor(false);
    final Map<Path,XdmNode> vocabularyCache = new java.util.concurrent.ConcurrentHashMap<>();
    final Map<String, Profile> profiles = new LinkedHashMap<>();
    final Path assets;
    final JsonObject manifest;
    record Profile(JsonObject info, Schema schema, XsltExecutable stylesheet, XPathExecutable documentTemplate) {}

    public ValidationEngine(Path directory) throws Exception {
        this(directory,Path.of(System.getenv().getOrDefault("TERMINOLOGY_DIR",directory.resolve("terminology").toString())));
    }
    ValidationEngine(Path directory,Path terminologyDirectory) throws Exception {
        assets = directory.toRealPath();
        processor.getUnderlyingConfiguration().setErrorReporterFactory(configuration -> error -> { });
        processor.getUnderlyingConfiguration().setLogger(new net.sf.saxon.lib.StandardLogger(new PrintStream(OutputStream.nullOutputStream())));
        manifest = JsonParser.parseString(Files.readString(assets.resolve("manifest.json"))).getAsJsonObject();
        for(var source:manifest.getAsJsonArray("sources")) {
            Path root=asset(source.getAsJsonObject().get("id").getAsString());
            var inventory=JsonParser.parseString(Files.readString(root.resolve(".files.json"))).getAsJsonObject();
            for(var entry:inventory.entrySet()) {
                Path file=root.resolve(entry.getKey()).toRealPath();
                if(!file.startsWith(root))throw new IOException("Invalid asset inventory path");
                String digest=HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(file)));
                if(!digest.equals(entry.getValue().getAsString()))throw new IOException("Validation asset checksum mismatch");
            }
        }
        var compilerURL = Objects.requireNonNull(getClass().getResource("/xslt/2.0/pipeline-for-svrl.xsl"));
        String compilerRoot = compilerURL.toString().split("/xslt/")[0] + "/xslt/";
        processor.getUnderlyingConfiguration().setResourceResolver(request -> {
            try {
                URI uri = URI.create(request.uri);
                if ("file".equals(uri.getScheme())) {
                    Path file = Path.of(uri).toRealPath();
                    if (file.startsWith(assets)) {
                        if(file.toString().endsWith(".xml")) {
                            var vocabulary=vocabularyCache.get(file);
                            if(vocabulary==null) {
                                vocabulary=processor.newDocumentBuilder().build(new StreamSource(file.toFile()));
                                var prior=vocabularyCache.putIfAbsent(file,vocabulary);
                                if(prior!=null)vocabulary=prior;
                            }
                            return vocabulary.asSource();
                        }
                        return new StreamSource(file.toFile());
                    }
                } else if (request.uri.startsWith(compilerRoot)) {
                    return new StreamSource(request.uri);
                }
            } catch (Exception ignored) { }
            throw new net.sf.saxon.trans.XPathException("External resource access denied");
        });
        boolean ccda5=false;
        for(var profile:manifest.getAsJsonArray("profiles"))if("ccda-5.0".equals(profile.getAsJsonObject().get("id").getAsString()))ccda5=true;
        var coverageCatalog=ccda5?CoverageCatalog.load(assets):CoverageCatalog.empty();
        var snapshotCanonicals=new HashSet<>(coverageCatalog.required);snapshotCanonicals.removeAll(GrammarChecks.CANONICALS);
        var terminology = TerminologySnapshots.loadDirectory(terminologyDirectory,snapshotCanonicals);
        var grammar=GrammarChecks.load(terminologyDirectory);grammar.register(processor);
        terminology.register(processor);
        CdaTimestamp.register(processor);
        var compiler = processor.newXsltCompiler();
        var pipeline = compiler.compile(new StreamSource(compilerURL.toString()));
        var schemaFactory = SchemaFactory.newDefaultInstance();
        schemaFactory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
        schemaFactory.setProperty(XMLConstants.ACCESS_EXTERNAL_DTD, "");
        // Only the fixed, checksum-verified XSD bundle can introduce schema imports.
        schemaFactory.setProperty(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "file");
        Map<Path, Schema> schemas = new HashMap<>();
        for (var item : manifest.getAsJsonArray("profiles")) {
            var info = item.getAsJsonObject();
            if(info.has("operatorVocabulary")) {
                var expected=info.getAsJsonObject("operatorVocabulary");
                Path local=terminologyDirectory.resolve(expected.get("file").getAsString());
                boolean ready=Files.isRegularFile(local);
                if(ready) {
                    Path file=local.toRealPath();
                    if(!file.startsWith(terminologyDirectory.toRealPath()) || Files.size(file)>150L*1024*1024)throw new IOException("Invalid operator vocabulary path or size");
                    var digest=java.security.MessageDigest.getInstance("SHA-256");
                    try(var input=new java.security.DigestInputStream(Files.newInputStream(file),digest)){input.transferTo(OutputStream.nullOutputStream());}
                    if(!HexFormat.of().formatHex(digest.digest()).equals(expected.get("sha256").getAsString()))throw new IOException("Operator vocabulary does not match the pinned release");
                    vocabularyCache.put(asset("ccda-2.1/voc.xml"),processor.newDocumentBuilder().build(new StreamSource(file.toFile())));
                }
                var setup=new JsonObject();setup.addProperty("ready",ready);setup.addProperty("setupGuide","/terminology.html");
                var missing=new JsonArray();if(!ready)missing.add("Authorized pinned C-CDA 2.1 vocabulary (contains CPT)");setup.add("missingDatasets",missing);info.add("terminologySetup",setup);
            }
            Path sch = asset(info.get("schematron").getAsString());
            Path xsd = asset(info.get("schema").getAsString());
            Schema schema = schemas.get(xsd);
            if (schema == null) {
                schema = schemaFactory.newSchema(xsd.toFile());
                schemas.put(xsd, schema);
            }
            var doc = trustedSchematron(sch);
            if (info.get("id").getAsString().equals("ccda-5.0")) {
                SupplementalRules.addPersonNameInvariant(doc);
                SupplementalRules.addTimestampInvariants(doc);
                var supplement = new JsonObject();
                supplement.addProperty("ruleId", SupplementalRules.NAME_INVARIANT);
                supplement.addProperty("method", "Reapply all pinned patient-name Schematron constraints at person-name locations, with the permitted plain-text alternative");
                supplement.addProperty("scope", "Pinned Schematron profile; not a general FHIR conformsTo implementation");
                var supplements = new JsonArray(); supplements.add(supplement);
                for(String id:List.of("active-high-ts-after-document","ts-value-before-document")) {
                    var check=new JsonObject();check.addProperty("ruleId",id);
                    check.addProperty("method","Precision-aware CDA timestamp comparison with explicit incomplete coverage for indeterminate comparisons");
                    check.addProperty("scope","ProcedureActivityProcedure; document time, effectiveTime value/low and active high");supplements.add(check);
                }
                info.add("supplementalChecks", supplements);
                info.add("terminologySetup",coverageCatalog.readiness(terminology,grammar));
                info.add("grammarDefinitions",grammar.metadata.deepCopy());
                info.addProperty("supplementalBindingChecks",coverageCatalog.addBindings(doc,terminology,grammar));
                if (terminology.members.containsKey(TerminologySnapshots.PROCEDURE)) {
                    SupplementalRules.addProcedureInvariants(doc);
                    for (String id : List.of("1198-8487", "1198-8511")) {
                        var check = new JsonObject(); check.addProperty("ruleId", id);
                        check.addProperty("method", "Exact code-system and code membership in a local checksum-pinned Procedure expansion, with the original permitted code-system alternatives");
                        check.addProperty("scope", "Pinned invariant and local terminology edition"); supplements.add(check);
                    }
                }
                if(terminology.members.keySet().containsAll(CoverageCatalog.LOCATION)) {
                    SupplementalRules.addLocationInvariant(doc);
                    var check=new JsonObject();check.addProperty("ruleId","shall-use-one-binding");check.addProperty("method","Exact code/system membership in the union of three installed location value sets");check.addProperty("scope","ServiceDeliveryLocation code");supplements.add(check);
                }
                if (!terminology.metadata.isEmpty()) info.add("terminologySnapshots", terminology.metadata.deepCopy());
                info.addProperty("vocabularyAssertionsNormalized",VocabularyAssertions.normalize(doc));
            }
            var destination = new XdmDestination();
            var transform = pipeline.load();
            transform.setSource(new DOMSource(doc, sch.toUri().toString()));
            transform.setDestination(destination);
            transform.transform();
            // These HL7 releases omit queryBinding and use XPath 1.0 coercions.
            // Keep the standard compiler, but retain backwards-compatible expression semantics.
            var generatedText = destination.getXdmNode().toString();
            var generatedFactory = DocumentBuilderFactory.newDefaultInstance();
            generatedFactory.setNamespaceAware(true);
            var generatedDoc = generatedFactory.newDocumentBuilder().parse(new InputSource(new StringReader(generatedText)));
            generatedDoc.getDocumentElement().setAttribute("version", "1.0");
            var generated = new DOMSource(generatedDoc);
            generated.setSystemId(sch.toUri().toString());
            var executable = compiler.compile(generated);
            // Derive supported document roots and versions from this exact pinned profile.
            var contexts = new TreeSet<String>();
            var rules = doc.getElementsByTagNameNS(SCH, "rule");
            for (int i = 0; i < rules.getLength(); i++) {
                String context = ((Element) rules.item(i)).getAttribute("context");
                if (context.startsWith("cda:ClinicalDocument[") && context.contains("cda:templateId[") && !context.contains("/")) contexts.add(context);
            }
            XPathExecutable documentTemplate = null;
            if (!contexts.isEmpty()) {
                var xpath = processor.newXPathCompiler();
                xpath.declareNamespace("cda", "urn:hl7-org:v3");
                documentTemplate = xpath.compile("exists(/* intersect (" + String.join(" | ", contexts) + "))");
            } else if (info.get("id").getAsString().startsWith("ccda-")) {
                throw new IOException("Profile has no supported document-level template rules");
            }
            profiles.put(info.get("id").getAsString(), new Profile(info, schema, executable, documentTemplate));
        }
    }

    private Path asset(String name) throws IOException {
        Path file = assets.resolve(name).toRealPath();
        if (!file.startsWith(assets)) throw new IOException("Invalid asset path");
        return file;
    }

    /** Upstream phase membership becomes explicit severity; assertions retain their text and tests. */
    static Document trustedSchematron(Path file) throws Exception {
        var factory = DocumentBuilderFactory.newDefaultInstance();
        factory.setNamespaceAware(true);
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        Document doc = factory.newDocumentBuilder().parse(file.toFile());
        doc.getDocumentElement().setAttribute("queryBinding", "xslt2");
        Map<String, String> severity = new HashMap<>();
        var phases = doc.getElementsByTagNameNS(SCH, "phase");
        for (int i = 0; i < phases.getLength(); i++) {
            var phase = (Element) phases.item(i);
            String level = phase.getAttribute("id").equals("warnings") ? "warning" : "error";
            var active = phase.getElementsByTagNameNS(SCH, "active");
            for (int j = 0; j < active.getLength(); j++) {
                severity.put(((Element) active.item(j)).getAttribute("pattern"), level);
            }
        }
        var patterns = doc.getElementsByTagNameNS(SCH, "pattern");
        for (int i = 0; i < patterns.getLength(); i++) {
            var pattern = (Element) patterns.item(i);
            var assertions = pattern.getElementsByTagNameNS(SCH, "assert");
            for (int j = 0; j < assertions.getLength(); j++) {
                var assertion = (Element) assertions.item(j);
                if (!assertion.hasAttribute("role")) assertion.setAttribute("role", severity.getOrDefault(pattern.getAttribute("id"), "error"));
                if (!assertion.hasAttribute("id")) assertion.setAttribute("id", "generated-" + i + "-" + j);
            }
        }
        return doc;
    }

    /** A strict parser: no DTDs/entities/network, and bounded tree complexity. */
    static SAXSource xmlSource(String xml) throws Exception {
        var factory = SAXParserFactory.newDefaultInstance();
        factory.setNamespaceAware(true);
        factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
        var reader = factory.newSAXParser().getXMLReader();
        reader.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        reader.setFeature("http://xml.org/sax/features/external-general-entities", false);
        reader.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
        reader.setProperty(XMLConstants.ACCESS_EXTERNAL_DTD, "");
        var limited = new XMLFilterImpl(reader) {
            int depth, nodes;
            @Override public void startElement(String uri, String local, String qname, Attributes attributes) throws SAXException {
                if (++depth > 128 || ++nodes > 100000 || attributes.getLength() > 256) {
                    throw new SAXException("XML complexity limit exceeded");
                }
                super.startElement(uri, local, qname, attributes);
            }
            @Override public void endElement(String uri, String local, String qname) throws SAXException {
                super.endElement(uri, local, qname); depth--;
            }
        };
        limited.setErrorHandler(new ErrorHandler() {
            public void warning(SAXParseException e) { }
            public void error(SAXParseException e) throws SAXException { throw e; }
            public void fatalError(SAXParseException e) throws SAXException { throw e; }
        });
        return new SAXSource(limited, new InputSource(new StringReader(xml)));
    }

    public List<JsonObject> profileInfo() { return profiles.values().stream().map(p -> p.info.deepCopy()).toList(); }

    public Map<String, Object> validate(String xml, String profileId, boolean includeWarnings) throws Exception {
        var profile = profiles.get(profileId);
        if (profile == null) throw new IllegalArgumentException("Unknown validation profile");
        if (xml == null || xml.isBlank()) throw new IllegalArgumentException("XML document is required");
        if (xml.getBytes(java.nio.charset.StandardCharsets.UTF_8).length > MAX_BYTES) throw new IllegalArgumentException("XML exceeds configured limit of " + MAX_BYTES + " bytes");
        long start = System.nanoTime();
        List<Map<String, Object>> findings = new ArrayList<>();
        List<Map<String, Object>> stages = new ArrayList<>();
        var builder = processor.newDocumentBuilder();
        builder.setLineNumbering(true);
        XdmNode document;
        try {
            document = builder.build(xmlSource(xml));
            stages.add(Map.of("name", "xml", "status", "completed"));
        } catch (Exception error) {
            Throwable cause = error;
            while (cause.getCause() != null) cause = cause.getCause();
            String reason=cause.getMessage();
            var finding = finding("error", "xml", reason==null ? "XML is malformed, contains a prohibited DTD, or exceeds complexity limits." : "XML parser: "+reason, null, null, null);
            if (cause instanceof SAXParseException e) { finding.put("line", e.getLineNumber()); finding.put("column", e.getColumnNumber()); }
            findings.add(finding);
            stages.add(Map.of("name", "xml", "status", "failed"));
            stages.add(Map.of("name", "xsd", "status", "not-run"));
            stages.add(Map.of("name", "schematron", "status", "not-run"));
            return report(profile, findings, stages, false, false, start, 0, includeWarnings);
        }
        boolean completed = true;
        if (profile.documentTemplate != null) {
            var selector = profile.documentTemplate.load();
            selector.setContextItem(document);
            if (!selector.effectiveBooleanValue()) {
                findings.add(finding("warning", "coverage", "No supported document-level template matched the ClinicalDocument root for this profile. Nested templates do not establish document-level coverage.", null, null, null));
            }
        }
        var validator = profile.schema.newValidator();
        validator.setProperty(XMLConstants.ACCESS_EXTERNAL_DTD, "");
        validator.setProperty(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
        validator.setErrorHandler(new ErrorHandler() {
            public void warning(SAXParseException e) throws SAXException { add(e, "warning"); }
            public void error(SAXParseException e) throws SAXException { add(e, "error"); }
            public void fatalError(SAXParseException e) throws SAXException { add(e, "error"); throw e; }
            void add(SAXParseException e, String severity) throws SAXException {
                if (findings.size() >= MAX_FINDINGS) throw new SAXException("Finding limit exceeded");
                var f = finding(severity, "xsd", e.getMessage(), null, null, null);
                f.put("line", e.getLineNumber()); f.put("column", e.getColumnNumber()); findings.add(f);
            }
        });
        try {
            validator.validate(xmlSource(xml));
            stages.add(Map.of("name", "xsd", "status", "completed"));
        } catch (Exception e) {
            completed = false;
            stages.add(Map.of("name", "xsd", "status", "incomplete"));
        }
        var collector = new SvrlCollector(document, findings, includeWarnings);
        if(profile.info.has("operatorVocabulary") && !profile.info.getAsJsonObject("terminologySetup").get("ready").getAsBoolean()) {
            findings.add(finding("warning","coverage","Install the authorized pinned C-CDA 2.1 vocabulary before running its Schematron checks. See /terminology.html.","terminology-setup-required",null,null));
            stages.add(Map.of("name","schematron","status","not-run","reason","terminology-setup-required"));
            return report(profile,findings,stages,false,false,start,0,includeWarnings);
        }
        try {
            var transformer = profile.stylesheet.load();
            // Don't allow Saxon diagnostics to log submitted XML or document values.
            transformer.setErrorReporter(error -> { });
            transformer.setInitialContextNode(document);
            transformer.setDestination(new SAXDestination(collector));
            transformer.transform();
            stages.add(Map.of("name", "schematron", "status", "completed", "firedRules", collector.firedRules));
            if (collector.firedRules == 0) {
                completed = false;
                findings.add(finding("warning", "coverage", "No Schematron rules matched this document. Check the document template and profile.", null, null, null));
            }
        } catch (Exception e) {
            completed = false;
            stages.add(Map.of("name", "schematron", "status", collector.truncated ? "truncated" : "failed"));
            if (!collector.truncated) findings.add(finding("error", "engine", "Schematron execution failed; this document has not been fully validated.", null, null, null));
        }
        return report(profile, findings, stages, completed, collector.truncated || findings.size() >= MAX_FINDINGS, start, collector.firedRules, includeWarnings);
    }

    /** Stream SVRL so a failing document cannot allocate an unbounded intermediate report. */
    final class SvrlCollector extends DefaultHandler {
        final XdmNode document;
        final List<Map<String,Object>> findings;
        final boolean warnings;
        final Map<String,Integer> lines = new HashMap<>();
        Map<String,Object> current;
        String context, pattern;
        StringBuilder message;
        boolean inText, truncated;
        int firedRules;
        SvrlCollector(XdmNode doc, List<Map<String,Object>> findings, boolean warnings) {
            document=doc;this.findings=findings;this.warnings=warnings;
        }
        @Override public void startElement(String uri,String local,String name,Attributes a) throws SAXException {
            if(Thread.currentThread().isInterrupted()) throw new SAXException("Validation cancelled");
            if(!SVRL.equals(uri)) return;
            if(local.equals("active-pattern")) pattern=a.getValue("id");
            if(local.equals("fired-rule")) { firedRules++;context=a.getValue("context"); }
            if(local.equals("text")) inText=true;
            if(!local.equals("failed-assert")&&!local.equals("successful-report"))return;
            String role=a.getValue("role");
            boolean coverage="coverage".equals(role);
            String severity=coverage||role!=null&&(role.contains("warn")||role.equals("info"))?"warning":"error";
            if(!warnings&&severity.equals("warning")&&!coverage){current=null;return;}
            if(findings.size()>=MAX_FINDINGS){truncated=true;throw new SAXException("Finding limit reached");}
            String location=a.getValue("location");
            current=finding(severity,coverage?"coverage":"schematron","",a.getValue("id"),location,a.getValue("test"));
            current.put("patternId",pattern);current.put("context",context);
            if(location!=null){int line=lines.computeIfAbsent(location,path->findLine(document,path));if(line>0)current.put("line",line);}
            message=new StringBuilder();
        }
        @Override public void characters(char[] data,int start,int length){if(inText&&current!=null&&message.length()<4001)message.append(data,start,Math.min(length,4001-message.length()));}
        @Override public void endElement(String uri,String local,String name){
            if(!SVRL.equals(uri))return;
            if(local.equals("text"))inText=false;
            if((local.equals("failed-assert")||local.equals("successful-report"))&&current!=null){String text=message.toString().strip();current.put("message",text.length()>4000?text.substring(0,4000)+" [message truncated]":text);if(text.length()>4000)current.put("messageTruncated",true);findings.add(current);current=null;}
        }
    }

    private int findLine(XdmNode document, String path) {
        try {
            var selector = processor.newXPathCompiler().compile(path).load();
            selector.setContextItem(document);
            var item = selector.evaluateSingle();
            return item instanceof XdmNode node ? node.getLineNumber() : -1;
        } catch (Exception ignored) { return -1; }
    }
    static String attr(XdmNode node, String name) { return node.getAttributeValue(new QName(name)); }
    static Map<String, Object> finding(String severity, String stage, String message, String id, String location, String test) {
        var f = new LinkedHashMap<String, Object>();
        f.put("severity", severity); f.put("stage", stage); f.put("message", message.length()>4000?message.substring(0,4000)+" [message truncated]":message);
        if(message.length()>4000)f.put("messageTruncated",true);
        if (id != null) f.put("ruleId", id);
        if (location != null) f.put("xpath", location);
        if (test != null) f.put("test", test);
        return f;
    }
    private Map<String, Object> report(Profile profile, List<Map<String, Object>> findings, List<Map<String, Object>> stages,
                                       boolean executed, boolean truncated, long start, int firedRules, boolean includeWarnings) {
        long errors = findings.stream().filter(f -> f.get("severity").equals("error")).count();
        long warnings = findings.stream().filter(f -> f.get("severity").equals("warning")).count();
        boolean coverageComplete = !profile.info.has("generationCoverage") || profile.info.getAsJsonObject("generationCoverage").entrySet().stream().allMatch(e -> e.getValue().getAsInt() == 0);
        boolean complete = executed && coverageComplete && !truncated && findings.stream().noneMatch(f -> "coverage".equals(f.get("stage")));
        var result = new LinkedHashMap<String, Object>();
        result.put("requestId", UUID.randomUUID().toString());
        result.put("applicationVersion", "2.0.0-preview.2");
        result.put("status", errors > 0 ? "invalid" : complete ? "valid" : "incomplete");
        result.put("validationComplete", complete);
        result.put("executionComplete", executed && !truncated);
        result.put("truncated", truncated);
        result.put("options", Map.of("includeWarnings",includeWarnings));
        result.put("summary", Map.of("errors", errors, "warnings", warnings, "findings", findings.size(), "firedRules", firedRules));
        result.put("findings", findings); result.put("stages", stages);
        result.put("profile", profile.info.deepCopy());
        result.put("sources", manifest.get("sources").deepCopy());
        result.put("engine", Map.of("name", "Saxon-HE / SchXslt", "saxon", "12.9", "schxslt", "1.10.1", "schemaRelease", "20260205_SDTC"));
        result.put("durationMs", Math.round((System.nanoTime() - start) / 10000.0) / 100.0);
        result.put("validatedAt", Instant.now().toString());
        return result;
    }
}
