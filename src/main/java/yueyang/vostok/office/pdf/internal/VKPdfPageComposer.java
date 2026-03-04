package yueyang.vostok.office.pdf.internal;

import yueyang.vostok.office.style.VKOfficeImageFit;
import yueyang.vostok.office.style.VKOfficeImageStyle;
import yueyang.vostok.office.style.VKOfficeLayoutStyle;
import yueyang.vostok.office.style.VKOfficeTextAlign;
import yueyang.vostok.office.style.VKOfficeTextStyle;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

/** 把页面元素排版为 PDF 内容流命令。 */
public final class VKPdfPageComposer {
    private static final double PAGE_WIDTH = 595.0;
    private static final double PAGE_HEIGHT = 842.0;
    private static final double DEFAULT_MARGIN_LEFT = 50.0;
    private static final double DEFAULT_MARGIN_RIGHT = 50.0;
    private static final double DEFAULT_MARGIN_TOP = 40.0;
    private static final double DEFAULT_MARGIN_BOTTOM = 40.0;
    private static final double DEFAULT_BLOCK_SPACING = 18.0;

    private VKPdfPageComposer() {
    }

    public static byte[] compose(List<Block> blocks, Map<String, ImageBox> imageBoxes) {
        return compose(blocks, imageBoxes, null);
    }

    public static byte[] compose(List<Block> blocks,
                                 Map<String, ImageBox> imageBoxes,
                                 VKOfficeLayoutStyle layoutStyle) {
        StringBuilder sb = new StringBuilder(512);
        double marginLeft = positiveOr(layoutStyle == null ? null : layoutStyle.marginLeft(), DEFAULT_MARGIN_LEFT);
        double marginRight = positiveOr(layoutStyle == null ? null : layoutStyle.marginRight(), DEFAULT_MARGIN_RIGHT);
        double marginTop = positiveOr(layoutStyle == null ? null : layoutStyle.marginTop(), DEFAULT_MARGIN_TOP);
        double marginBottom = positiveOr(layoutStyle == null ? null : layoutStyle.marginBottom(), DEFAULT_MARGIN_BOTTOM);
        double blockSpacing = positiveOr(layoutStyle == null ? null : layoutStyle.blockSpacing(), DEFAULT_BLOCK_SPACING);
        double defaultLineHeight = positiveOr(layoutStyle == null ? null : layoutStyle.defaultLineHeight(), 16.0);
        double y = PAGE_HEIGHT - marginTop;

        for (Block block : blocks) {
            if (block == null) {
                continue;
            }
            if (block.type() == BlockType.TEXT) {
                String text = block.text() == null ? "" : block.text();
                VKOfficeTextStyle style = block.textStyle();
                double fontSize = positiveOr(style == null ? null : asDouble(style.fontSize()), 12.0);
                double lineHeight = positiveOr(style == null ? null : style.lineSpacing(), defaultLineHeight);
                String[] lines = text.replace("\r\n", "\n").replace('\r', '\n').split("\\n", -1);
                for (String line : lines) {
                    if (y < marginBottom + lineHeight) {
                        y = PAGE_HEIGHT - marginTop;
                    }
                    double x = resolveTextX(line, style == null ? null : style.align(), fontSize, marginLeft, marginRight);
                    sb.append("BT /F1 ").append(format(fontSize)).append(" Tf ")
                            .append(format(x)).append(' ')
                            .append(format(y)).append(" Td (")
                            .append(escapePdfText(line)).append(") Tj ET\n");
                    y -= lineHeight;
                }
                y -= blockSpacing * 0.2;
                continue;
            }

            if (block.type() == BlockType.IMAGE) {
                ImageBox box = imageBoxes.get(block.imageName());
                if (box == null) {
                    continue;
                }
                VKOfficeImageStyle imageStyle = block.imageStyle();
                double drawW = positiveOr(imageStyle == null ? null : asDouble(imageStyle.width()), box.width());
                double drawH = positiveOr(imageStyle == null ? null : asDouble(imageStyle.height()), box.height());
                double maxW = PAGE_WIDTH - marginLeft - marginRight;
                double maxH = 260;
                boolean keepAspect = imageStyle == null || imageStyle.keepAspectRatio() == null || imageStyle.keepAspectRatio();
                if (keepAspect) {
                    double scale = Math.min(1.0, Math.min(maxW / Math.max(drawW, 1), maxH / Math.max(drawH, 1)));
                    drawW = Math.max(1, drawW * scale);
                    drawH = Math.max(1, drawH * scale);
                    if (imageStyle != null && imageStyle.fit() == VKOfficeImageFit.COVER) {
                        double coverScale = Math.max(maxW / Math.max(drawW, 1), maxH / Math.max(drawH, 1));
                        drawW = Math.max(1, drawW * coverScale);
                        drawH = Math.max(1, drawH * coverScale);
                    }
                } else if (imageStyle != null && imageStyle.fit() == VKOfficeImageFit.CONTAIN) {
                    double scale = Math.min(1.0, Math.min(maxW / Math.max(drawW, 1), maxH / Math.max(drawH, 1)));
                    drawW = Math.max(1, drawW * scale);
                    drawH = Math.max(1, drawH * scale);
                } else if (imageStyle != null && imageStyle.fit() == VKOfficeImageFit.STRETCH) {
                    drawW = maxW;
                    drawH = maxH;
                }

                if (y - drawH < marginBottom) {
                    y = PAGE_HEIGHT - marginTop;
                }
                double posY = y - drawH;

                sb.append("q ")
                        .append(format(drawW)).append(" 0 0 ")
                        .append(format(drawH)).append(' ')
                        .append(format(marginLeft)).append(' ')
                        .append(format(posY)).append(" cm /")
                        .append(block.imageName())
                        .append(" Do Q\n");
                y = posY - blockSpacing;
            }
        }

        return sb.toString().getBytes(StandardCharsets.ISO_8859_1);
    }

    /** 页面元素块。 */
    public record Block(BlockType type,
                        String text,
                        String imageName,
                        VKOfficeTextStyle textStyle,
                        VKOfficeImageStyle imageStyle) {
        public static Block text(String text) {
            return new Block(BlockType.TEXT, text, null, null, null);
        }

        public static Block text(String text, VKOfficeTextStyle textStyle) {
            return new Block(BlockType.TEXT, text, null, textStyle, null);
        }

        public static Block image(String imageName) {
            return new Block(BlockType.IMAGE, null, imageName, null, null);
        }

        public static Block image(String imageName, VKOfficeImageStyle imageStyle) {
            return new Block(BlockType.IMAGE, null, imageName, null, imageStyle);
        }
    }

    /** 图片尺寸盒子。 */
    public record ImageBox(int width, int height) {
    }

    /** 页面元素类型。 */
    public enum BlockType {
        TEXT,
        IMAGE
    }

    private static String escapePdfText(String text) {
        if (text == null || text.isEmpty()) {
            return "";
        }
        StringBuilder out = new StringBuilder(text.length() + 16);
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            switch (c) {
                case '\\' -> out.append("\\\\");
                case '(' -> out.append("\\(");
                case ')' -> out.append("\\)");
                case '\n' -> out.append("\\n");
                case '\r' -> out.append("\\r");
                case '\t' -> out.append("\\t");
                default -> out.append(c);
            }
        }
        return out.toString();
    }

    private static String format(double v) {
        long i = (long) v;
        if (Math.abs(v - i) < 0.0001) {
            return Long.toString(i);
        }
        return String.format(java.util.Locale.ROOT, "%.2f", v);
    }

    private static double resolveTextX(String text,
                                       VKOfficeTextAlign align,
                                       double fontSize,
                                       double marginLeft,
                                       double marginRight) {
        if (align == null || align == VKOfficeTextAlign.LEFT || align == VKOfficeTextAlign.JUSTIFY) {
            return marginLeft;
        }
        double width = Math.max(1, text == null ? 0 : text.length()) * Math.max(6.0, fontSize * 0.52);
        double usable = PAGE_WIDTH - marginLeft - marginRight;
        if (align == VKOfficeTextAlign.CENTER) {
            return Math.max(marginLeft, marginLeft + (usable - width) / 2.0);
        }
        if (align == VKOfficeTextAlign.RIGHT) {
            return Math.max(marginLeft, PAGE_WIDTH - marginRight - width);
        }
        return marginLeft;
    }

    private static Double asDouble(Integer v) {
        return v == null ? null : v.doubleValue();
    }

    private static double positiveOr(Double value, double fallback) {
        if (value == null || value <= 0) {
            return fallback;
        }
        return value;
    }
}
