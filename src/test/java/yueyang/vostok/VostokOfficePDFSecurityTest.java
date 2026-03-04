package yueyang.vostok;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import yueyang.vostok.file.VKFileConfig;
import yueyang.vostok.office.VKOfficeConfig;
import yueyang.vostok.office.exception.VKOfficeErrorCode;
import yueyang.vostok.office.exception.VKOfficeException;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class VostokOfficePDFSecurityTest {
    @TempDir
    Path tempDir;

    @AfterEach
    void tearDown() {
        Vostok.Office.close();
        Vostok.File.close();
    }

    private void init() {
        Vostok.File.init(new VKFileConfig().baseDir(tempDir.toString()));
        Vostok.Office.init(new VKOfficeConfig());
    }

    @Test
    void testRejectFakePDFByMagic() {
        init();
        Vostok.File.write("pdf/fake.pdf", "not-a-pdf");

        VKOfficeException ex = assertThrows(VKOfficeException.class,
                () -> Vostok.Office.readPDFText("pdf/fake.pdf"));
        assertEquals(VKOfficeErrorCode.SECURITY_ERROR, ex.getErrorCode());
    }

    @Test
    void testRejectUnsupportedImageFilter() {
        init();

        String pdf = "%PDF-1.4\n"
                + "1 0 obj\n<< /Type /Catalog /Pages 2 0 R >>\nendobj\n"
                + "2 0 obj\n<< /Type /Pages /Kids [3 0 R] /Count 1 >>\nendobj\n"
                + "3 0 obj\n<< /Type /Page /Parent 2 0 R /Resources << /XObject << /Im1 4 0 R >> >> /Contents 5 0 R /MediaBox [0 0 100 100] >>\nendobj\n"
                + "4 0 obj\n<< /Type /XObject /Subtype /Image /Width 1 /Height 1 /ColorSpace /DeviceRGB /BitsPerComponent 8 /Filter /LZWDecode /Length 3 >>\nstream\nabc\nendstream\nendobj\n"
                + "5 0 obj\n<< /Length 0 >>\nstream\n\nendstream\nendobj\n"
                + "xref\n0 6\n0000000000 65535 f \n0000000010 00000 n \n0000000060 00000 n \n0000000118 00000 n \n0000000250 00000 n \n0000000414 00000 n \n"
                + "trailer\n<< /Size 6 /Root 1 0 R >>\nstartxref\n470\n%%EOF\n";
        Vostok.File.writeBytes("pdf/filter.pdf", pdf.getBytes(StandardCharsets.ISO_8859_1));

        VKOfficeException ex = assertThrows(VKOfficeException.class,
                () -> Vostok.Office.readPDFImages("pdf/filter.pdf"));
        assertEquals(VKOfficeErrorCode.UNSUPPORTED_FORMAT, ex.getErrorCode());
    }
}
