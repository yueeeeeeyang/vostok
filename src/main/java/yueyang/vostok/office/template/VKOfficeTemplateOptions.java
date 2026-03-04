package yueyang.vostok.office.template;

/** 模板渲染选项。 */
public final class VKOfficeTemplateOptions {
    private int maxOutputChars = 20_000_000;
    private boolean strictVariable;

    public static VKOfficeTemplateOptions defaults() {
        return new VKOfficeTemplateOptions();
    }

    public int maxOutputChars() {
        return maxOutputChars;
    }

    public VKOfficeTemplateOptions maxOutputChars(int maxOutputChars) {
        this.maxOutputChars = maxOutputChars;
        return this;
    }

    public boolean strictVariable() {
        return strictVariable;
    }

    public VKOfficeTemplateOptions strictVariable(boolean strictVariable) {
        this.strictVariable = strictVariable;
        return this;
    }
}
