package yueyang.vostok.office.pdf.internal;

import yueyang.vostok.office.exception.VKOfficeErrorCode;
import yueyang.vostok.office.exception.VKOfficeException;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.Locale;
import java.util.zip.InflaterInputStream;

/** 解码 PDF stream（当前支持 FlateDecode / DCTDecode）。 */
public final class VKPdfStreamDecoder {
    private VKPdfStreamDecoder() {
    }

    public static byte[] decode(String dictionary, byte[] streamBytes, long maxStreamBytes) {
        if (streamBytes == null) {
            return null;
        }
        ensureLimit(streamBytes.length, maxStreamBytes, "PDF stream bytes");

        String dict = dictionary == null ? "" : dictionary.toLowerCase(Locale.ROOT);
        if (!dict.contains("/filter")) {
            return streamBytes;
        }

        boolean hasDct = dict.contains("/dctdecode");
        boolean hasFlate = dict.contains("/flatedecode");

        if (hasDct && !hasFlate) {
            // JPEG 数据保持原样。
            return streamBytes;
        }
        if (hasFlate && !hasDct) {
            return inflate(streamBytes, maxStreamBytes);
        }
        if (hasFlate && hasDct) {
            // 复杂组合过滤器本期不支持。
            throw new VKOfficeException(VKOfficeErrorCode.UNSUPPORTED_FORMAT,
                    "Unsupported PDF stream filter combination: FlateDecode + DCTDecode");
        }
        throw new VKOfficeException(VKOfficeErrorCode.UNSUPPORTED_FORMAT,
                "Unsupported PDF stream filter");
    }

    private static byte[] inflate(byte[] in, long maxStreamBytes) {
        try (ByteArrayInputStream bin = new ByteArrayInputStream(in);
             InflaterInputStream inflater = new InflaterInputStream(bin);
             ByteArrayOutputStream out = new ByteArrayOutputStream(Math.max(256, in.length * 2))) {
            byte[] buf = new byte[8192];
            int n;
            while ((n = inflater.read(buf)) != -1) {
                out.write(buf, 0, n);
                ensureLimit(out.size(), maxStreamBytes, "PDF decoded stream bytes");
            }
            return out.toByteArray();
        } catch (VKOfficeException e) {
            throw e;
        } catch (Exception e) {
            throw new VKOfficeException(VKOfficeErrorCode.PARSE_ERROR,
                    "Inflate PDF stream failed", e);
        }
    }

    private static void ensureLimit(long value, long limit, String name) {
        if (limit > 0 && value > limit) {
            throw new VKOfficeException(VKOfficeErrorCode.LIMIT_EXCEEDED,
                    name + " exceed limit: " + value + " > " + limit);
        }
    }
}
