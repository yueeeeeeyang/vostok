package yueyang.vostok.office.word;

import yueyang.vostok.office.style.VKOfficeImageStyle;
import yueyang.vostok.office.style.VKOfficeLayoutStyle;
import yueyang.vostok.office.style.VKOfficeTextStyle;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Word 写入请求，元素按添加顺序输出到正文。 */
public final class VKWordWriteRequest {
    private final List<VKWordWriteElement> elements = new ArrayList<>();
    private VKOfficeLayoutStyle layoutStyle;

    /** 追加任意写入元素。 */
    public VKWordWriteRequest addElement(VKWordWriteElement element) {
        if (element != null) {
            elements.add(element);
        }
        return this;
    }

    /** 追加段落。 */
    public VKWordWriteRequest addParagraph(String text) {
        elements.add(new VKWordParagraphElement(text));
        return this;
    }

    /** 追加段落（带样式）。 */
    public VKWordWriteRequest addParagraph(String text, VKOfficeTextStyle style) {
        elements.add(new VKWordParagraphElement(text, style));
        return this;
    }

    /** 追加 bytes 图片。 */
    public VKWordWriteRequest addImageBytes(String fileName, byte[] bytes) {
        elements.add(VKWordImageElement.fromBytes(fileName, bytes));
        return this;
    }

    /** 追加 bytes 图片（带样式）。 */
    public VKWordWriteRequest addImageBytes(String fileName, byte[] bytes, VKOfficeImageStyle style) {
        elements.add(VKWordImageElement.fromBytes(fileName, bytes, style));
        return this;
    }

    /** 追加文件路径图片。 */
    public VKWordWriteRequest addImageFile(String filePath) {
        elements.add(VKWordImageElement.fromFile(filePath));
        return this;
    }

    /** 追加文件路径图片（带样式）。 */
    public VKWordWriteRequest addImageFile(String filePath, VKOfficeImageStyle style) {
        elements.add(VKWordImageElement.fromFile(filePath, style));
        return this;
    }

    /** 返回只读元素列表。 */
    public List<VKWordWriteElement> elements() {
        return Collections.unmodifiableList(elements);
    }

    /** 设置页面级版式（可选）。 */
    public VKWordWriteRequest layoutStyle(VKOfficeLayoutStyle layoutStyle) {
        this.layoutStyle = layoutStyle;
        return this;
    }

    /** 获取页面级版式。 */
    public VKOfficeLayoutStyle layoutStyle() {
        return layoutStyle;
    }
}
