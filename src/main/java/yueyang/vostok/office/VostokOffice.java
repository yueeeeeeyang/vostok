package yueyang.vostok.office;

import yueyang.vostok.Vostok;
import yueyang.vostok.file.LocalFileStore;
import yueyang.vostok.file.VKUnzipOptions;
import yueyang.vostok.office.excel.VKExcelReadOptions;
import yueyang.vostok.office.excel.VKExcelRowView;
import yueyang.vostok.office.excel.VKExcelSheet;
import yueyang.vostok.office.excel.VKExcelWorkbook;
import yueyang.vostok.office.excel.VKExcelWriteOptions;
import yueyang.vostok.office.excel.template.VKExcelTemplateOptions;
import yueyang.vostok.office.convert.VKOfficeConvertOptions;
import yueyang.vostok.office.excel.internal.VKExcelLimits;
import yueyang.vostok.office.excel.internal.VKExcelPackageReader;
import yueyang.vostok.office.excel.internal.VKExcelPackageWriter;
import yueyang.vostok.office.excel.internal.VKExcelSecurityGuard;
import yueyang.vostok.office.excel.template.internal.VKExcelTemplateRenderer;
import yueyang.vostok.office.excel.template.internal.VKExcelTemplateSecurityGuard;
import yueyang.vostok.office.exception.VKOfficeErrorCode;
import yueyang.vostok.office.exception.VKOfficeException;
import yueyang.vostok.office.job.VKOfficeJobDeadLetterHandler;
import yueyang.vostok.office.job.VKOfficeJobFilter;
import yueyang.vostok.office.job.VKOfficeJobInfo;
import yueyang.vostok.office.job.VKOfficeJobListener;
import yueyang.vostok.office.job.VKOfficeJobNotification;
import yueyang.vostok.office.job.VKOfficeJobQuery;
import yueyang.vostok.office.job.VKOfficeJobRequest;
import yueyang.vostok.office.job.VKOfficeJobResult;
import yueyang.vostok.office.job.VKOfficeJobRuntime;
import yueyang.vostok.office.job.VKOfficeJobStatus;
import yueyang.vostok.office.job.VKOfficeJobSubscription;
import yueyang.vostok.office.pdf.VKPdfDocument;
import yueyang.vostok.office.pdf.VKPdfImage;
import yueyang.vostok.office.pdf.VKPdfReadOptions;
import yueyang.vostok.office.pdf.VKPdfWriteOptions;
import yueyang.vostok.office.pdf.VKPdfWriteRequest;
import yueyang.vostok.office.pdf.internal.VKPdfDocumentReader;
import yueyang.vostok.office.pdf.internal.VKPdfLimits;
import yueyang.vostok.office.pdf.internal.VKPdfSecurityGuard;
import yueyang.vostok.office.pdf.internal.VKPdfWriter;
import yueyang.vostok.office.pdf.stream.VKPdfStreamBlock;
import yueyang.vostok.office.pdf.stream.VKPdfStreamBlockType;
import yueyang.vostok.office.pdf.stream.VKPdfStreamOptions;
import yueyang.vostok.office.pdf.structured.VKPdfStructuredDocument;
import yueyang.vostok.office.pdf.structured.VKPdfStructuredNode;
import yueyang.vostok.office.pdf.structured.VKPdfStructuredNodeType;
import yueyang.vostok.office.pdf.structured.VKPdfStructuredOptions;
import yueyang.vostok.office.ppt.VKPptDocument;
import yueyang.vostok.office.ppt.VKPptImage;
import yueyang.vostok.office.ppt.VKPptReadOptions;
import yueyang.vostok.office.ppt.VKPptWriteOptions;
import yueyang.vostok.office.ppt.VKPptWriteRequest;
import yueyang.vostok.office.ppt.internal.VKPptLimits;
import yueyang.vostok.office.ppt.internal.VKPptPackageReader;
import yueyang.vostok.office.ppt.internal.VKPptPackageWriter;
import yueyang.vostok.office.ppt.internal.VKPptSecurityGuard;
import yueyang.vostok.office.ppt.stream.VKPptStreamBlock;
import yueyang.vostok.office.ppt.stream.VKPptStreamBlockType;
import yueyang.vostok.office.ppt.stream.VKPptStreamOptions;
import yueyang.vostok.office.ppt.structured.VKPptStructuredDocument;
import yueyang.vostok.office.ppt.structured.VKPptStructuredNode;
import yueyang.vostok.office.ppt.structured.VKPptStructuredNodeType;
import yueyang.vostok.office.ppt.structured.VKPptStructuredOptions;
import yueyang.vostok.office.template.VKOfficeTemplateData;
import yueyang.vostok.office.template.VKOfficeTemplateOptions;
import yueyang.vostok.office.template.internal.VKOfficeTemplateEngine;
import yueyang.vostok.office.word.VKWordDocument;
import yueyang.vostok.office.word.VKWordImage;
import yueyang.vostok.office.word.VKWordReadOptions;
import yueyang.vostok.office.word.VKWordWriteOptions;
import yueyang.vostok.office.word.VKWordWriteRequest;
import yueyang.vostok.office.word.internal.VKWordLimits;
import yueyang.vostok.office.word.internal.VKWordPackageReader;
import yueyang.vostok.office.word.internal.VKWordPackageWriter;
import yueyang.vostok.office.word.internal.VKWordSecurityGuard;
import yueyang.vostok.office.word.stream.VKWordStreamBlock;
import yueyang.vostok.office.word.stream.VKWordStreamBlockType;
import yueyang.vostok.office.word.stream.VKWordStreamOptions;
import yueyang.vostok.office.word.structured.VKWordStructuredDocument;
import yueyang.vostok.office.word.structured.VKWordStructuredNode;
import yueyang.vostok.office.word.structured.VKWordStructuredNodeType;
import yueyang.vostok.office.word.structured.VKWordStructuredOptions;

import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * Office 模块门面。
 *
 * <p>当前提供 Excel / Word / PPT / PDF（.xlsx / .docx / .pptx / .pdf）读写能力。</p>
 */
public class VostokOffice {
    private static final Object LOCK = new Object();
    private static volatile boolean initialized;
    private static volatile VKOfficeConfig config;
    private static volatile VKOfficeJobRuntime jobRuntime;

    protected VostokOffice() {
    }

    /** 使用默认配置初始化。 */
    public static void init() {
        init(new VKOfficeConfig());
    }

    /** 显式初始化 Office 模块。 */
    public static void init(VKOfficeConfig officeConfig) {
        synchronized (LOCK) {
            if (jobRuntime != null) {
                jobRuntime.close();
                jobRuntime = null;
            }
            VKOfficeConfig cfg = officeConfig == null ? new VKOfficeConfig() : officeConfig;
            validateConfig(cfg);
            config = copyConfig(cfg);
            jobRuntime = new VKOfficeJobRuntime();
            jobRuntime.init(config);
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
            if (jobRuntime != null) {
                jobRuntime.close();
                jobRuntime = null;
            }
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

    /** 读取 PPT 全文文本。 */
    public static String readPPTText(String path) {
        return readPPTText(path, VKPptReadOptions.defaults());
    }

    /** 读取 PPT 全文文本。 */
    public static String readPPTText(String path, VKPptReadOptions options) {
        return withPPTReader(path, options, VKPptPackageReader::readText);
    }

    /** 读取 PPT 全部图片。 */
    public static List<VKPptImage> readPPTImages(String path) {
        return readPPTImages(path, VKPptReadOptions.defaults());
    }

    /** 读取 PPT 全部图片。 */
    public static List<VKPptImage> readPPTImages(String path, VKPptReadOptions options) {
        return withPPTReader(path, options, VKPptPackageReader::readImages);
    }

    /** 获取 PPT 字数（按非空白 Unicode code point 统计）。 */
    public static int countPPTChars(String path) {
        return countPPTChars(path, VKPptReadOptions.defaults());
    }

    /** 获取 PPT 字数（仅计数路径）。 */
    public static int countPPTChars(String path, VKPptReadOptions options) {
        return withPPTReader(path, options, VKPptPackageReader::countChars);
    }

    /** 获取 PPT 图片数量（按出现次数统计）。 */
    public static int countPPTImages(String path) {
        return countPPTImages(path, VKPptReadOptions.defaults());
    }

    /** 获取 PPT 图片数量（按出现次数统计）。 */
    public static int countPPTImages(String path, VKPptReadOptions options) {
        return withPPTReader(path, options, VKPptPackageReader::countImages);
    }

    /** 获取 PPT 幻灯片数。 */
    public static int countPPTSlides(String path) {
        return countPPTSlides(path, VKPptReadOptions.defaults());
    }

    /** 获取 PPT 幻灯片数。 */
    public static int countPPTSlides(String path, VKPptReadOptions options) {
        return withPPTReader(path, options, VKPptPackageReader::countSlides);
    }

    /** 一次解包聚合读取 PPT（文本 + 字数 + 图片 + 幻灯片数）。 */
    public static VKPptDocument readPPT(String path) {
        return readPPT(path, VKPptReadOptions.defaults());
    }

    /** 一次解包聚合读取 PPT（文本 + 字数 + 图片 + 幻灯片数）。 */
    public static VKPptDocument readPPT(String path, VKPptReadOptions options) {
        return withPPTReader(path, options, VKPptPackageReader::readDocument);
    }

    /** 写入 PPT（文本 + 图片）。 */
    public static void writePPT(String path, VKPptWriteRequest request) {
        writePPT(path, request, VKPptWriteOptions.defaults());
    }

    /** 写入 PPT（文本 + 图片）。 */
    public static void writePPT(String path, VKPptWriteRequest request, VKPptWriteOptions options) {
        ensureInitialized();
        ensureLocalFileMode();
        if (request == null) {
            throw new VKOfficeException(VKOfficeErrorCode.INVALID_ARGUMENT, "PPT write request is null");
        }

        VKPptSecurityGuard.assertSafePath(path);
        requirePptx(path);

        VKPptLimits limits = VKPptLimits.fromWrite(config, options);
        String tempDir = createTempDir(limits.tempSubDir(), "pptx_");
        try {
            Path packageRoot = readLocalPath(tempDir);
            Path baseDir = Path.of(Vostok.File.config().getBaseDir()).toAbsolutePath().normalize();
            VKPptPackageWriter writer = new VKPptPackageWriter(packageRoot, baseDir, limits);
            writer.writePackage(request);
            Vostok.File.zip(tempDir, path, false);
            ensureFileBytesLimit(path, limits.maxDocumentBytes(), "PPT document");
        } finally {
            cleanupTempDir(tempDir);
        }
    }

    /** 读取 PDF 全文文本。 */
    public static String readPDFText(String path) {
        return readPDFText(path, VKPdfReadOptions.defaults());
    }

    /** 读取 PDF 全文文本。 */
    public static String readPDFText(String path, VKPdfReadOptions options) {
        return withPDFReader(path, options, VKPdfDocumentReader::readText);
    }

    /** 读取 PDF 全部图片。 */
    public static List<VKPdfImage> readPDFImages(String path) {
        return readPDFImages(path, VKPdfReadOptions.defaults());
    }

    /** 读取 PDF 全部图片。 */
    public static List<VKPdfImage> readPDFImages(String path, VKPdfReadOptions options) {
        return withPDFReader(path, options, VKPdfDocumentReader::readImages);
    }

    /** 获取 PDF 字数（按非空白 Unicode code point 统计）。 */
    public static int countPDFChars(String path) {
        return countPDFChars(path, VKPdfReadOptions.defaults());
    }

    /** 获取 PDF 字数（仅计数路径）。 */
    public static int countPDFChars(String path, VKPdfReadOptions options) {
        return withPDFReader(path, options, VKPdfDocumentReader::countChars);
    }

    /** 获取 PDF 图片数量（按出现次数统计）。 */
    public static int countPDFImages(String path) {
        return countPDFImages(path, VKPdfReadOptions.defaults());
    }

    /** 获取 PDF 图片数量（按出现次数统计）。 */
    public static int countPDFImages(String path, VKPdfReadOptions options) {
        return withPDFReader(path, options, VKPdfDocumentReader::countImages);
    }

    /** 获取 PDF 页数。 */
    public static int countPDFPages(String path) {
        return countPDFPages(path, VKPdfReadOptions.defaults());
    }

    /** 获取 PDF 页数。 */
    public static int countPDFPages(String path, VKPdfReadOptions options) {
        return withPDFReader(path, options, VKPdfDocumentReader::countPages);
    }

    /** 聚合读取 PDF（文本 + 字数 + 图片 + 页数）。 */
    public static VKPdfDocument readPDF(String path) {
        return readPDF(path, VKPdfReadOptions.defaults());
    }

    /** 聚合读取 PDF（文本 + 字数 + 图片 + 页数）。 */
    public static VKPdfDocument readPDF(String path, VKPdfReadOptions options) {
        return withPDFReader(path, options, VKPdfDocumentReader::readDocument);
    }

    /** 写入 PDF（文本 + 图片）。 */
    public static void writePDF(String path, VKPdfWriteRequest request) {
        writePDF(path, request, VKPdfWriteOptions.defaults());
    }

    /** 写入 PDF（文本 + 图片）。 */
    public static void writePDF(String path, VKPdfWriteRequest request, VKPdfWriteOptions options) {
        ensureInitialized();
        ensureLocalFileMode();
        if (request == null) {
            throw new VKOfficeException(VKOfficeErrorCode.INVALID_ARGUMENT, "PDF write request is null");
        }

        VKPdfSecurityGuard.assertSafePath(path);
        requirePdf(path);

        VKPdfLimits limits = VKPdfLimits.fromWrite(config, options);
        Path baseDir = Path.of(Vostok.File.config().getBaseDir()).toAbsolutePath().normalize();
        VKPdfWriter writer = new VKPdfWriter(baseDir, limits);
        byte[] bytes = writer.write(request);
        Vostok.File.writeBytes(path, bytes);
        ensureFileBytesLimit(path, limits.maxDocumentBytes(), "PDF document");
    }

    // ---------------------------------------------------------------- 模板能力

    /** 使用数据渲染 Excel 模板并输出到目标文档。 */
    public static void renderExcelTemplate(String templatePath, String outputPath, VKOfficeTemplateData data) {
        renderExcelTemplate(templatePath, outputPath, data == null ? Map.of() : data.values(), VKExcelTemplateOptions.defaults());
    }

    /** 使用数据渲染 Excel 模板并输出到目标文档。 */
    public static void renderExcelTemplate(String templatePath,
                                           String outputPath,
                                           Map<String, Object> data,
                                           VKExcelTemplateOptions options) {
        ensureInitialized();
        ensureLocalFileMode();

        VKExcelTemplateOptions opt = options == null ? VKExcelTemplateOptions.defaults() : options;
        VKExcelTemplateSecurityGuard.assertTemplatePath(templatePath);
        VKExcelTemplateSecurityGuard.assertOutputPath(outputPath);

        VKExcelReadOptions readOptions = VKExcelReadOptions.defaults();
        if (opt.tempSubDir() != null && !opt.tempSubDir().isBlank()) {
            readOptions.tempSubDir(opt.tempSubDir());
        }
        VKExcelWorkbook templateWorkbook = readExcel(templatePath, readOptions);
        VKExcelWorkbook renderedWorkbook = new VKExcelTemplateRenderer().renderWorkbook(
                templateWorkbook,
                data == null ? Map.of() : data,
                opt
        );

        VKExcelWriteOptions writeOptions = VKExcelWriteOptions.defaults();
        if (opt.tempSubDir() != null && !opt.tempSubDir().isBlank()) {
            writeOptions.tempSubDir(opt.tempSubDir());
        }
        if (opt.maxOutputBytes() > 0) {
            writeOptions.maxWorkbookBytes(opt.maxOutputBytes());
        }
        writeExcel(outputPath, renderedWorkbook, writeOptions);
    }

    /** 使用数据渲染 Word 模板并输出到目标文档。 */
    public static void renderWordTemplate(String templatePath, String outputPath, VKOfficeTemplateData data) {
        renderWordTemplate(templatePath, outputPath, data == null ? Map.of() : data.values(), VKOfficeTemplateOptions.defaults());
    }

    /** 使用数据渲染 Word 模板并输出到目标文档。 */
    public static void renderWordTemplate(String templatePath,
                                          String outputPath,
                                          Map<String, Object> data,
                                          VKOfficeTemplateOptions options) {
        String templateText = readWordText(templatePath);
        String rendered = VKOfficeTemplateEngine.render(templateText, data, options);
        writeWord(outputPath, new VKWordWriteRequest().addParagraph(rendered));
    }

    /** 使用数据渲染 PPT 模板并输出到目标文档。 */
    public static void renderPPTTemplate(String templatePath, String outputPath, VKOfficeTemplateData data) {
        renderPPTTemplate(templatePath, outputPath, data == null ? Map.of() : data.values(), VKOfficeTemplateOptions.defaults());
    }

    /** 使用数据渲染 PPT 模板并输出到目标文档。 */
    public static void renderPPTTemplate(String templatePath,
                                         String outputPath,
                                         Map<String, Object> data,
                                         VKOfficeTemplateOptions options) {
        String templateText = readPPTText(templatePath);
        String rendered = VKOfficeTemplateEngine.render(templateText, data, options);
        VKPptWriteRequest req = new VKPptWriteRequest();
        req.addSlide().addParagraph(rendered);
        writePPT(outputPath, req);
    }

    /** 使用数据渲染 PDF 模板并输出到目标文档。 */
    public static void renderPDFTemplate(String templatePath, String outputPath, VKOfficeTemplateData data) {
        renderPDFTemplate(templatePath, outputPath, data == null ? Map.of() : data.values(), VKOfficeTemplateOptions.defaults());
    }

    /** 使用数据渲染 PDF 模板并输出到目标文档。 */
    public static void renderPDFTemplate(String templatePath,
                                         String outputPath,
                                         Map<String, Object> data,
                                         VKOfficeTemplateOptions options) {
        String templateText = readPDFText(templatePath);
        String rendered = VKOfficeTemplateEngine.render(templateText, data, options);
        VKPdfWriteRequest req = new VKPdfWriteRequest();
        req.addPage().addParagraph(rendered);
        writePDF(outputPath, req);
    }

    // ---------------------------------------------------------------- 转换能力

    /** Office 转 PDF（支持 docx/pptx/xlsx -> pdf）。 */
    public static void convertToPDF(String sourcePath, String targetPdfPath) {
        convertToPDF(sourcePath, targetPdfPath, VKOfficeConvertOptions.defaults());
    }

    /** Office 转 PDF（支持 docx/pptx/xlsx -> pdf）。 */
    public static void convertToPDF(String sourcePath, String targetPdfPath, VKOfficeConvertOptions options) {
        VKOfficeConvertOptions opt = options == null ? VKOfficeConvertOptions.defaults() : options;
        String src = sourcePath == null ? "" : sourcePath.toLowerCase();
        VKPdfWriteRequest request = new VKPdfWriteRequest();
        if (src.endsWith(".docx")) {
            String text = readWordText(sourcePath);
            splitTextToPdfPages(request, text, opt.maxTextCharsPerPage());
            if (opt.includeImages()) {
                request = appendWordImagesToPdf(request, readWordImages(sourcePath));
            }
            writePDF(targetPdfPath, request);
            return;
        }
        if (src.endsWith(".pptx")) {
            String text = readPPTText(sourcePath);
            splitTextToPdfPages(request, text, opt.maxTextCharsPerPage());
            if (opt.includeImages()) {
                request = appendPptImagesToPdf(request, readPPTImages(sourcePath));
            }
            writePDF(targetPdfPath, request);
            return;
        }
        if (src.endsWith(".xlsx")) {
            String text = excelToText(readExcel(sourcePath));
            splitTextToPdfPages(request, text, opt.maxTextCharsPerPage());
            writePDF(targetPdfPath, request);
            return;
        }
        throw new VKOfficeException(VKOfficeErrorCode.UNSUPPORTED_FORMAT,
                "Only docx/pptx/xlsx can convert to pdf: " + sourcePath);
    }

    /** xlsx -> csv。 */
    public static void convertExcelToCSV(String excelPath, String csvPath) {
        convertExcelToCSV(excelPath, csvPath, VKOfficeConvertOptions.defaults());
    }

    /** xlsx -> csv。 */
    public static void convertExcelToCSV(String excelPath, String csvPath, VKOfficeConvertOptions options) {
        VKOfficeConvertOptions opt = options == null ? VKOfficeConvertOptions.defaults() : options;
        VKExcelWorkbook workbook = readExcel(excelPath);
        VKExcelSheet sheet = pickSheet(workbook, opt.csvSheetName());
        if (sheet == null) {
            throw new VKOfficeException(VKOfficeErrorCode.NOT_FOUND, "Excel sheet not found");
        }
        String delimiter = safeDelimiter(opt.csvDelimiter());
        String lineSep = opt.csvLineSeparator() == null ? "\n" : opt.csvLineSeparator();
        Vostok.File.write(csvPath, "");

        List<Integer> rowIndexes = new ArrayList<>(sheet.rows().keySet());
        rowIndexes.sort(Comparator.naturalOrder());
        int maxCol = sheet.maxColumnIndex();
        for (Integer rowIndex : rowIndexes) {
            StringBuilder line = new StringBuilder();
            for (int c = 1; c <= maxCol; c++) {
                if (c > 1) {
                    line.append(delimiter);
                }
                var cell = sheet.getCell(rowIndex, c);
                String value = cell == null ? "" : cellToText(cell.type().name(), cell.value(), cell.formula());
                line.append(escapeCsv(value, delimiter));
            }
            Vostok.File.append(csvPath, line + lineSep);
        }
    }

    /** csv -> xlsx。 */
    public static void convertCSVToExcel(String csvPath, String excelPath) {
        convertCSVToExcel(csvPath, excelPath, VKOfficeConvertOptions.defaults());
    }

    /** csv -> xlsx。 */
    public static void convertCSVToExcel(String csvPath, String excelPath, VKOfficeConvertOptions options) {
        VKOfficeConvertOptions opt = options == null ? VKOfficeConvertOptions.defaults() : options;
        String delimiter = safeDelimiter(opt.csvDelimiter());
        String charsetName = opt.csvCharset() == null || opt.csvCharset().isBlank() ? "UTF-8" : opt.csvCharset();
        Charset charset = Charset.forName(charsetName);
        List<String> lines = Vostok.File.readLines(csvPath);
        VKExcelSheet sheet = new VKExcelSheet(opt.csvSheetName() == null || opt.csvSheetName().isBlank()
                ? "Sheet1" : opt.csvSheetName());
        int row = 1;
        for (String line : lines) {
            List<String> fields = parseCsvLine(new String(line.getBytes(charset), charset), delimiter);
            for (int c = 0; c < fields.size(); c++) {
                sheet.setCell(row, c + 1, yueyang.vostok.office.excel.VKExcelCellType.STRING, fields.get(c));
            }
            row++;
        }
        writeExcel(excelPath, new VKExcelWorkbook().addSheet(sheet));
    }

    // ---------------------------------------------------------------- 流式读取

    /** Word 流式读取。 */
    public static void readWordStream(String path, Consumer<VKWordStreamBlock> consumer) {
        readWordStream(path, VKWordStreamOptions.defaults(), consumer);
    }

    /** Word 流式读取。 */
    public static void readWordStream(String path, VKWordStreamOptions options, Consumer<VKWordStreamBlock> consumer) {
        if (consumer == null) {
            throw new VKOfficeException(VKOfficeErrorCode.INVALID_ARGUMENT, "Word stream consumer is null");
        }
        VKWordStreamOptions opt = options == null ? VKWordStreamOptions.defaults() : options;
        if (opt.includeMeta()) {
            VKWordDocument doc = readWord(path);
            consumer.accept(new VKWordStreamBlock(VKWordStreamBlockType.META, 1, 0, "word/document.xml",
                    "chars=" + doc.charCount() + ", images=" + doc.imageCount(), null));
        }
        if (opt.includeText()) {
            String text = readWordText(path);
            String[] lines = text.replace("\r\n", "\n").replace('\r', '\n').split("\\n", -1);
            int idx = 0;
            for (String line : lines) {
                idx++;
                consumer.accept(new VKWordStreamBlock(VKWordStreamBlockType.TEXT, 1, idx, "word/document.xml", line, null));
            }
        }
        if (opt.includeImages()) {
            List<VKWordImage> images = readWordImages(path);
            for (int i = 0; i < images.size(); i++) {
                VKWordImage image = images.get(i);
                consumer.accept(new VKWordStreamBlock(VKWordStreamBlockType.IMAGE, 1, i + 1, image.partName(), null, image));
            }
        }
    }

    /** PPT 流式读取。 */
    public static void readPPTStream(String path, Consumer<VKPptStreamBlock> consumer) {
        readPPTStream(path, VKPptStreamOptions.defaults(), consumer);
    }

    /** PPT 流式读取。 */
    public static void readPPTStream(String path, VKPptStreamOptions options, Consumer<VKPptStreamBlock> consumer) {
        if (consumer == null) {
            throw new VKOfficeException(VKOfficeErrorCode.INVALID_ARGUMENT, "PPT stream consumer is null");
        }
        VKPptStreamOptions opt = options == null ? VKPptStreamOptions.defaults() : options;
        if (opt.includeMeta()) {
            VKPptDocument doc = readPPT(path);
            consumer.accept(new VKPptStreamBlock(VKPptStreamBlockType.META, 0, 0, "ppt/presentation.xml",
                    "chars=" + doc.charCount() + ", slides=" + doc.slideCount() + ", images=" + doc.imageCount(), null));
        }
        if (opt.includeText()) {
            String text = readPPTText(path);
            String[] lines = text.replace("\r\n", "\n").replace('\r', '\n').split("\\n", -1);
            int idx = 0;
            for (String line : lines) {
                idx++;
                consumer.accept(new VKPptStreamBlock(VKPptStreamBlockType.TEXT, 1, idx, "ppt/slides/slide1.xml", line, null));
            }
        }
        if (opt.includeImages()) {
            List<VKPptImage> images = readPPTImages(path);
            for (int i = 0; i < images.size(); i++) {
                VKPptImage image = images.get(i);
                consumer.accept(new VKPptStreamBlock(VKPptStreamBlockType.IMAGE, image.slideIndex(), i + 1, image.partName(), null, image));
            }
        }
    }

    /** PDF 流式读取。 */
    public static void readPDFStream(String path, Consumer<VKPdfStreamBlock> consumer) {
        readPDFStream(path, VKPdfStreamOptions.defaults(), consumer);
    }

    /** PDF 流式读取。 */
    public static void readPDFStream(String path, VKPdfStreamOptions options, Consumer<VKPdfStreamBlock> consumer) {
        if (consumer == null) {
            throw new VKOfficeException(VKOfficeErrorCode.INVALID_ARGUMENT, "PDF stream consumer is null");
        }
        VKPdfStreamOptions opt = options == null ? VKPdfStreamOptions.defaults() : options;
        if (opt.includeMeta()) {
            VKPdfDocument doc = readPDF(path);
            consumer.accept(new VKPdfStreamBlock(VKPdfStreamBlockType.META, 0, 0, null,
                    "chars=" + doc.charCount() + ", pages=" + doc.pageCount() + ", images=" + doc.imageCount(), null));
        }
        if (opt.includeText()) {
            String text = readPDFText(path);
            String[] lines = text.replace("\r\n", "\n").replace('\r', '\n').split("\\n", -1);
            int idx = 0;
            for (String line : lines) {
                idx++;
                consumer.accept(new VKPdfStreamBlock(VKPdfStreamBlockType.TEXT, 1, idx, null, line, null));
            }
        }
        if (opt.includeImages()) {
            List<VKPdfImage> images = readPDFImages(path);
            for (int i = 0; i < images.size(); i++) {
                VKPdfImage image = images.get(i);
                consumer.accept(new VKPdfStreamBlock(VKPdfStreamBlockType.IMAGE, image.pageIndex(), i + 1, image.objectRef(), null, image));
            }
        }
    }

    // ---------------------------------------------------------------- 结构化提取

    /** Word 结构化提取。 */
    public static VKWordStructuredDocument readWordStructured(String path) {
        return readWordStructured(path, VKWordStructuredOptions.defaults());
    }

    /** Word 结构化提取。 */
    public static VKWordStructuredDocument readWordStructured(String path, VKWordStructuredOptions options) {
        VKWordStructuredOptions opt = options == null ? VKWordStructuredOptions.defaults() : options;
        VKWordStructuredDocument doc = new VKWordStructuredDocument();
        VKWordStructuredNode section = new VKWordStructuredNode(VKWordStructuredNodeType.SECTION, 1, 0, "word/document.xml", null, null);
        if (opt.includeText()) {
            String[] lines = readWordText(path).replace("\r\n", "\n").replace('\r', '\n').split("\\n", -1);
            for (int i = 0; i < lines.length; i++) {
                section.addChild(new VKWordStructuredNode(
                        VKWordStructuredNodeType.PARAGRAPH, 1, i + 1, "word/document.xml", lines[i], null));
            }
        }
        if (opt.includeImages()) {
            for (VKWordImage image : readWordImages(path)) {
                section.addChild(new VKWordStructuredNode(
                        VKWordStructuredNodeType.IMAGE, 1, image.index(), image.partName(), null, image));
            }
        }
        doc.addNode(section);
        return doc;
    }

    /** PPT 结构化提取。 */
    public static VKPptStructuredDocument readPPTStructured(String path) {
        return readPPTStructured(path, VKPptStructuredOptions.defaults());
    }

    /** PPT 结构化提取。 */
    public static VKPptStructuredDocument readPPTStructured(String path, VKPptStructuredOptions options) {
        VKPptStructuredOptions opt = options == null ? VKPptStructuredOptions.defaults() : options;
        VKPptStructuredDocument doc = new VKPptStructuredDocument();
        int slides = countPPTSlides(path);
        for (int i = 1; i <= slides; i++) {
            doc.addNode(new VKPptStructuredNode(VKPptStructuredNodeType.SLIDE, i, "ppt/slides/slide" + i + ".xml", null, null));
        }
        if (opt.includeText()) {
            String[] lines = readPPTText(path).replace("\r\n", "\n").replace('\r', '\n').split("\\n", -1);
            VKPptStructuredNode slide1 = doc.nodes().isEmpty()
                    ? new VKPptStructuredNode(VKPptStructuredNodeType.SLIDE, 1, "ppt/slides/slide1.xml", null, null)
                    : doc.nodes().get(0);
            for (String line : lines) {
                slide1.addChild(new VKPptStructuredNode(VKPptStructuredNodeType.TEXT_SHAPE, 1, "ppt/slides/slide1.xml", line, null));
            }
            if (doc.nodes().isEmpty()) {
                doc.addNode(slide1);
            }
        }
        if (opt.includeImages()) {
            Map<Integer, VKPptStructuredNode> slideMap = new LinkedHashMap<>();
            for (VKPptStructuredNode node : doc.nodes()) {
                slideMap.put(node.slideIndex(), node);
            }
            for (VKPptImage image : readPPTImages(path)) {
                VKPptStructuredNode slide = slideMap.computeIfAbsent(image.slideIndex(),
                        k -> new VKPptStructuredNode(VKPptStructuredNodeType.SLIDE, k, "ppt/slides/slide" + k + ".xml", null, null));
                slide.addChild(new VKPptStructuredNode(VKPptStructuredNodeType.IMAGE, image.slideIndex(), image.partName(), null, image));
                if (!doc.nodes().contains(slide)) {
                    doc.addNode(slide);
                }
            }
        }
        return doc;
    }

    /** PDF 结构化提取。 */
    public static VKPdfStructuredDocument readPDFStructured(String path) {
        return readPDFStructured(path, VKPdfStructuredOptions.defaults());
    }

    /** PDF 结构化提取。 */
    public static VKPdfStructuredDocument readPDFStructured(String path, VKPdfStructuredOptions options) {
        VKPdfStructuredOptions opt = options == null ? VKPdfStructuredOptions.defaults() : options;
        VKPdfStructuredDocument doc = new VKPdfStructuredDocument();
        int pages = countPDFPages(path);
        for (int i = 1; i <= pages; i++) {
            doc.addNode(new VKPdfStructuredNode(VKPdfStructuredNodeType.PAGE, i, null, null, null));
        }
        if (opt.includeText()) {
            String text = readPDFText(path);
            VKPdfStructuredNode firstPage = doc.nodes().isEmpty()
                    ? new VKPdfStructuredNode(VKPdfStructuredNodeType.PAGE, 1, null, null, null)
                    : doc.nodes().get(0);
            firstPage.addChild(new VKPdfStructuredNode(VKPdfStructuredNodeType.TEXT_BLOCK, firstPage.pageIndex(), null, text, null));
            if (doc.nodes().isEmpty()) {
                doc.addNode(firstPage);
            }
        }
        if (opt.includeImages()) {
            Map<Integer, VKPdfStructuredNode> pageMap = new LinkedHashMap<>();
            for (VKPdfStructuredNode page : doc.nodes()) {
                pageMap.put(page.pageIndex(), page);
            }
            for (VKPdfImage image : readPDFImages(path)) {
                VKPdfStructuredNode page = pageMap.computeIfAbsent(image.pageIndex(),
                        p -> new VKPdfStructuredNode(VKPdfStructuredNodeType.PAGE, p, null, null, null));
                page.addChild(new VKPdfStructuredNode(VKPdfStructuredNodeType.IMAGE, image.pageIndex(), image.objectRef(), null, image));
                if (!doc.nodes().contains(page)) {
                    doc.addNode(page);
                }
            }
        }
        return doc;
    }

    // ---------------------------------------------------------------- 异步任务 + 回调

    /** 提交异步任务。 */
    public static String submitJob(VKOfficeJobRequest request) {
        return ensureJobRuntime().submit(request);
    }

    /** 查询任务。 */
    public static VKOfficeJobInfo getJob(String jobId) {
        return ensureJobRuntime().get(jobId);
    }

    /** 列表查询任务。 */
    public static List<VKOfficeJobInfo> listJobs(VKOfficeJobQuery query) {
        return ensureJobRuntime().list(query);
    }

    /** 取消任务。 */
    public static boolean cancelJob(String jobId) {
        return ensureJobRuntime().cancel(jobId);
    }

    /** 等待任务完成（默认 30s）。 */
    public static VKOfficeJobResult awaitJob(String jobId) {
        return ensureJobRuntime().await(jobId, 30_000L);
    }

    /** 等待任务完成（自定义超时）。 */
    public static VKOfficeJobResult awaitJob(String jobId, long timeoutMs) {
        return ensureJobRuntime().await(jobId, timeoutMs);
    }

    /** 注册任务通知监听。 */
    public static VKOfficeJobSubscription onJob(VKOfficeJobListener listener) {
        return ensureJobRuntime().onJob(null, null, listener, false);
    }

    /** 注册指定状态监听。 */
    public static VKOfficeJobSubscription onJob(VKOfficeJobStatus status, VKOfficeJobListener listener) {
        return ensureJobRuntime().onJob(status, null, listener, false);
    }

    /** 注册指定状态 + 过滤器监听。 */
    public static VKOfficeJobSubscription onJob(VKOfficeJobStatus status,
                                                VKOfficeJobFilter filter,
                                                VKOfficeJobListener listener) {
        return ensureJobRuntime().onJob(status, filter, listener, false);
    }

    /** 注册一次性监听。 */
    public static VKOfficeJobSubscription onceJob(VKOfficeJobStatus status, VKOfficeJobListener listener) {
        return ensureJobRuntime().onJob(status, null, listener, true);
    }

    /** 注册完成态监听。 */
    public static VKOfficeJobSubscription onJobCompleted(VKOfficeJobListener listener) {
        return onJob(VKOfficeJobStatus.SUCCEEDED, listener);
    }

    /** 注册失败态监听。 */
    public static VKOfficeJobSubscription onJobFailed(VKOfficeJobListener listener) {
        return onJob(VKOfficeJobStatus.FAILED, listener);
    }

    /** 注册取消态监听。 */
    public static VKOfficeJobSubscription onJobCancelled(VKOfficeJobListener listener) {
        return onJob(VKOfficeJobStatus.CANCELLED, listener);
    }

    /** 注销单个订阅。 */
    public static void offJob(VKOfficeJobSubscription subscription) {
        ensureJobRuntime().offJob(subscription);
    }

    /** 清空所有订阅。 */
    public static void offAllJobs() {
        ensureJobRuntime().offAllJobs();
    }

    /** 设置 dead-letter 处理器。 */
    public static void onJobDeadLetter(VKOfficeJobDeadLetterHandler handler) {
        ensureJobRuntime().onJobDeadLetter(handler);
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

    /**
     * PPT 统一读取模板：
     * 负责安全校验、解包与清理，业务逻辑由 action 操作 VKPptPackageReader。
     */
    private static <T> T withPPTReader(String path, VKPptReadOptions options, Function<VKPptPackageReader, T> action) {
        ensureInitialized();
        ensureLocalFileMode();
        if (action == null) {
            throw new VKOfficeException(VKOfficeErrorCode.INVALID_ARGUMENT, "PPT reader action is null");
        }

        VKPptSecurityGuard.assertSafePath(path);
        requirePptx(path);

        VKPptLimits limits = VKPptLimits.fromRead(config, options);
        ensureFileBytesLimit(path, limits.maxDocumentBytes(), "PPT document");
        VKPptSecurityGuard.assertZipMagic(readLocalPath(path));

        String tempDir = createTempDir(limits.tempSubDir(), "pptx_");
        try {
            Vostok.File.unzip(path, tempDir, buildUnzipOptions(config));
            Path packageRoot = locatePackageRoot(readLocalPath(tempDir));
            VKPptPackageReader reader = new VKPptPackageReader(packageRoot, limits);
            return action.apply(reader);
        } finally {
            cleanupTempDir(tempDir);
        }
    }

    /**
     * PDF 统一读取模板：
     * 负责安全校验并创建解析器，业务逻辑由 action 操作 VKPdfDocumentReader。
     */
    private static <T> T withPDFReader(String path, VKPdfReadOptions options, Function<VKPdfDocumentReader, T> action) {
        ensureInitialized();
        ensureLocalFileMode();
        if (action == null) {
            throw new VKOfficeException(VKOfficeErrorCode.INVALID_ARGUMENT, "PDF reader action is null");
        }

        VKPdfSecurityGuard.assertSafePath(path);
        requirePdf(path);

        VKPdfLimits limits = VKPdfLimits.fromRead(config, options);
        ensureFileBytesLimit(path, limits.maxDocumentBytes(), "PDF document");

        Path source = readLocalPath(path);
        VKPdfSecurityGuard.assertPdfMagic(source);

        byte[] bytes;
        try {
            bytes = Files.readAllBytes(source);
        } catch (IOException e) {
            throw new VKOfficeException(VKOfficeErrorCode.IO_ERROR,
                    "Read PDF bytes failed: " + path, e);
        }
        VKPdfDocumentReader reader = new VKPdfDocumentReader(bytes, limits);
        return action.apply(reader);
    }

    /** 保障任务运行时可用。 */
    private static VKOfficeJobRuntime ensureJobRuntime() {
        ensureInitialized();
        VKOfficeJobRuntime runtime = jobRuntime;
        if (runtime == null) {
            throw new VKOfficeException(VKOfficeErrorCode.STATE_ERROR, "Office job runtime is unavailable");
        }
        return runtime;
    }

    /** 按页阈值把文本分配到 PDF 页面。 */
    private static void splitTextToPdfPages(VKPdfWriteRequest request, String text, int maxCharsPerPage) {
        if (request == null) {
            return;
        }
        String src = text == null ? "" : text;
        int pageSize = maxCharsPerPage <= 0 ? 2000 : maxCharsPerPage;
        if (src.isEmpty()) {
            request.addPage().addParagraph("");
            return;
        }
        int start = 0;
        while (start < src.length()) {
            int end = Math.min(src.length(), start + pageSize);
            request.addPage().addParagraph(src.substring(start, end));
            start = end;
        }
    }

    private static VKPdfWriteRequest appendWordImagesToPdf(VKPdfWriteRequest request, List<VKWordImage> images) {
        if (images == null || images.isEmpty()) {
            return request;
        }
        VKPdfWriteRequest out = request == null ? new VKPdfWriteRequest() : request;
        for (VKWordImage image : images) {
            if (image == null || image.bytes() == null) {
                continue;
            }
            out.addPage().addImageBytes(resolveImageFileName(image.mediaPath(), image.contentType()), image.bytes());
        }
        return out;
    }

    private static VKPdfWriteRequest appendPptImagesToPdf(VKPdfWriteRequest request, List<VKPptImage> images) {
        if (images == null || images.isEmpty()) {
            return request;
        }
        VKPdfWriteRequest out = request == null ? new VKPdfWriteRequest() : request;
        for (VKPptImage image : images) {
            if (image == null || image.bytes() == null) {
                continue;
            }
            out.addPage().addImageBytes(resolveImageFileName(image.mediaPath(), image.contentType()), image.bytes());
        }
        return out;
    }

    private static String resolveImageFileName(String mediaPath, String contentType) {
        if (mediaPath != null && !mediaPath.isBlank()) {
            Path p = Path.of(mediaPath);
            Path fileName = p.getFileName();
            if (fileName != null) {
                return fileName.toString();
            }
        }
        if (contentType == null) {
            return "image.bin";
        }
        if (contentType.contains("png")) {
            return "image.png";
        }
        if (contentType.contains("jpeg") || contentType.contains("jpg")) {
            return "image.jpg";
        }
        return "image.bin";
    }

    private static String excelToText(VKExcelWorkbook workbook) {
        if (workbook == null || workbook.sheets().isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (VKExcelSheet sheet : workbook.sheets()) {
            sb.append("# ").append(sheet.name()).append('\n');
            List<Integer> rows = new ArrayList<>(sheet.rows().keySet());
            rows.sort(Comparator.naturalOrder());
            int maxCol = sheet.maxColumnIndex();
            for (Integer row : rows) {
                for (int c = 1; c <= maxCol; c++) {
                    if (c > 1) {
                        sb.append('\t');
                    }
                    var cell = sheet.getCell(row, c);
                    if (cell != null) {
                        sb.append(cellToText(cell.type().name(), cell.value(), cell.formula()));
                    }
                }
                sb.append('\n');
            }
            sb.append('\n');
        }
        return sb.toString();
    }

    private static VKExcelSheet pickSheet(VKExcelWorkbook workbook, String sheetName) {
        if (workbook == null || workbook.sheets().isEmpty()) {
            return null;
        }
        if (sheetName != null && !sheetName.isBlank()) {
            VKExcelSheet byName = workbook.getSheet(sheetName);
            if (byName != null) {
                return byName;
            }
        }
        return workbook.sheets().get(0);
    }

    private static String safeDelimiter(String delimiter) {
        if (delimiter == null || delimiter.isEmpty()) {
            return ",";
        }
        return delimiter;
    }

    private static String cellToText(String type, String value, String formula) {
        if ("FORMULA".equals(type)) {
            if (formula != null && !formula.isBlank()) {
                return "=" + formula;
            }
        }
        return value == null ? "" : value;
    }

    private static String escapeCsv(String value, String delimiter) {
        String v = value == null ? "" : value;
        boolean needQuote = v.contains("\"") || v.contains("\n") || v.contains("\r") || v.contains(delimiter);
        if (!needQuote) {
            return v;
        }
        return "\"" + v.replace("\"", "\"\"") + "\"";
    }

    /**
     * 简易 CSV 解析器。
     *
     * <p>支持双引号转义规则（"" -> "），满足 xlsx<->csv 首版转换。</p>
     */
    private static List<String> parseCsvLine(String line, String delimiter) {
        String src = line == null ? "" : line;
        String d = delimiter == null || delimiter.isEmpty() ? "," : delimiter;
        char dc = d.charAt(0);
        List<String> out = new ArrayList<>();
        StringBuilder cur = new StringBuilder();
        boolean quoted = false;
        for (int i = 0; i < src.length(); i++) {
            char c = src.charAt(i);
            if (c == '"') {
                if (quoted && i + 1 < src.length() && src.charAt(i + 1) == '"') {
                    cur.append('"');
                    i++;
                } else {
                    quoted = !quoted;
                }
                continue;
            }
            if (c == dc && !quoted) {
                out.add(cur.toString());
                cur.setLength(0);
                continue;
            }
            cur.append(c);
        }
        out.add(cur.toString());
        return out;
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

    private static void requirePptx(String path) {
        if (path == null || !path.toLowerCase().endsWith(".pptx")) {
            throw new VKOfficeException(VKOfficeErrorCode.UNSUPPORTED_FORMAT,
                    "Only .pptx is supported: " + path);
        }
    }

    private static void requirePdf(String path) {
        if (path == null || !path.toLowerCase().endsWith(".pdf")) {
            throw new VKOfficeException(VKOfficeErrorCode.UNSUPPORTED_FORMAT,
                    "Only .pdf is supported: " + path);
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
                .officeTempDir(source.getOfficeTempDir())
                .wordMaxDocumentBytes(source.getWordMaxDocumentBytes())
                .wordMaxTextChars(source.getWordMaxTextChars())
                .wordMaxImages(source.getWordMaxImages())
                .wordMaxSingleImageBytes(source.getWordMaxSingleImageBytes())
                .wordMaxTotalImageBytes(source.getWordMaxTotalImageBytes())
                .pptMaxDocumentBytes(source.getPptMaxDocumentBytes())
                .pptMaxSlides(source.getPptMaxSlides())
                .pptMaxTextChars(source.getPptMaxTextChars())
                .pptMaxImages(source.getPptMaxImages())
                .pptMaxSingleImageBytes(source.getPptMaxSingleImageBytes())
                .pptMaxTotalImageBytes(source.getPptMaxTotalImageBytes())
                .pdfMaxDocumentBytes(source.getPdfMaxDocumentBytes())
                .pdfMaxPages(source.getPdfMaxPages())
                .pdfMaxTextChars(source.getPdfMaxTextChars())
                .pdfMaxImages(source.getPdfMaxImages())
                .pdfMaxSingleImageBytes(source.getPdfMaxSingleImageBytes())
                .pdfMaxTotalImageBytes(source.getPdfMaxTotalImageBytes())
                .pdfMaxObjects(source.getPdfMaxObjects())
                .pdfMaxStreamBytes(source.getPdfMaxStreamBytes())
                .officeJobEnabled(source.getOfficeJobEnabled())
                .officeJobWorkerThreads(source.getOfficeJobWorkerThreads())
                .officeJobQueueCapacity(source.getOfficeJobQueueCapacity())
                .officeJobRetentionMs(source.getOfficeJobRetentionMs())
                .officeJobResultMaxBytes(source.getOfficeJobResultMaxBytes())
                .officeJobNotifyOnRunning(source.getOfficeJobNotifyOnRunning())
                .officeJobCallbackThreads(source.getOfficeJobCallbackThreads())
                .officeJobCallbackQueueCapacity(source.getOfficeJobCallbackQueueCapacity())
                .officeJobCallbackTimeoutMs(source.getOfficeJobCallbackTimeoutMs())
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
                || cfg.getPptMaxDocumentBytes() <= 0
                || cfg.getPptMaxSlides() <= 0
                || cfg.getPptMaxTextChars() <= 0
                || cfg.getPptMaxImages() <= 0
                || cfg.getPptMaxSingleImageBytes() <= 0
                || cfg.getPptMaxTotalImageBytes() <= 0
                || cfg.getPdfMaxDocumentBytes() <= 0
                || cfg.getPdfMaxPages() <= 0
                || cfg.getPdfMaxTextChars() <= 0
                || cfg.getPdfMaxImages() <= 0
                || cfg.getPdfMaxSingleImageBytes() <= 0
                || cfg.getPdfMaxTotalImageBytes() <= 0
                || cfg.getPdfMaxObjects() <= 0
                || cfg.getPdfMaxStreamBytes() <= 0
                || cfg.getOfficeJobWorkerThreads() <= 0
                || cfg.getOfficeJobQueueCapacity() <= 0
                || cfg.getOfficeJobRetentionMs() <= 0
                || cfg.getOfficeJobResultMaxBytes() <= 0
                || cfg.getOfficeJobCallbackThreads() <= 0
                || cfg.getOfficeJobCallbackQueueCapacity() <= 0
                || cfg.getOfficeJobCallbackTimeoutMs() <= 0
                || cfg.getXxeSampleBytes() <= 0) {
            throw new VKOfficeException(VKOfficeErrorCode.CONFIG_ERROR,
                    "Invalid office limits in VKOfficeConfig");
        }
        if (cfg.getOfficeTempDir() == null || cfg.getOfficeTempDir().trim().isEmpty()) {
            throw new VKOfficeException(VKOfficeErrorCode.CONFIG_ERROR,
                    "Office officeTempDir is blank");
        }
    }
}
