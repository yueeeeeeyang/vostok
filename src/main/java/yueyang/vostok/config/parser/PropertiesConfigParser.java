package yueyang.vostok.config.parser;

import yueyang.vostok.config.exception.VKConfigErrorCode;
import yueyang.vostok.config.exception.VKConfigException;

import java.io.IOException;
import java.io.InputStream;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Properties;

public class PropertiesConfigParser implements VKConfigParser {
    @Override
    public boolean supports(String fileName) {
        return fileName != null && fileName.toLowerCase().endsWith(".properties");
    }

    @Override
    public Map<String, String> parse(String sourceId, InputStream inputStream) {
        Properties p = new Properties();
        try (InputStream in = inputStream) {
            p.load(in);
        } catch (IOException e) {
            throw new VKConfigException(VKConfigErrorCode.IO_ERROR, "Failed to read properties: " + sourceId, e);
        }

        Map<String, String> out = new LinkedHashMap<>();
        for (String name : p.stringPropertyNames()) {
            out.put(name, p.getProperty(name));
        }
        return out;
    }
}
