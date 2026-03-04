package yueyang.vostok.office.word.internal;

import yueyang.vostok.office.excel.internal.VKXmlSafeFactory;
import yueyang.vostok.office.exception.VKOfficeErrorCode;
import yueyang.vostok.office.exception.VKOfficeException;

import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamReader;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** 解析 Word 包中的部件关系图。 */
public final class VKWordPartGraphResolver {
    private static final String TYPE_OFFICE_DOCUMENT = "/officedocument";
    private static final String TYPE_HEADER = "/header";
    private static final String TYPE_FOOTER = "/footer";
    private static final String TYPE_FOOTNOTES = "/footnotes";
    private static final String TYPE_ENDNOTES = "/endnotes";
    private static final String TYPE_COMMENTS = "/comments";

    private VKWordPartGraphResolver() {
    }

    public static VKWordPartGraph resolve(Path packageRoot, VKWordLimits limits) {
        String mainPart = resolveMainPart(packageRoot, limits);

        Set<String> scanParts = new LinkedHashSet<>();
        scanParts.add(mainPart);

        List<RelEntry> mainEntries = readPartRelEntries(packageRoot, mainPart, limits);
        for (RelEntry entry : mainEntries) {
            String type = lower(entry.type());
            if (limits.includeHeaderFooter() && (type.endsWith(TYPE_HEADER) || type.endsWith(TYPE_FOOTER))) {
                scanParts.add(entry.targetPart());
            }
            if (limits.includeFootnotes() && type.endsWith(TYPE_FOOTNOTES)) {
                scanParts.add(entry.targetPart());
            }
            if (limits.includeEndnotes() && type.endsWith(TYPE_ENDNOTES)) {
                scanParts.add(entry.targetPart());
            }
            if (limits.includeComments() && type.endsWith(TYPE_COMMENTS)) {
                scanParts.add(entry.targetPart());
            }
        }

        Map<String, Map<String, String>> partRelationships = new LinkedHashMap<>();
        for (String part : scanParts) {
            List<RelEntry> entries = readPartRelEntries(packageRoot, part, limits);
            Map<String, String> rels = new LinkedHashMap<>();
            for (RelEntry entry : entries) {
                if (entry.id() != null && entry.targetPart() != null) {
                    rels.put(entry.id(), entry.targetPart());
                }
            }
            partRelationships.put(part, Collections.unmodifiableMap(rels));
        }

        return new VKWordPartGraph(mainPart,
                Collections.unmodifiableList(new ArrayList<>(scanParts)),
                Collections.unmodifiableMap(partRelationships));
    }

    private static String resolveMainPart(Path packageRoot, VKWordLimits limits) {
        Path rootRels = packageRoot.resolve("_rels/.rels").normalize();
        if (!Files.exists(rootRels) || !Files.isRegularFile(rootRels)) {
            Path fallback = packageRoot.resolve("word/document.xml").normalize();
            if (!Files.exists(fallback) || !Files.isRegularFile(fallback)) {
                throw new VKOfficeException(VKOfficeErrorCode.PARSE_ERROR,
                        "Missing root relationships and word/document.xml");
            }
            return "word/document.xml";
        }

        VKWordSecurityGuard.assertSafeXmlSample(rootRels, limits.xxeSampleBytes());
        for (RelEntry entry : readRels(rootRels, "", false)) {
            if (lower(entry.type()).endsWith(TYPE_OFFICE_DOCUMENT)) {
                return entry.targetPart();
            }
        }
        throw new VKOfficeException(VKOfficeErrorCode.PARSE_ERROR,
                "Invalid docx package: officeDocument relationship not found");
    }

    private static List<RelEntry> readPartRelEntries(Path packageRoot, String sourcePart, VKWordLimits limits) {
        Path sourcePath = packageRoot.resolve(sourcePart).normalize();
        if (!sourcePath.startsWith(packageRoot.normalize()) || !Files.exists(sourcePath) || !Files.isRegularFile(sourcePath)) {
            throw new VKOfficeException(VKOfficeErrorCode.PARSE_ERROR,
                    "Missing word part: " + sourcePart);
        }

        Path parent = sourcePath.getParent();
        String fileName = sourcePath.getFileName().toString();
        Path relsPath = (parent == null
                ? packageRoot.resolve("_rels").resolve(fileName + ".rels")
                : parent.resolve("_rels").resolve(fileName + ".rels")).normalize();

        if (!Files.exists(relsPath) || !Files.isRegularFile(relsPath)) {
            return List.of();
        }
        VKWordSecurityGuard.assertSafeXmlSample(relsPath, limits.xxeSampleBytes());
        return readRels(relsPath, sourcePart, true);
    }

    private static List<RelEntry> readRels(Path relsPath, String sourcePart, boolean skipExternal) {
        XMLInputFactory factory = VKXmlSafeFactory.createInputFactory();
        List<RelEntry> entries = new ArrayList<>();
        try (InputStream in = Files.newInputStream(relsPath)) {
            XMLStreamReader r = factory.createXMLStreamReader(in);
            while (r.hasNext()) {
                int event = r.next();
                if (event != XMLStreamConstants.START_ELEMENT || !"Relationship".equals(r.getLocalName())) {
                    continue;
                }
                String id = attr(r, "Id");
                String type = attr(r, "Type");
                String target = attr(r, "Target");
                String targetMode = attr(r, "TargetMode");
                if (skipExternal && "External".equalsIgnoreCase(targetMode)) {
                    continue;
                }
                if (target == null || target.isBlank()) {
                    continue;
                }
                String targetPart = resolveTargetPart(sourcePart, target);
                entries.add(new RelEntry(id, type, targetPart));
            }
            return entries;
        } catch (VKOfficeException e) {
            throw e;
        } catch (Exception e) {
            throw new VKOfficeException(VKOfficeErrorCode.PARSE_ERROR,
                    "Read relationships failed: " + relsPath, e);
        }
    }

    private static String resolveTargetPart(String sourcePart, String target) {
        String v = target.replace('\\', '/').trim();
        if (v.isEmpty()) {
            return "";
        }
        if (v.startsWith("/")) {
            return normalizePart(v.substring(1));
        }
        Path base = Path.of(sourcePart.isBlank() ? "." : sourcePart).getParent();
        Path resolved = (base == null ? Path.of(v) : base.resolve(v)).normalize();
        return normalizePart(resolved.toString());
    }

    private static String normalizePart(String part) {
        String v = part.replace('\\', '/');
        while (v.startsWith("./")) {
            v = v.substring(2);
        }
        if (v.startsWith("../") || "..".equals(v)) {
            throw new VKOfficeException(VKOfficeErrorCode.SECURITY_ERROR,
                    "Word part escapes package root: " + part);
        }
        return v;
    }

    private static String attr(XMLStreamReader r, String localName) {
        for (int i = 0; i < r.getAttributeCount(); i++) {
            if (localName.equals(r.getAttributeLocalName(i))) {
                return r.getAttributeValue(i);
            }
        }
        return null;
    }

    private static String lower(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT);
    }

    private record RelEntry(String id, String type, String targetPart) {
    }

    /** Word 部件关系图。 */
    public static final class VKWordPartGraph {
        private final String mainPartName;
        private final List<String> scanPartNames;
        private final Map<String, Map<String, String>> partRelationships;

        private VKWordPartGraph(String mainPartName,
                                List<String> scanPartNames,
                                Map<String, Map<String, String>> partRelationships) {
            this.mainPartName = mainPartName;
            this.scanPartNames = scanPartNames;
            this.partRelationships = partRelationships;
        }

        public String mainPartName() {
            return mainPartName;
        }

        public List<String> scanPartNames() {
            return scanPartNames;
        }

        public Map<String, String> relationships(String partName) {
            return partRelationships.getOrDefault(partName, Map.of());
        }
    }
}
