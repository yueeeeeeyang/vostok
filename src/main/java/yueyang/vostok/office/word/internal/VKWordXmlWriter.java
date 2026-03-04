package yueyang.vostok.office.word.internal;

import yueyang.vostok.office.exception.VKOfficeErrorCode;
import yueyang.vostok.office.exception.VKOfficeException;

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
                                    Set<String> imageExtensions) {
        try {
            Files.createDirectories(packageRoot.resolve("_rels"));
            Files.createDirectories(packageRoot.resolve("word/_rels"));
            Files.createDirectories(packageRoot.resolve("word/media"));

            writeRootRels(packageRoot.resolve("_rels/.rels"));
            writeContentTypes(packageRoot.resolve("[Content_Types].xml"), imageExtensions);
            writeStyles(packageRoot.resolve("word/styles.xml"));
            writeDocument(packageRoot.resolve("word/document.xml"), blocks);
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

    private static void writeDocument(Path path, List<BodyBlock> blocks) throws Exception {
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
                    writeImageParagraph(w, block.imageRelId(), block.imageName(), imageDocPrId++);
                } else {
                    writeTextParagraph(w, block.text());
                }
            }

            w.write("<w:sectPr>");
            w.write("<w:pgSz w:w=\"12240\" w:h=\"15840\"/>");
            w.write("<w:pgMar w:top=\"1440\" w:right=\"1440\" w:bottom=\"1440\" w:left=\"1440\" w:header=\"708\" w:footer=\"708\" w:gutter=\"0\"/>");
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

    private static void writeTextParagraph(BufferedWriter w, String text) throws Exception {
        String v = text == null ? "" : text;
        w.write("<w:p>");
        String normalized = v.replace("\r\n", "\n").replace('\r', '\n');
        String[] lines = normalized.split("\\n", -1);
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];
            w.write("<w:r><w:t");
            if (needPreserveSpace(line)) {
                w.write(" xml:space=\"preserve\"");
            }
            w.write(">" + escapeXmlText(line) + "</w:t></w:r>");
            if (i < lines.length - 1) {
                w.write("<w:r><w:br/></w:r>");
            }
        }
        w.write("</w:p>");
    }

    private static void writeImageParagraph(BufferedWriter w, String rid, String imageName, int docPrId) throws Exception {
        String name = imageName == null || imageName.isBlank() ? "image" : imageName;
        w.write("<w:p><w:r><w:drawing>");
        w.write("<wp:inline distT=\"0\" distB=\"0\" distL=\"0\" distR=\"0\">");
        w.write("<wp:extent cx=\"" + DEFAULT_CX + "\" cy=\"" + DEFAULT_CY + "\"/>");
        w.write("<wp:effectExtent l=\"0\" t=\"0\" r=\"0\" b=\"0\"/>");
        w.write("<wp:docPr id=\"" + docPrId + "\" name=\"" + escapeXmlAttr(name) + "\"/>");
        w.write("<wp:cNvGraphicFramePr><a:graphicFrameLocks noChangeAspect=\"1\"/></wp:cNvGraphicFramePr>");
        w.write("<a:graphic><a:graphicData uri=\"http://schemas.openxmlformats.org/drawingml/2006/picture\">");
        w.write("<pic:pic>");
        w.write("<pic:nvPicPr><pic:cNvPr id=\"0\" name=\"" + escapeXmlAttr(name) + "\"/><pic:cNvPicPr/></pic:nvPicPr>");
        w.write("<pic:blipFill><a:blip r:embed=\"" + escapeXmlAttr(rid) + "\"/><a:stretch><a:fillRect/></a:stretch></pic:blipFill>");
        w.write("<pic:spPr><a:xfrm><a:off x=\"0\" y=\"0\"/><a:ext cx=\"" + DEFAULT_CX + "\" cy=\"" + DEFAULT_CY + "\"/></a:xfrm>");
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

    /** 正文块：段落文本或图片引用。 */
    public static final class BodyBlock {
        private final String text;
        private final String imageRelId;
        private final String imageName;

        private BodyBlock(String text, String imageRelId, String imageName) {
            this.text = text;
            this.imageRelId = imageRelId;
            this.imageName = imageName;
        }

        public static BodyBlock paragraph(String text) {
            return new BodyBlock(text, null, null);
        }

        public static BodyBlock image(String imageRelId, String imageName) {
            return new BodyBlock(null, imageRelId, imageName);
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
    }
}
