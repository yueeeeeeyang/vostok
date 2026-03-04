package yueyang.vostok.office;

import yueyang.vostok.Vostok;
import yueyang.vostok.file.LocalFileStore;
import yueyang.vostok.file.VKUnzipOptions;
import yueyang.vostok.office.excel.VKExcelReadOptions;
import yueyang.vostok.office.excel.VKExcelRowView;
import yueyang.vostok.office.excel.VKExcelWorkbook;
import yueyang.vostok.office.excel.VKExcelWriteOptions;
import yueyang.vostok.office.excel.internal.VKExcelLimits;
import yueyang.vostok.office.excel.internal.VKExcelPackageReader;
import yueyang.vostok.office.excel.internal.VKExcelPackageWriter;
import yueyang.vostok.office.excel.internal.VKExcelSecurityGuard;
import yueyang.vostok.office.exception.VKOfficeErrorCode;
import yueyang.vostok.office.exception.VKOfficeException;
import yueyang.vostok.office.word.VKWordDocument;
import yueyang.vostok.office.word.VKWordImage;
import yueyang.vostok.office.word.VKWordReadOptions;
import yueyang.vostok.office.word.VKWordWriteOptions;
import yueyang.vostok.office.word.VKWordWriteRequest;
import yueyang.vostok.office.word.internal.VKWordLimits;
import yueyang.vostok.office.word.internal.VKWordPackageReader;
import yueyang.vostok.office.word.internal.VKWordPackageWriter;
import yueyang.vostok.office.word.internal.VKWordSecurityGuard;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * Office 模块门面。
 *
 * <p>当前提供 Excel / Word（.xlsx / .docx）读写能力，底层文件读写、压缩解压复用 File 模块。</p>
 */
public class VostokOffice {
    private static final Object LOCK = new Object();
    private static volatile boolean initialized;
    private static volatile VKOfficeConfig config;

    protected VostokOffice() {
    }

    /** 使用默认配置初始化。 */
    public static void init() {
        init(new VKOfficeConfig());
    }

    /** 显式初始化 Office 模块。 */
    public static void init(VKOfficeConfig officeConfig) {
        synchronized (LOCK) {
            VKOfficeConfig cfg = officeConfig == null ? new VKOfficeConfig() : officeConfig;
            validateConfig(cfg);
            config = copyConfig(cfg);
            initialized = true;
        }
    }

    /** 返回模块是否已初始化。 */
    public static boolean started() {
        return initialized;
    }

    /** 返回当前配置副本。 */
    public static VKOfficeConfig config() {
        ensureInitialized();
        return copyConfig(config);
    }

    /** 关闭模块并重置状态。 */
    public static void close() {
        synchronized (LOCK) {
            initialized = false;
            config = null;
        }
    }

    /** 读取 .xlsx（全量加载）。 */
    public static VKExcelWorkbook readExcel(String path) {
        return readExcel(path, VKExcelReadOptions.defaults());
    }

    /**
     * 按选项读取 .xlsx（全量加载）。
     *
     * <p>大文件建议改用 {@link #readExcelRows} 以避免整本工作簿占用内存。</p>
     */
    public static VKExcelWorkbook readExcel(String path, VKExcelReadOptions options) {
        ensureInitialized();
        ensureLocalFileMode();
        VKExcelSecurityGuard.assertSafePath(path);
        requireXlsx(path);

        VKExcelLimits limits = VKExcelLimits.fromRead(config, options);
        ensureFileBytesLimit(path, limits.maxWorkbookBytes(), "Excel workbook");
        VKExcelSecurityGuard.assertZipMagic(readLocalPath(path));

        String tempDir = createTempDir(limits.tempSubDir(), "xlsx_");
        try {
            // 读取路径：复用 File 模块 unzip 能力，Office 只负责解析 OOXML。
            Vostok.File.unzip(path, tempDir, buildUnzipOptions(config));
            Path packageRoot = locatePackageRoot(readLocalPath(tempDir));
            VKExcelPackageReader reader = new VKExcelPackageReader(packageRoot, limits);
            return reader.readWorkbook();
        } finally {
            cleanupTempDir(tempDir);
        }
    }

    /** 写入 .xlsx。 */
    public static void writeExcel(String path, VKExcelWorkbook workbook) {
        writeExcel(path, workbook, VKExcelWriteOptions.defaults());
    }

    /** 写入 .xlsx（含限制参数）。 */
    public static void writeExcel(String path, VKExcelWorkbook workbook, VKExcelWriteOptions options) {
        ensureInitialized();
        ensureLocalFileMode();
        VKExcelSecurityGuard.assertSafePath(path);
        requireXlsx(path);
        if (workbook == null) {
            throw new VKOfficeException(VKOfficeErrorCode.INVALID_ARGUMENT, "Excel workbook is null");
        }

        VKExcelLimits limits = VKExcelLimits.fromWrite(config, options);
        String tempDir = createTempDir(limits.tempSubDir(), "xlsx_");
        try {
            // 写入路径：先生成解包目录，再通过 File.zip(不含根目录) 打包为标准 xlsx。
            VKExcelPackageWriter writer = new VKExcelPackageWriter(readLocalPath(tempDir), limits);
            writer.writeWorkbook(workbook);
            Vostok.File.zip(tempDir, path, false);
            ensureFileBytesLimit(path, limits.maxWorkbookBytes(), "Excel workbook");
        } finally {
            cleanupTempDir(tempDir);
        }
    }

    /**
     * 流式逐行读取 .xlsx。
     *
     * @param sheetName 为空时读取第一个 sheet
     */
    public static void readExcelRows(String path,
                                     String sheetName,
                                     VKExcelReadOptions options,
                                     Consumer<VKExcelRowView> consumer) {
        ensureInitialized();
        ensureLocalFileMode();
        if (consumer == null) {
            throw new VKOfficeException(VKOfficeErrorCode.INVALID_ARGUMENT, "Excel row consumer is null");
        }
        VKExcelSecurityGuard.assertSafePath(path);
        requireXlsx(path);

        VKExcelLimits limits = VKExcelLimits.fromRead(config, options);
        ensureFileBytesLimit(path, limits.maxWorkbookBytes(), "Excel workbook");
        VKExcelSecurityGuard.assertZipMagic(readLocalPath(path));

        String tempDir = createTempDir(limits.tempSubDir(), "xlsx_");
        try {
            Vostok.File.unzip(path, tempDir, buildUnzipOptions(config));
            Path packageRoot = locatePackageRoot(readLocalPath(tempDir));
            VKExcelPackageReader reader = new VKExcelPackageReader(packageRoot, limits);
            reader.readRows(sheetName, consumer);
        } finally {
            cleanupTempDir(tempDir);
        }
    }

    /** 读取 docx 全文文本。 */
    public static String readWordText(String path) {
        return readWordText(path, VKWordReadOptions.defaults());
    }

    /** 读取 docx 全文文本。 */
    public static String readWordText(String path, VKWordReadOptions options) {
        return withWordReader(path, options, VKWordPackageReader::readText);
    }

    /** 读取 docx 全部图片。 */
    public static List<VKWordImage> readWordImages(String path) {
        return readWordImages(path, VKWordReadOptions.defaults());
    }

    /** 读取 docx 全部图片。 */
    public static List<VKWordImage> readWordImages(String path, VKWordReadOptions options) {
        return withWordReader(path, options, VKWordPackageReader::readImages);
    }

    /** 获取 docx 字数（按非空白 Unicode code point 统计）。 */
    public static int countWordChars(String path) {
        return countWordChars(path, VKWordReadOptions.defaults());
    }

    /** 获取 docx 字数（仅计数路径，不构建全文字符串）。 */
    public static int countWordChars(String path, VKWordReadOptions options) {
        return withWordReader(path, options, VKWordPackageReader::countChars);
    }

    /** 获取 docx 图片数量（按出现次数统计）。 */
    public static int countWordImages(String path) {
        return countWordImages(path, VKWordReadOptions.defaults());
    }

    /** 获取 docx 图片数量（按出现次数统计）。 */
    public static int countWordImages(String path, VKWordReadOptions options) {
        return withWordReader(path, options, VKWordPackageReader::countImages);
    }

    /** 一次解包聚合读取 docx（文本 + 字数 + 图片 + 图片数）。 */
    public static VKWordDocument readWord(String path) {
        return readWord(path, VKWordReadOptions.defaults());
    }

    /** 一次解包聚合读取 docx（文本 + 字数 + 图片 + 图片数）。 */
    public static VKWordDocument readWord(String path, VKWordReadOptions options) {
        return withWordReader(path, options, VKWordPackageReader::readDocument);
    }

    /** 写入 docx（文本 + 图片混排）。 */
    public static void writeWord(String path, VKWordWriteRequest request) {
        writeWord(path, request, VKWordWriteOptions.defaults());
    }

    /** 写入 docx（文本 + 图片混排）。 */
    public static void writeWord(String path, VKWordWriteRequest request, VKWordWriteOptions options) {
        ensureInitialized();
        ensureLocalFileMode();
        if (request == null) {
            throw new VKOfficeException(VKOfficeErrorCode.INVALID_ARGUMENT, "Word write request is null");
        }

        VKWordSecurityGuard.assertSafePath(path);
        requireDocx(path);

        VKWordLimits limits = VKWordLimits.fromWrite(config, options);
        String tempDir = createTempDir(limits.tempSubDir(), "docx_");
        try {
            Path packageRoot = readLocalPath(tempDir);
            Path baseDir = Path.of(Vostok.File.config().getBaseDir()).toAbsolutePath().normalize();
            VKWordPackageWriter writer = new VKWordPackageWriter(packageRoot, baseDir, limits);
            writer.writePackage(request);
            Vostok.File.zip(tempDir, path, false);
            ensureFileBytesLimit(path, limits.maxDocumentBytes(), "Word document");
        } finally {
            cleanupTempDir(tempDir);
        }
    }

    /**
     * Word 统一读取模板：
     * 负责安全校验、解包与清理，业务逻辑由 action 操作 VKWordPackageReader。
     */
    private static <T> T withWordReader(String path, VKWordReadOptions options, Function<VKWordPackageReader, T> action) {
        ensureInitialized();
        ensureLocalFileMode();
        if (action == null) {
            throw new VKOfficeException(VKOfficeErrorCode.INVALID_ARGUMENT, "Word reader action is null");
        }

        VKWordSecurityGuard.assertSafePath(path);
        requireDocx(path);

        VKWordLimits limits = VKWordLimits.fromRead(config, options);
        ensureFileBytesLimit(path, limits.maxDocumentBytes(), "Word document");
        VKWordSecurityGuard.assertZipMagic(readLocalPath(path));

        String tempDir = createTempDir(limits.tempSubDir(), "docx_");
        try {
            Vostok.File.unzip(path, tempDir, buildUnzipOptions(config));
            Path packageRoot = locatePackageRoot(readLocalPath(tempDir));
            VKWordPackageReader reader = new VKWordPackageReader(packageRoot, limits);
            return action.apply(reader);
        } finally {
            cleanupTempDir(tempDir);
        }
    }

    private static void ensureInitialized() {
        if (!initialized) {
            synchronized (LOCK) {
                if (!initialized) {
                    init(new VKOfficeConfig());
                }
            }
        }
    }

    private static void ensureLocalFileMode() {
        String mode = Vostok.File.currentMode();
        if (!LocalFileStore.MODE.equals(mode)) {
            throw new VKOfficeException(VKOfficeErrorCode.STATE_ERROR,
                    "Vostok.Office currently supports only local file mode, current=" + mode);
        }
    }

    private static void requireXlsx(String path) {
        if (path == null || !path.toLowerCase().endsWith(".xlsx")) {
            throw new VKOfficeException(VKOfficeErrorCode.UNSUPPORTED_FORMAT,
                    "Only .xlsx is supported: " + path);
        }
    }

    private static void requireDocx(String path) {
        if (path == null || !path.toLowerCase().endsWith(".docx")) {
            throw new VKOfficeException(VKOfficeErrorCode.UNSUPPORTED_FORMAT,
                    "Only .docx is supported: " + path);
        }
    }

    private static String createTempDir(String subDir, String prefix) {
        String tempFile = Vostok.File.createTemp(subDir, prefix, ".tmp");
        Vostok.File.deleteIfExists(tempFile);
        Vostok.File.mkdirs(tempFile);
        return tempFile;
    }

    private static void cleanupTempDir(String tempDir) {
        if (tempDir == null || tempDir.isBlank()) {
            return;
        }
        try {
            Vostok.File.deleteRecursively(tempDir);
        } catch (Exception ignore) {
        }
    }

    private static VKUnzipOptions buildUnzipOptions(VKOfficeConfig cfg) {
        return VKUnzipOptions.builder()
                .replaceExisting(true)
                .maxEntries(cfg.getUnzipMaxEntries())
                .maxTotalUncompressedBytes(cfg.getUnzipMaxTotalUncompressedBytes())
                .maxEntryUncompressedBytes(cfg.getUnzipMaxEntryUncompressedBytes())
                .build();
    }

    private static Path locatePackageRoot(Path unpackDir) {
        if (Files.exists(unpackDir.resolve("[Content_Types].xml"))) {
            return unpackDir;
        }
        // 部分 zip 工具会把整个目录作为根节点打包：这里允许单层目录包裹。
        try (var children = Files.list(unpackDir)) {
            Path[] dirs = children.filter(Files::isDirectory).limit(2).toArray(Path[]::new);
            if (dirs.length == 1 && Files.exists(dirs[0].resolve("[Content_Types].xml"))) {
                return dirs[0].normalize();
            }
        } catch (IOException e) {
            throw new VKOfficeException(VKOfficeErrorCode.PARSE_ERROR,
                    "Resolve OOXML package root failed: " + unpackDir, e);
        }
        throw new VKOfficeException(VKOfficeErrorCode.PARSE_ERROR,
                "Invalid OOXML package: missing [Content_Types].xml");
    }

    private static void ensureFileBytesLimit(String path, long max, String targetName) {
        if (max <= 0) {
            return;
        }
        long size = Vostok.File.size(path);
        if (size > max) {
            throw new VKOfficeException(VKOfficeErrorCode.LIMIT_EXCEEDED,
                    targetName + " bytes exceed limit: " + size + " > " + max);
        }
    }

    private static Path readLocalPath(String relativePath) {
        if (relativePath == null || relativePath.isBlank()) {
            throw new VKOfficeException(VKOfficeErrorCode.INVALID_ARGUMENT, "Path is blank");
        }
        return resolveAgainstFileBase(relativePath);
    }

    private static Path resolveAgainstFileBase(String relativePath) {
        Path base = Path.of(Vostok.File.config().getBaseDir()).toAbsolutePath().normalize();
        Path rel = Path.of(relativePath.trim());
        Path resolved = rel.isAbsolute() ? rel.toAbsolutePath().normalize() : base.resolve(rel).normalize();
        if (!resolved.startsWith(base)) {
            throw new VKOfficeException(VKOfficeErrorCode.SECURITY_ERROR,
                    "Path escapes file baseDir: " + relativePath);
        }
        return resolved;
    }

    private static VKOfficeConfig copyConfig(VKOfficeConfig source) {
        return new VKOfficeConfig()
                .excelMaxSheets(source.getExcelMaxSheets())
                .excelMaxRowsPerSheet(source.getExcelMaxRowsPerSheet())
                .excelMaxColsPerRow(source.getExcelMaxColsPerRow())
                .excelMaxCellChars(source.getExcelMaxCellChars())
                .excelMaxSharedStrings(source.getExcelMaxSharedStrings())
                .excelMaxWorkbookBytes(source.getExcelMaxWorkbookBytes())
                .excelTempDir(source.getExcelTempDir())
                .wordMaxDocumentBytes(source.getWordMaxDocumentBytes())
                .wordMaxTextChars(source.getWordMaxTextChars())
                .wordMaxImages(source.getWordMaxImages())
                .wordMaxSingleImageBytes(source.getWordMaxSingleImageBytes())
                .wordMaxTotalImageBytes(source.getWordMaxTotalImageBytes())
                .wordTempDir(source.getWordTempDir())
                .xxeSampleBytes(source.getXxeSampleBytes())
                .unzipMaxEntries(source.getUnzipMaxEntries())
                .unzipMaxTotalUncompressedBytes(source.getUnzipMaxTotalUncompressedBytes())
                .unzipMaxEntryUncompressedBytes(source.getUnzipMaxEntryUncompressedBytes());
    }

    private static void validateConfig(VKOfficeConfig cfg) {
        if (cfg.getExcelMaxSheets() <= 0
                || cfg.getExcelMaxRowsPerSheet() <= 0
                || cfg.getExcelMaxColsPerRow() <= 0
                || cfg.getExcelMaxCellChars() <= 0
                || cfg.getExcelMaxSharedStrings() <= 0
                || cfg.getExcelMaxWorkbookBytes() <= 0
                || cfg.getWordMaxDocumentBytes() <= 0
                || cfg.getWordMaxTextChars() <= 0
                || cfg.getWordMaxImages() <= 0
                || cfg.getWordMaxSingleImageBytes() <= 0
                || cfg.getWordMaxTotalImageBytes() <= 0
                || cfg.getXxeSampleBytes() <= 0) {
            throw new VKOfficeException(VKOfficeErrorCode.CONFIG_ERROR,
                    "Invalid office limits in VKOfficeConfig");
        }
        if (cfg.getExcelTempDir() == null || cfg.getExcelTempDir().trim().isEmpty()) {
            throw new VKOfficeException(VKOfficeErrorCode.CONFIG_ERROR,
                    "Office excelTempDir is blank");
        }
        if (cfg.getWordTempDir() == null || cfg.getWordTempDir().trim().isEmpty()) {
            throw new VKOfficeException(VKOfficeErrorCode.CONFIG_ERROR,
                    "Office wordTempDir is blank");
        }
    }
}
