package yueyang.vostok.office.word;

/** Word 图片读取模式。 */
public enum VKWordImageLoadMode {
    /** 默认模式：返回图片元数据 + bytes。 */
    BYTES,
    /** 大文件模式：仅返回元数据，不加载 bytes。 */
    METADATA_ONLY
}
