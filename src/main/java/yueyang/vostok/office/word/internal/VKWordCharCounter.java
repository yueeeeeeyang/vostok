package yueyang.vostok.office.word.internal;

import yueyang.vostok.office.exception.VKOfficeErrorCode;
import yueyang.vostok.office.exception.VKOfficeException;

/** Word 字符统计器：仅统计非空白 Unicode code point。 */
public final class VKWordCharCounter extends VKWordXmlScanner.NoopSink {
    private final int maxChars;
    private int charCount;

    public VKWordCharCounter(int maxChars) {
        this.maxChars = maxChars;
    }

    @Override
    public void onText(String text) {
        charCount += countNonWhitespace(text);
        ensureLimit();
    }

    public int charCount() {
        return charCount;
    }

    public static int countNonWhitespace(String text) {
        if (text == null || text.isEmpty()) {
            return 0;
        }
        int count = 0;
        int i = 0;
        while (i < text.length()) {
            int cp = text.codePointAt(i);
            if (!Character.isWhitespace(cp)) {
                count++;
            }
            i += Character.charCount(cp);
        }
        return count;
    }

    private void ensureLimit() {
        if (maxChars > 0 && charCount > maxChars) {
            throw new VKOfficeException(VKOfficeErrorCode.LIMIT_EXCEEDED,
                    "Word chars exceed limit: " + charCount + " > " + maxChars);
        }
    }
}
