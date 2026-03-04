package yueyang.vostok.office.ppt;

import yueyang.vostok.office.style.VKOfficeLayoutStyle;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** PPT 写入请求，包含多页幻灯片。 */
public final class VKPptWriteRequest {
    private final List<VKPptWriteSlide> slides = new ArrayList<>();
    private VKOfficeLayoutStyle layoutStyle;

    /** 追加幻灯片。 */
    public VKPptWriteRequest addSlide(VKPptWriteSlide slide) {
        if (slide != null) {
            slides.add(slide);
        }
        return this;
    }

    /** 新增一页并返回，便于链式追加元素。 */
    public VKPptWriteSlide addSlide() {
        VKPptWriteSlide slide = new VKPptWriteSlide();
        slides.add(slide);
        return slide;
    }

    /** 返回只读幻灯片列表。 */
    public List<VKPptWriteSlide> slides() {
        return Collections.unmodifiableList(slides);
    }

    /** 设置页面级版式（可选）。 */
    public VKPptWriteRequest layoutStyle(VKOfficeLayoutStyle layoutStyle) {
        this.layoutStyle = layoutStyle;
        return this;
    }

    /** 获取页面级版式。 */
    public VKOfficeLayoutStyle layoutStyle() {
        return layoutStyle;
    }
}
