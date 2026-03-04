package yueyang.vostok.office.ppt.internal;

import yueyang.vostok.office.excel.internal.VKXmlSafeFactory;
import yueyang.vostok.office.exception.VKOfficeErrorCode;
import yueyang.vostok.office.exception.VKOfficeException;

import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamReader;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

/** 基于 StAX 的 PPT XML 扫描器。 */
public final class VKPptXmlScanner {

    /** 扫描回调。 */
    public interface Sink {
        void onText(String text);

        void onParagraphEnd();

        void onImageRef(String relationId);
    }

    /** 空实现，方便局部覆盖。 */
    public static class NoopSink implements Sink {
        @Override
        public void onText(String text) {
        }

        @Override
        public void onParagraphEnd() {
        }

        @Override
        public void onImageRef(String relationId) {
        }
    }

    private VKPptXmlScanner() {
    }

    public static void scan(Path xmlPath, Sink sink) {
        XMLInputFactory factory = VKXmlSafeFactory.createInputFactory();
        Sink out = sink == null ? new NoopSink() : sink;
        try (InputStream in = Files.newInputStream(xmlPath)) {
            XMLStreamReader r = factory.createXMLStreamReader(in);
            while (r.hasNext()) {
                int event = r.next();
                if (event == XMLStreamConstants.START_ELEMENT) {
                    String local = r.getLocalName();
                    if ("t".equals(local)) {
                        out.onText(r.getElementText());
                        continue;
                    }
                    if ("br".equals(local)) {
                        out.onParagraphEnd();
                        continue;
                    }
                    if ("blip".equals(local)) {
                        String embed = attr(r, "embed");
                        if (embed != null && !embed.isBlank()) {
                            out.onImageRef(embed);
                        }
                    }
                } else if (event == XMLStreamConstants.END_ELEMENT) {
                    if ("p".equals(r.getLocalName())) {
                        out.onParagraphEnd();
                    }
                }
            }
        } catch (VKOfficeException e) {
            throw e;
        } catch (Exception e) {
            throw new VKOfficeException(VKOfficeErrorCode.PARSE_ERROR,
                    "Scan ppt xml failed: " + xmlPath, e);
        }
    }

    private static String attr(XMLStreamReader r, String localName) {
        for (int i = 0; i < r.getAttributeCount(); i++) {
            if (localName.equals(r.getAttributeLocalName(i))) {
                return r.getAttributeValue(i);
            }
        }
        return null;
    }
}
