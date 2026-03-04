package yueyang.vostok.office.pdf.internal;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

/** 把页面元素排版为 PDF 内容流命令。 */
public final class VKPdfPageComposer {
    private static final double PAGE_WIDTH = 595.0;
    private static final double PAGE_HEIGHT = 842.0;
    private static final double MARGIN_LEFT = 50.0;
    private static final double MARGIN_BOTTOM = 40.0;

    private VKPdfPageComposer() {
    }

    public static byte[] compose(List<Block> blocks, Map<String, ImageBox> imageBoxes) {
        StringBuilder sb = new StringBuilder(512);
        double y = PAGE_HEIGHT - 40;

        for (Block block : blocks) {
            if (block == null) {
                continue;
            }
            if (block.type() == BlockType.TEXT) {
                String text = block.text() == null ? "" : block.text();
                String[] lines = text.replace("\r\n", "\n").replace('\r', '\n').split("\\n", -1);
                for (String line : lines) {
                    if (y < MARGIN_BOTTOM + 20) {
                        y = PAGE_HEIGHT - 40;
                    }
                    sb.append("BT /F1 12 Tf ")
                            .append(format(MARGIN_LEFT)).append(' ')
                            .append(format(y)).append(" Td (")
                            .append(escapePdfText(line)).append(") Tj ET\n");
                    y -= 16;
                }
                continue;
            }

            if (block.type() == BlockType.IMAGE) {
                ImageBox box = imageBoxes.get(block.imageName());
                if (box == null) {
                    continue;
                }
                double drawW = box.width();
                double drawH = box.height();
                double maxW = PAGE_WIDTH - MARGIN_LEFT * 2;
                double maxH = 260;
                double scale = Math.min(1.0, Math.min(maxW / Math.max(drawW, 1), maxH / Math.max(drawH, 1)));
                drawW = Math.max(1, drawW * scale);
                drawH = Math.max(1, drawH * scale);

                if (y - drawH < MARGIN_BOTTOM) {
                    y = PAGE_HEIGHT - 40;
                }
                double posY = y - drawH;

                sb.append("q ")
                        .append(format(drawW)).append(" 0 0 ")
                        .append(format(drawH)).append(' ')
                        .append(format(MARGIN_LEFT)).append(' ')
                        .append(format(posY)).append(" cm /")
                        .append(block.imageName())
                        .append(" Do Q\n");
                y = posY - 18;
            }
        }

        return sb.toString().getBytes(StandardCharsets.ISO_8859_1);
    }

    /** 页面元素块。 */
    public record Block(BlockType type, String text, String imageName) {
        public static Block text(String text) {
            return new Block(BlockType.TEXT, text, null);
        }

        public static Block image(String imageName) {
            return new Block(BlockType.IMAGE, null, imageName);
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
}
