package yueyang.vostok.util;

import yueyang.vostok.data.exception.VKArgumentException;

import java.util.regex.Pattern;

public final class VKNameValidator {
    private static final Pattern NAME_PATTERN = Pattern.compile("[A-Za-z0-9_]+(\\.[A-Za-z0-9_]+)?");

    private VKNameValidator() {
    }

    public static void validate(String name, String desc) {
        VKAssert.notBlank(name, desc + " is blank");
        if (!NAME_PATTERN.matcher(name).matches()) {
            throw new VKArgumentException(desc + " invalid: " + name);
        }
    }
}
