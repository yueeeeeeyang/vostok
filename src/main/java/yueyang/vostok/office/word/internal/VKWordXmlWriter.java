package yueyang.vostok.office.word.internal;

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

/** 生成最小可用的 Word OOXML 文件结构。 */
public final class VKWordXmlWriter {
    private static final long DEFAULT_CX = 1905000L;
    private static final long DEFAULT_CY = 1905000L;

    private VKWordXmlWriter() {
    }

    public static void writePackage(Path packageRoot,
                                    List<BodyBlock> blocks,
                                    Map<String, String> imageRelationships,
                                    Set<String> imageExtensions,
                                    VKOfficeLayoutStyle layoutStyle) {
        try {
            Files.createDirectories(packageRoot.resolve("_rels"));
            Files.createDirectories(packageRoot.resolve("word/_rels"));
            Files.createDirectories(packageRoot.resolve("word/media"));

            writeRootRels(packageRoot.resolve("_rels/.rels"));
            writeContentTypes(packageRoot.resolve("[Content_Types].xml"), imageExtensions);
            writeStyles(packageRoot.resolve("word/styles.xml"));
            writeDocument(packageRoot.resolve("word/document.xml"), blocks, layoutStyle);
            writeDocumentRels(packageRoot.resolve("word/_rels/document.xml.rels"), imageRelationships);
        } catch (VKOfficeException e) {
            throw e;
        } catch (Exception e) {
            throw new VKOfficeException(VKOfficeErrorCode.WRITE_ERROR,
                    "Write word package xml failed", e);
        }
    }

    private static void writeRootRels(Path path) throws Exception {
        try (BufferedWriter w = Files.newBufferedWriter(path, StandardCharsets.UTF_8)) {
            w.write("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>");
            w.write("<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">");
            w.write("<Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument\" Target=\"word/document.xml\"/>");
            w.write("</Relationships>");
        }
    }

    private static void writeContentTypes(Path path, Set<String> imageExtensions) throws Exception {
        Set<String> exts = imageExtensions == null ? Set.of() : new LinkedHashSet<>(imageExtensions);
        try (BufferedWriter w = Files.newBufferedWriter(path, StandardCharsets.UTF_8)) {
            w.write("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>");
            w.write("<Types xmlns=\"http://schemas.openxmlformats.org/package/2006/content-types\">");
            w.write("<Default Extension=\"rels\" ContentType=\"application/vnd.openxmlformats-package.relationships+xml\"/>");
            w.write("<Default Extension=\"xml\" ContentType=\"application/xml\"/>");
            for (String ext : exts) {
                String e = ext == null || ext.isBlank() ? "bin" : ext;
                String ct = VKWordContentTypeResolver.contentTypeByFileName("x." + e);
                w.write("<Default Extension=\"" + escapeXmlAttr(e) + "\" ContentType=\"" + escapeXmlAttr(ct) + "\"/>");
            }
            w.write("<Override PartName=\"/word/document.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml\"/>");
            w.write("<Override PartName=\"/word/styles.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.wordprocessingml.styles+xml\"/>");
            w.write("</Types>");
        }
    }

    private static void writeStyles(Path path) throws Exception {
        try (BufferedWriter w = Files.newBufferedWriter(path, StandardCharsets.UTF_8)) {
            w.write("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>");
            w.write("<w:styles xmlns:w=\"http://schemas.openxmlformats.org/wordprocessingml/2006/main\">");
            w.write("<w:style w:type=\"paragraph\" w:default=\"1\" w:styleId=\"Normal\">");
            w.write("<w:name w:val=\"Normal\"/>");
            w.write("</w:style>");
            w.write("</w:styles>");
        }
    }

    private static void writeDocument(Path path, List<BodyBlock> blocks, VKOfficeLayoutStyle layoutStyle) throws Exception {
        List<BodyBlock> body = blocks == null ? List.of() : new ArrayList<>(blocks);
        if (body.isEmpty()) {
            body = Collections.singletonList(BodyBlock.paragraph(""));
        }

        try (BufferedWriter w = Files.newBufferedWriter(path, StandardCharsets.UTF_8)) {
            w.write("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>");
            w.write("<w:document");
            w.write(" xmlns:w=\"http://schemas.openxmlformats.org/wordprocessingml/2006/main\"");
            w.write(" xmlns:r=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships\"");
            w.write(" xmlns:wp=\"http://schemas.openxmlformats.org/drawingml/2006/wordprocessingDrawing\"");
            w.write(" xmlns:a=\"http://schemas.openxmlformats.org/drawingml/2006/main\"");
            w.write(" xmlns:pic=\"http://schemas.openxmlformats.org/drawingml/2006/picture\"");
            w.write(">");
            w.write("<w:body>");

            int imageDocPrId = 1;
            for (BodyBlock block : body) {
                if (block.imageRelId() != null) {
                    writeImageParagraph(w, block.imageRelId(), block.imageName(), block.imageStyle(), imageDocPrId++);
                } else {
                    writeTextParagraph(w, block.text(), block.textStyle());
                }
            }

            w.write("<w:sectPr>");
            w.write("<w:pgSz w:w=\"12240\" w:h=\"15840\"/>");
            int marginTop = toTwips(layoutStyle == null ? null : layoutStyle.marginTop(), 1440);
            int marginRight = toTwips(layoutStyle == null ? null : layoutStyle.marginRight(), 1440);
            int marginBottom = toTwips(layoutStyle == null ? null : layoutStyle.marginBottom(), 1440);
            int marginLeft = toTwips(layoutStyle == null ? null : layoutStyle.marginLeft(), 1440);
            w.write("<w:pgMar w:top=\"" + marginTop + "\" w:right=\"" + marginRight + "\" w:bottom=\"" + marginBottom
                    + "\" w:left=\"" + marginLeft + "\" w:header=\"708\" w:footer=\"708\" w:gutter=\"0\"/>");
            w.write("</w:sectPr>");
            w.write("</w:body>");
            w.write("</w:document>");
        }
    }

    private static void writeDocumentRels(Path path, Map<String, String> imageRelationships) throws Exception {
        try (BufferedWriter w = Files.newBufferedWriter(path, StandardCharsets.UTF_8)) {
            w.write("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>");
            w.write("<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">");
            w.write("<Relationship Id=\"rIdStyles\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles\" Target=\"styles.xml\"/>");
            if (imageRelationships != null) {
                for (Map.Entry<String, String> e : imageRelationships.entrySet()) {
                    String rid = e.getKey();
                    String target = e.getValue();
                    w.write("<Relationship Id=\"" + escapeXmlAttr(rid)
                            + "\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/image\" Target=\""
                            + escapeXmlAttr(target) + "\"/>");
                }
            }
            w.write("</Relationships>");
        }
    }

    private static void writeTextParagraph(BufferedWriter w, String text, VKOfficeTextStyle style) throws Exception {
        String v = text == null ? "" : text;
        w.write("<w:p>");
        if (style != null && (style.align() != null || style.lineSpacing() != null)) {
            w.write("<w:pPr>");
            if (style.align() != null) {
                w.write("<w:jc w:val=\"" + toWordAlign(style.align()) + "\"/>");
            }
            if (style.lineSpacing() != null && style.lineSpacing() > 0) {
                w.write("<w:spacing w:line=\"" + Math.max(1, (int) (style.lineSpacing() * 20)) + "\" w:lineRule=\"auto\"/>");
            }
            w.write("</w:pPr>");
        }
        String normalized = v.replace("\r\n", "\n").replace('\r', '\n');
        String[] lines = normalized.split("\\n", -1);
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];
            w.write("<w:r>");
            writeTextRunStyle(w, style);
            w.write("<w:t");
            if (needPreserveSpace(line)) {
                w.write(" xml:space=\"preserve\"");
            }
            w.write(">" + escapeXmlText(line) + "</w:t>");
            w.write("</w:r>");
            if (i < lines.length - 1) {
                w.write("<w:r><w:br/></w:r>");
            }
        }
        w.write("</w:p>");
    }

    private static void writeImageParagraph(BufferedWriter w,
                                            String rid,
                                            String imageName,
                                            VKOfficeImageStyle imageStyle,
                                            int docPrId) throws Exception {
        String name = imageName == null || imageName.isBlank() ? "image" : imageName;
        long cx = toEmu(imageStyle == null ? null : imageStyle.width(), DEFAULT_CX);
        long cy = toEmu(imageStyle == null ? null : imageStyle.height(), DEFAULT_CY);
        w.write("<w:p><w:r><w:drawing>");
        w.write("<wp:inline distT=\"0\" distB=\"0\" distL=\"0\" distR=\"0\">");
        w.write("<wp:extent cx=\"" + cx + "\" cy=\"" + cy + "\"/>");
        w.write("<wp:effectExtent l=\"0\" t=\"0\" r=\"0\" b=\"0\"/>");
        w.write("<wp:docPr id=\"" + docPrId + "\" name=\"" + escapeXmlAttr(name) + "\"/>");
        w.write("<wp:cNvGraphicFramePr><a:graphicFrameLocks noChangeAspect=\"1\"/></wp:cNvGraphicFramePr>");
        w.write("<a:graphic><a:graphicData uri=\"http://schemas.openxmlformats.org/drawingml/2006/picture\">");
        w.write("<pic:pic>");
        w.write("<pic:nvPicPr><pic:cNvPr id=\"0\" name=\"" + escapeXmlAttr(name) + "\"/><pic:cNvPicPr/></pic:nvPicPr>");
        w.write("<pic:blipFill><a:blip r:embed=\"" + escapeXmlAttr(rid) + "\"/><a:stretch><a:fillRect/></a:stretch></pic:blipFill>");
        w.write("<pic:spPr><a:xfrm><a:off x=\"0\" y=\"0\"/><a:ext cx=\"" + cx + "\" cy=\"" + cy + "\"/></a:xfrm>");
        w.write("<a:prstGeom prst=\"rect\"><a:avLst/></a:prstGeom></pic:spPr>");
        w.write("</pic:pic>");
        w.write("</a:graphicData></a:graphic>");
        w.write("</wp:inline>");
        w.write("</w:drawing></w:r></w:p>");
    }

    private static boolean needPreserveSpace(String value) {
        if (value == null || value.isEmpty()) {
            return false;
        }
        return Character.isWhitespace(value.charAt(0))
                || Character.isWhitespace(value.charAt(value.length() - 1));
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
                || (style.colorHex() != null && !style.colorHex().isBlank())
                || (style.fontSize() != null && style.fontSize() > 0)
                || (style.fontFamily() != null && !style.fontFamily().isBlank());
        if (!has) {
            return;
        }
        w.write("<w:rPr>");
        if (style.bold() != null && style.bold()) {
            w.write("<w:b/>");
        }
        if (style.italic() != null && style.italic()) {
            w.write("<w:i/>");
        }
        if (style.colorHex() != null && !style.colorHex().isBlank()) {
            w.write("<w:color w:val=\"" + sanitizeColor(style.colorHex()) + "\"/>");
        }
        if (style.fontSize() != null && style.fontSize() > 0) {
            int halfPt = style.fontSize() * 2;
            w.write("<w:sz w:val=\"" + halfPt + "\"/><w:szCs w:val=\"" + halfPt + "\"/>");
        }
        if (style.fontFamily() != null && !style.fontFamily().isBlank()) {
            String ff = escapeXmlAttr(style.fontFamily());
            w.write("<w:rFonts w:ascii=\"" + ff + "\" w:hAnsi=\"" + ff + "\" w:eastAsia=\"" + ff + "\"/>");
        }
        w.write("</w:rPr>");
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

    private static String toWordAlign(VKOfficeTextAlign align) {
        if (align == null) {
            return "left";
        }
        return switch (align) {
            case CENTER -> "center";
            case RIGHT -> "right";
            case JUSTIFY -> "both";
            default -> "left";
        };
    }

    private static int toTwips(Double pt, int fallback) {
        if (pt == null || pt <= 0) {
            return fallback;
        }
        return Math.max(1, (int) Math.round(pt * 20.0));
    }

    private static long toEmu(Integer px, long fallback) {
        if (px == null || px <= 0) {
            return fallback;
        }
        return Math.max(1, px.longValue() * 9525L);
    }

    /** 正文块：段落文本或图片引用。 */
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
