package yueyang.vostok.office.ppt;

import yueyang.vostok.office.style.VKOfficeImageStyle;
import yueyang.vostok.office.style.VKOfficeTextStyle;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** PPT 写入幻灯片，元素按添加顺序输出。 */
public final class VKPptWriteSlide {
    private final List<VKPptWriteElement> elements = new ArrayList<>();

    /** 追加任意元素。 */
    public VKPptWriteSlide addElement(VKPptWriteElement element) {
        if (element != null) {
            elements.add(element);
        }
        return this;
    }

    /** 追加文本段落。 */
    public VKPptWriteSlide addParagraph(String text) {
        elements.add(new VKPptParagraphElement(text));
        return this;
    }

    /** 追加文本段落（带样式）。 */
    public VKPptWriteSlide addParagraph(String text, VKOfficeTextStyle style) {
        elements.add(new VKPptParagraphElement(text, style));
        return this;
    }

    /** 追加 bytes 图片。 */
    public VKPptWriteSlide addImageBytes(String fileName, byte[] bytes) {
        elements.add(VKPptImageElement.fromBytes(fileName, bytes));
        return this;
    }

    /** 追加 bytes 图片（带样式）。 */
    public VKPptWriteSlide addImageBytes(String fileName, byte[] bytes, VKOfficeImageStyle style) {
        elements.add(VKPptImageElement.fromBytes(fileName, bytes, style));
        return this;
    }

    /** 追加本地图片路径。 */
    public VKPptWriteSlide addImageFile(String filePath) {
        elements.add(VKPptImageElement.fromFile(filePath));
        return this;
    }

    /** 追加本地图片路径（带样式）。 */
    public VKPptWriteSlide addImageFile(String filePath, VKOfficeImageStyle style) {
        elements.add(VKPptImageElement.fromFile(filePath, style));
        return this;
    }

    /** 返回只读元素列表。 */
    public List<VKPptWriteElement> elements() {
        return Collections.unmodifiableList(elements);
    }
}
