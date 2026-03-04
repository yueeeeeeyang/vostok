package yueyang.vostok.office.ppt.internal;

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
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** 解析 PPT 包中的部件关系图。 */
public final class VKPptPartGraphResolver {
    private static final String TYPE_OFFICE_DOCUMENT = "/officedocument";
    private static final String TYPE_SLIDE = "/slide";

    private VKPptPartGraphResolver() {
    }

    public static VKPptPartGraph resolve(Path packageRoot, VKPptLimits limits) {
        String presentationPart = resolvePresentationPart(packageRoot, limits);
        List<RelEntry> presentationEntries = readPartRelEntries(packageRoot, presentationPart, limits);

        Map<String, String> presentationRelMap = new LinkedHashMap<>();
        for (RelEntry entry : presentationEntries) {
            if (entry.id() != null && entry.targetPart() != null) {
                presentationRelMap.put(entry.id(), entry.targetPart());
            }
        }

        List<String> orderedSlides = readSlideOrder(packageRoot, presentationPart, limits, presentationRelMap);
        if (orderedSlides.isEmpty()) {
            orderedSlides = discoverSlidesByFolder(packageRoot);
        }
        if (limits.maxSlides() > 0 && orderedSlides.size() > limits.maxSlides()) {
            throw new VKOfficeException(VKOfficeErrorCode.LIMIT_EXCEEDED,
                    "PPT slides exceed limit: " + orderedSlides.size() + " > " + limits.maxSlides());
        }

        Map<String, Map<String, String>> partRelationships = new LinkedHashMap<>();
        for (String slidePart : orderedSlides) {
            List<RelEntry> entries = readPartRelEntries(packageRoot, slidePart, limits);
            Map<String, String> rels = new LinkedHashMap<>();
            for (RelEntry entry : entries) {
                if (entry.id() != null && entry.targetPart() != null) {
                    rels.put(entry.id(), entry.targetPart());
                }
            }
            partRelationships.put(slidePart, Collections.unmodifiableMap(rels));
        }

        Map<String, Integer> slideIndexByPart = new LinkedHashMap<>();
        for (int i = 0; i < orderedSlides.size(); i++) {
            slideIndexByPart.put(orderedSlides.get(i), i + 1);
        }

        return new VKPptPartGraph(presentationPart,
                Collections.unmodifiableList(new ArrayList<>(orderedSlides)),
                Collections.unmodifiableMap(partRelationships),
                Collections.unmodifiableMap(slideIndexByPart));
    }

    private static String resolvePresentationPart(Path packageRoot, VKPptLimits limits) {
        Path rootRels = packageRoot.resolve("_rels/.rels").normalize();
        if (!Files.exists(rootRels) || !Files.isRegularFile(rootRels)) {
            Path fallback = packageRoot.resolve("ppt/presentation.xml").normalize();
            if (!Files.exists(fallback) || !Files.isRegularFile(fallback)) {
                throw new VKOfficeException(VKOfficeErrorCode.PARSE_ERROR,
                        "Missing root relationships and ppt/presentation.xml");
            }
            return "ppt/presentation.xml";
        }

        VKPptSecurityGuard.assertSafeXmlSample(rootRels, limits.xxeSampleBytes());
        for (RelEntry entry : readRels(rootRels, "", false)) {
            if (lower(entry.type()).endsWith(TYPE_OFFICE_DOCUMENT)) {
                return entry.targetPart();
            }
        }
        throw new VKOfficeException(VKOfficeErrorCode.PARSE_ERROR,
                "Invalid pptx package: officeDocument relationship not found");
    }

    private static List<String> readSlideOrder(Path packageRoot,
                                               String presentationPart,
                                               VKPptLimits limits,
                                               Map<String, String> presentationRelMap) {
        Path presentationPath = packageRoot.resolve(presentationPart).normalize();
        if (!presentationPath.startsWith(packageRoot.normalize())
                || !Files.exists(presentationPath)
                || !Files.isRegularFile(presentationPath)) {
            throw new VKOfficeException(VKOfficeErrorCode.PARSE_ERROR,
                    "PPT presentation part not found: " + presentationPart);
        }

        VKPptSecurityGuard.assertSafeXmlSample(presentationPath, limits.xxeSampleBytes());
        XMLInputFactory factory = VKXmlSafeFactory.createInputFactory();
        List<String> slides = new ArrayList<>();
        try (InputStream in = Files.newInputStream(presentationPath)) {
            XMLStreamReader r = factory.createXMLStreamReader(in);
            while (r.hasNext()) {
                int event = r.next();
                if (event != XMLStreamConstants.START_ELEMENT || !"sldId".equals(r.getLocalName())) {
                    continue;
                }
                String rid = attr(r, "id");
                if (rid == null || rid.isBlank()) {
                    continue;
                }
                String part = presentationRelMap.get(rid);
                if (part != null && !part.isBlank()) {
                    slides.add(part);
                }
            }
            return slides;
        } catch (Exception e) {
            throw new VKOfficeException(VKOfficeErrorCode.PARSE_ERROR,
                    "Read ppt slide order failed: " + presentationPath, e);
        }
    }

    private static List<String> discoverSlidesByFolder(Path packageRoot) {
        Path slidesDir = packageRoot.resolve("ppt/slides").normalize();
        if (!Files.exists(slidesDir) || !Files.isDirectory(slidesDir)) {
            return List.of();
        }
        try (var stream = Files.list(slidesDir)) {
            return stream
                    .filter(Files::isRegularFile)
                    .map(slidesDir::relativize)
                    .map(Path::toString)
                    .filter(name -> name.startsWith("slide") && name.endsWith(".xml"))
                    .sorted(Comparator.comparingInt(VKPptPartGraphResolver::slideNo).thenComparing(v -> v))
                    .map(name -> "ppt/slides/" + name.replace('\\', '/'))
                    .toList();
        } catch (Exception e) {
            throw new VKOfficeException(VKOfficeErrorCode.PARSE_ERROR,
                    "Discover ppt slides failed: " + slidesDir, e);
        }
    }

    private static int slideNo(String name) {
        int s = name.indexOf("slide");
        int e = name.lastIndexOf('.');
        if (s < 0 || e <= s + 5) {
            return Integer.MAX_VALUE;
        }
        try {
            return Integer.parseInt(name.substring(s + 5, e));
        } catch (Exception ignore) {
            return Integer.MAX_VALUE;
        }
    }

    private static List<RelEntry> readPartRelEntries(Path packageRoot, String sourcePart, VKPptLimits limits) {
        Path sourcePath = packageRoot.resolve(sourcePart).normalize();
        if (!sourcePath.startsWith(packageRoot.normalize()) || !Files.exists(sourcePath) || !Files.isRegularFile(sourcePath)) {
            throw new VKOfficeException(VKOfficeErrorCode.PARSE_ERROR,
                    "Missing ppt part: " + sourcePart);
        }

        Path parent = sourcePath.getParent();
        String fileName = sourcePath.getFileName().toString();
        Path relsPath = (parent == null
                ? packageRoot.resolve("_rels").resolve(fileName + ".rels")
                : parent.resolve("_rels").resolve(fileName + ".rels")).normalize();

        if (!Files.exists(relsPath) || !Files.isRegularFile(relsPath)) {
            return List.of();
        }
        VKPptSecurityGuard.assertSafeXmlSample(relsPath, limits.xxeSampleBytes());
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
                    "PPT part escapes package root: " + part);
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

    /** PPT 部件关系图。 */
    public static final class VKPptPartGraph {
        private final String presentationPartName;
        private final List<String> slidePartNames;
        private final Map<String, Map<String, String>> partRelationships;
        private final Map<String, Integer> slideIndexByPart;

        private VKPptPartGraph(String presentationPartName,
                               List<String> slidePartNames,
                               Map<String, Map<String, String>> partRelationships,
                               Map<String, Integer> slideIndexByPart) {
            this.presentationPartName = presentationPartName;
            this.slidePartNames = slidePartNames;
            this.partRelationships = partRelationships;
            this.slideIndexByPart = slideIndexByPart;
        }

        public String presentationPartName() {
            return presentationPartName;
        }

        public List<String> slidePartNames() {
            return slidePartNames;
        }

        public Map<String, String> relationships(String partName) {
            return partRelationships.getOrDefault(partName, Map.of());
        }

        public int slideIndex(String partName) {
            return slideIndexByPart.getOrDefault(partName, 0);
        }
    }
}
