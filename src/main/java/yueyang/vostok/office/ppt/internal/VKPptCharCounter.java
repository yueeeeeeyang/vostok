package yueyang.vostok.office.ppt.internal;

import yueyang.vostok.office.exception.VKOfficeErrorCode;
import yueyang.vostok.office.exception.VKOfficeException;

/** PPT 字符统计器：仅统计非空白 Unicode code point。 */
public final class VKPptCharCounter {
    private final int maxChars;
    private int count;

    public VKPptCharCounter(int maxChars) {
        this.maxChars = maxChars;
    }

    public void onText(String text) {
        count += countNonWhitespace(text);
        if (maxChars > 0 && count > maxChars) {
            throw new VKOfficeException(VKOfficeErrorCode.LIMIT_EXCEEDED,
                    "PPT chars exceed limit: " + count + " > " + maxChars);
        }
    }

    public int charCount() {
        return count;
    }

    public static int countNonWhitespace(String text) {
        if (text == null || text.isEmpty()) {
            return 0;
        }
        int c = 0;
        for (int i = 0; i < text.length(); ) {
            int cp = text.codePointAt(i);
            i += Character.charCount(cp);
            if (!Character.isWhitespace(cp)) {
                c++;
            }
        }
        return c;
    }
}
