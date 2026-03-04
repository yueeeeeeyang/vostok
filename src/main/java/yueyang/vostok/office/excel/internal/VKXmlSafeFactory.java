package yueyang.vostok.office.excel.internal;

import yueyang.vostok.office.exception.VKOfficeErrorCode;
import yueyang.vostok.office.exception.VKOfficeException;

import javax.xml.stream.XMLInputFactory;

/** 构建禁用外部实体/DTD 的 XMLInputFactory。 */
public final class VKXmlSafeFactory {
    private VKXmlSafeFactory() {
    }

    public static XMLInputFactory createInputFactory() {
        try {
            XMLInputFactory factory = XMLInputFactory.newFactory();
            factory.setProperty(XMLInputFactory.SUPPORT_DTD, false);
            factory.setProperty(XMLInputFactory.IS_SUPPORTING_EXTERNAL_ENTITIES, false);
            factory.setProperty(XMLInputFactory.IS_REPLACING_ENTITY_REFERENCES, false);
            return factory;
        } catch (Exception e) {
            throw new VKOfficeException(VKOfficeErrorCode.SECURITY_ERROR,
                    "Create safe XMLInputFactory failed", e);
        }
    }
}
