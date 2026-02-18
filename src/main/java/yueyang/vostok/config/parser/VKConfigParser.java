package yueyang.vostok.config.parser;

import java.io.InputStream;
import java.util.Map;

public interface VKConfigParser {
    boolean supports(String fileName);

    Map<String, String> parse(String sourceId, InputStream inputStream);
}
