package yueyang.vostok.office.ppt.internal;

import yueyang.vostok.office.exception.VKOfficeErrorCode;
import yueyang.vostok.office.exception.VKOfficeException;
import yueyang.vostok.office.style.VKOfficeImageStyle;
import yueyang.vostok.office.style.VKOfficeLayoutStyle;
import yueyang.vostok.office.style.VKOfficeTextAlign;
import yueyang.vostok.office.style.VKOfficeTextStyle;

import java.io.BufferedWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** 生成最小可用的 PPT OOXML 文件结构。 */
public final class VKPptXmlWriter {
    private VKPptXmlWriter() {
    }

    public static void writePackage(Path packageRoot,
                                    List<SlidePart> slides,
                                    Set<String> imageExtensions,
                                    VKOfficeLayoutStyle layoutStyle) {
        try {
            Files.createDirectories(packageRoot.resolve("_rels"));
            Files.createDirectories(packageRoot.resolve("ppt/_rels"));
            Files.createDirectories(packageRoot.resolve("ppt/slides/_rels"));
            Files.createDirectories(packageRoot.resolve("ppt/media"));

            writeRootRels(packageRoot.resolve("_rels/.rels"));
            writeContentTypes(packageRoot.resolve("[Content_Types].xml"), slides, imageExtensions);
            writePresentation(packageRoot.resolve("ppt/presentation.xml"), slides.size());
            writePresentationRels(packageRoot.resolve("ppt/_rels/presentation.xml.rels"), slides.size());

            for (SlidePart slide : slides) {
                writeSlide(packageRoot.resolve("ppt/slides/slide" + slide.slideNo() + ".xml"), slide, layoutStyle);
                writeSlideRels(packageRoot.resolve("ppt/slides/_rels/slide" + slide.slideNo() + ".xml.rels"), slide.imageRelationships());
            }
        } catch (VKOfficeException e) {
            throw e;
        } catch (Exception e) {
            throw new VKOfficeException(VKOfficeErrorCode.WRITE_ERROR,
                    "Write ppt package xml failed", e);
        }
    }

    private static void writeRootRels(Path path) throws Exception {
        try (BufferedWriter w = Files.newBufferedWriter(path, StandardCharsets.UTF_8)) {
            w.write("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>");
            w.write("<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">");
            w.write("<Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument\" Target=\"ppt/presentation.xml\"/>");
            w.write("</Relationships>");
        }
    }

    private static void writeContentTypes(Path path, List<SlidePart> slides, Set<String> imageExtensions) throws Exception {
        Set<String> exts = imageExtensions == null ? Set.of() : new LinkedHashSet<>(imageExtensions);
        try (BufferedWriter w = Files.newBufferedWriter(path, StandardCharsets.UTF_8)) {
            w.write("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>");
            w.write("<Types xmlns=\"http://schemas.openxmlformats.org/package/2006/content-types\">");
            w.write("<Default Extension=\"rels\" ContentType=\"application/vnd.openxmlformats-package.relationships+xml\"/>");
            w.write("<Default Extension=\"xml\" ContentType=\"application/xml\"/>");
            for (String ext : exts) {
                String e = ext == null || ext.isBlank() ? "bin" : ext;
                String ct = VKPptContentTypeResolver.contentTypeByFileName("x." + e);
                w.write("<Default Extension=\"" + escapeXmlAttr(e) + "\" ContentType=\"" + escapeXmlAttr(ct) + "\"/>");
            }
            w.write("<Override PartName=\"/ppt/presentation.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.presentationml.presentation.main+xml\"/>");
            for (SlidePart slide : slides) {
                w.write("<Override PartName=\"/ppt/slides/slide" + slide.slideNo() + ".xml\" ContentType=\"application/vnd.openxmlformats-officedocument.presentationml.slide+xml\"/>");
            }
            w.write("</Types>");
        }
    }

    private static void writePresentation(Path path, int slideCount) throws Exception {
        try (BufferedWriter w = Files.newBufferedWriter(path, StandardCharsets.UTF_8)) {
            w.write("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>");
            w.write("<p:presentation xmlns:a=\"http://schemas.openxmlformats.org/drawingml/2006/main\" xmlns:r=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships\" xmlns:p=\"http://schemas.openxmlformats.org/presentationml/2006/main\">");
            w.write("<p:sldIdLst>");
            for (int i = 1; i <= slideCount; i++) {
                w.write("<p:sldId id=\"" + (255 + i) + "\" r:id=\"rId" + i + "\"/>");
            }
            w.write("</p:sldIdLst>");
            w.write("<p:sldSz cx=\"9144000\" cy=\"6858000\" type=\"screen4x3\"/>");
            w.write("<p:notesSz cx=\"6858000\" cy=\"9144000\"/>");
            w.write("</p:presentation>");
        }
    }

    private static void writePresentationRels(Path path, int slideCount) throws Exception {
        try (BufferedWriter w = Files.newBufferedWriter(path, StandardCharsets.UTF_8)) {
            w.write("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>");
            w.write("<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">");
            for (int i = 1; i <= slideCount; i++) {
                w.write("<Relationship Id=\"rId" + i + "\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/slide\" Target=\"slides/slide" + i + ".xml\"/>");
            }
            w.write("</Relationships>");
        }
    }

    private static void writeSlide(Path path, SlidePart slide, VKOfficeLayoutStyle layoutStyle) throws Exception {
        List<BodyBlock> blocks = slide.blocks() == null ? List.of() : new ArrayList<>(slide.blocks());
        if (blocks.isEmpty()) {
            blocks = Collections.singletonList(BodyBlock.paragraph(""));
        }

        try (BufferedWriter w = Files.newBufferedWriter(path, StandardCharsets.UTF_8)) {
            w.write("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>");
            w.write("<p:sld xmlns:a=\"http://schemas.openxmlformats.org/drawingml/2006/main\" xmlns:r=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships\" xmlns:p=\"http://schemas.openxmlformats.org/presentationml/2006/main\">");
            w.write("<p:cSld><p:spTree>");
            w.write("<p:nvGrpSpPr><p:cNvPr id=\"1\" name=\"\"/><p:cNvGrpSpPr/><p:nvPr/></p:nvGrpSpPr>");
            w.write("<p:grpSpPr/>");

            int shapeId = 2;
            long top = toEmu(layoutStyle == null ? null : layoutStyle.marginTop(), 1000000L);
            long left = toEmu(layoutStyle == null ? null : layoutStyle.marginLeft(), 1000000L);
            for (BodyBlock block : blocks) {
                if (block.imageRelId() != null) {
                    writeImage(w, shapeId++, block.imageRelId(), block.imageName(), block.imageStyle(), left, top);
                    top += toEmu(layoutStyle == null ? null : layoutStyle.blockSpacing(), 340000L);
                } else {
                    writeText(w, shapeId++, block.text(), block.textStyle(), left, top);
                    top += toEmu(layoutStyle == null ? null : layoutStyle.defaultLineHeight(), 380000L);
                }
            }

            w.write("</p:spTree></p:cSld>");
            w.write("<p:clrMapOvr><a:masterClrMapping/></p:clrMapOvr>");
            w.write("</p:sld>");
        }
    }

    private static void writeText(BufferedWriter w,
                                  int shapeId,
                                  String text,
                                  VKOfficeTextStyle style,
                                  long left,
                                  long top) throws Exception {
        String v = text == null ? "" : text;
        w.write("<p:sp>");
        w.write("<p:nvSpPr><p:cNvPr id=\"" + shapeId + "\" name=\"TextBox " + shapeId + "\"/><p:cNvSpPr txBox=\"1\"/><p:nvPr/></p:nvSpPr>");
        w.write("<p:spPr><a:xfrm><a:off x=\"" + left + "\" y=\"" + top + "\"/><a:ext cx=\"7600000\" cy=\"450000\"/></a:xfrm></p:spPr>");
        w.write("<p:txBody><a:bodyPr/><a:lstStyle>");
        String normalized = v.replace("\r\n", "\n").replace('\r', '\n');
        String[] lines = normalized.split("\\n", -1);
        for (String line : lines) {
            w.write("<a:p>");
            if (style != null && style.align() != null) {
                w.write("<a:pPr algn=\"" + toPptAlign(style.align()) + "\"/>");
            }
            w.write("<a:r>");
            writeTextRunStyle(w, style);
            w.write("<a:t>" + escapeXmlText(line) + "</a:t>");
            w.write("</a:r>");
            w.write("</a:p>");
        }
        w.write("</a:lstStyle></p:txBody>");
        w.write("</p:sp>");
    }

    private static void writeImage(BufferedWriter w,
                                   int shapeId,
                                   String rid,
                                   String imageName,
                                   VKOfficeImageStyle imageStyle,
                                   long left,
                                   long top) throws Exception {
        String name = imageName == null || imageName.isBlank() ? "image" : imageName;
        long cx = toEmu(imageStyle == null ? null : imageStyle.width(), 3000000L);
        long cy = toEmu(imageStyle == null ? null : imageStyle.height(), 3000000L);
        w.write("<p:pic>");
        w.write("<p:nvPicPr><p:cNvPr id=\"" + shapeId + "\" name=\"" + escapeXmlAttr(name) + "\"/><p:cNvPicPr/><p:nvPr/></p:nvPicPr>");
        w.write("<p:blipFill><a:blip r:embed=\"" + escapeXmlAttr(rid) + "\"/><a:stretch><a:fillRect/></a:stretch></p:blipFill>");
        w.write("<p:spPr><a:xfrm><a:off x=\"" + left + "\" y=\"" + top + "\"/><a:ext cx=\"" + cx + "\" cy=\"" + cy
                + "\"/></a:xfrm><a:prstGeom prst=\"rect\"><a:avLst/></a:prstGeom></p:spPr>");
        w.write("</p:pic>");
    }

    private static void writeSlideRels(Path path, Map<String, String> imageRelationships) throws Exception {
        try (BufferedWriter w = Files.newBufferedWriter(path, StandardCharsets.UTF_8)) {
            w.write("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>");
            w.write("<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">");
            if (imageRelationships != null) {
                for (Map.Entry<String, String> e : imageRelationships.entrySet()) {
                    w.write("<Relationship Id=\"" + escapeXmlAttr(e.getKey())
                            + "\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/image\" Target=\""
                            + escapeXmlAttr(e.getValue()) + "\"/>");
                }
            }
            w.write("</Relationships>");
        }
    }

    private static String escapeXmlText(String value) {
        if (value == null || value.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder(value.length() + 16);
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            switch (c) {
                case '&' -> sb.append("&amp;");
                case '<' -> sb.append("&lt;");
                case '>' -> sb.append("&gt;");
                default -> sb.append(c);
            }
        }
        return sb.toString();
    }

    private static String escapeXmlAttr(String value) {
        if (value == null || value.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder(value.length() + 16);
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            switch (c) {
                case '&' -> sb.append("&amp;");
                case '<' -> sb.append("&lt;");
                case '>' -> sb.append("&gt;");
                case '"' -> sb.append("&quot;");
                default -> sb.append(c);
            }
        }
        return sb.toString();
    }

    private static void writeTextRunStyle(BufferedWriter w, VKOfficeTextStyle style) throws Exception {
        if (style == null) {
            return;
        }
        boolean has = (style.bold() != null && style.bold())
                || (style.italic() != null && style.italic())
                || (style.fontSize() != null && style.fontSize() > 0)
                || (style.colorHex() != null && !style.colorHex().isBlank());
        if (!has) {
            return;
        }
        w.write("<a:rPr");
        if (style.bold() != null && style.bold()) {
            w.write(" b=\"1\"");
        }
        if (style.italic() != null && style.italic()) {
            w.write(" i=\"1\"");
        }
        if (style.fontSize() != null && style.fontSize() > 0) {
            w.write(" sz=\"" + (style.fontSize() * 100) + "\"");
        }
        w.write(">");
        if (style.colorHex() != null && !style.colorHex().isBlank()) {
            w.write("<a:solidFill><a:srgbClr val=\"" + sanitizeColor(style.colorHex()) + "\"/></a:solidFill>");
        }
        w.write("</a:rPr>");
    }

    private static String sanitizeColor(String colorHex) {
        String v = colorHex.trim();
        if (v.startsWith("#")) {
            v = v.substring(1);
        }
        if (v.length() == 3) {
            v = "" + v.charAt(0) + v.charAt(0) + v.charAt(1) + v.charAt(1) + v.charAt(2) + v.charAt(2);
        }
        if (v.length() != 6) {
            return "000000";
        }
        return v.toUpperCase();
    }

    private static String toPptAlign(VKOfficeTextAlign align) {
        if (align == null) {
            return "l";
        }
        return switch (align) {
            case CENTER -> "ctr";
            case RIGHT -> "r";
            case JUSTIFY -> "just";
            default -> "l";
        };
    }

    private static long toEmu(Double pt, long fallback) {
        if (pt == null || pt <= 0) {
            return fallback;
        }
        return Math.max(1, Math.round(pt * 12700.0));
    }

    private static long toEmu(Integer px, long fallback) {
        if (px == null || px <= 0) {
            return fallback;
        }
        return Math.max(1, px.longValue() * 9525L);
    }

    /** 幻灯片结构信息。 */
    public static final class SlidePart {
        private final int slideNo;
        private final List<BodyBlock> blocks;
        private final Map<String, String> imageRelationships;

        public SlidePart(int slideNo, List<BodyBlock> blocks, Map<String, String> imageRelationships) {
            this.slideNo = slideNo;
            this.blocks = blocks;
            this.imageRelationships = imageRelationships;
        }

        public int slideNo() {
            return slideNo;
        }

        public List<BodyBlock> blocks() {
            return blocks;
        }

        public Map<String, String> imageRelationships() {
            return imageRelationships;
        }
    }

    /** 幻灯片块：段落文本或图片引用。 */
    public static final class BodyBlock {
        private final String text;
        private final String imageRelId;
        private final String imageName;
        private final VKOfficeTextStyle textStyle;
        private final VKOfficeImageStyle imageStyle;

        private BodyBlock(String text,
                          String imageRelId,
                          String imageName,
                          VKOfficeTextStyle textStyle,
                          VKOfficeImageStyle imageStyle) {
            this.text = text;
            this.imageRelId = imageRelId;
            this.imageName = imageName;
            this.textStyle = textStyle;
            this.imageStyle = imageStyle;
        }

        public static BodyBlock paragraph(String text) {
            return new BodyBlock(text, null, null, null, null);
        }

        public static BodyBlock paragraph(String text, VKOfficeTextStyle textStyle) {
            return new BodyBlock(text, null, null, textStyle, null);
        }

        public static BodyBlock image(String imageRelId, String imageName) {
            return new BodyBlock(null, imageRelId, imageName, null, null);
        }

        public static BodyBlock image(String imageRelId, String imageName, VKOfficeImageStyle imageStyle) {
            return new BodyBlock(null, imageRelId, imageName, null, imageStyle);
        }

        public String text() {
            return text;
        }

        public String imageRelId() {
            return imageRelId;
        }

        public String imageName() {
            return imageName;
        }

        public VKOfficeTextStyle textStyle() {
            return textStyle;
        }

        public VKOfficeImageStyle imageStyle() {
            return imageStyle;
        }
    }
}
