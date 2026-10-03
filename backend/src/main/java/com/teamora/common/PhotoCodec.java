package com.teamora.common;

import com.teamora.common.exception.BadRequestException;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Set;

/**
 * Decodes photos sent inline in a JSON body (clock-in selfies, claim receipts).
 * Accepts bare base64 or a {@code data:image/...;base64,...} data URL, and
 * enforces the shared 2 MB decoded-size cap — checked on the encoded length
 * first, so an oversized upload is refused before anything is decoded.
 *
 * <p>Only JPEG, PNG and HEIC/HEIF are accepted, and the type is decided by the
 * file's own magic bytes (never by the declared data-URL type), so what is
 * stored and later served with that content-type really is that kind of image.
 * Invalid input → 400.
 */
public final class PhotoCodec {

    /** Cap on the decoded photo size (~2 MB) — clients downscale before sending. */
    public static final int MAX_BYTES = 2 * 1024 * 1024;
    /** Base64 length of {@link #MAX_BYTES} (4 chars per 3 bytes, padded). */
    static final int MAX_ENCODED_CHARS = ((MAX_BYTES + 2) / 3) * 4;
    public static final String DEFAULT_TYPE = "image/jpeg";

    public static final String JPEG = "image/jpeg";
    public static final String PNG = "image/png";
    public static final String HEIC = "image/heic";
    public static final String HEIF = "image/heif";
    public static final Set<String> ALLOWED_TYPES = Set.of(JPEG, PNG, HEIC, HEIF);

    private static final String TOO_LARGE = "Photo too large (max 2 MB). Please retake.";
    private static final String UNSUPPORTED = "Photo must be a JPEG, PNG or HEIC image.";

    /** ISO-BMFF brands used by HEIC (HEVC-coded) vs generic HEIF images. */
    private static final Set<String> HEIC_BRANDS = Set.of("heic", "heix", "hevc", "hevx", "heim", "heis", "hevm", "hevs");
    private static final Set<String> HEIF_BRANDS = Set.of("mif1", "msf1", "heif");

    private PhotoCodec() {}

    /** Decoded bytes + content-type. */
    public record Photo(byte[] bytes, String contentType) {}

    /** Bare base64 or a data URL → decoded bytes + their real content-type. */
    public static Photo decode(String raw) {
        String data = raw.trim();
        if (data.startsWith("data:")) {
            int comma = data.indexOf(',');
            if (comma < 0) {
                throw new BadRequestException("Invalid photo data URL");
            }
            String header = data.substring(5, comma); // e.g. "image/jpeg;base64"
            int semi = header.indexOf(';');
            String mime = (semi >= 0 ? header.substring(0, semi) : header).trim().toLowerCase();
            if (!mime.isEmpty() && !ALLOWED_TYPES.contains(mime) && !mime.equals("image/jpg")) {
                throw new BadRequestException(UNSUPPORTED);
            }
            data = data.substring(comma + 1);
        }
        // Cheap pre-check before stripping whitespace / decoding: even allowing for
        // line breaks, a body this long can't decode to <= 2 MB.
        if (data.length() > MAX_ENCODED_CHARS + MAX_ENCODED_CHARS / 64 + 16) {
            throw new BadRequestException(TOO_LARGE);
        }
        data = data.replaceAll("\\s", "");
        if (data.length() > MAX_ENCODED_CHARS) {
            throw new BadRequestException(TOO_LARGE);
        }
        byte[] bytes;
        try {
            bytes = Base64.getDecoder().decode(data);
        } catch (IllegalArgumentException ex) {
            throw new BadRequestException("Invalid photo encoding");
        }
        if (bytes.length == 0) {
            throw new BadRequestException("Photo is empty");
        }
        if (bytes.length > MAX_BYTES) {
            throw new BadRequestException(TOO_LARGE);
        }
        String type = sniff(bytes);
        if (type == null) {
            throw new BadRequestException(UNSUPPORTED);
        }
        return new Photo(bytes, type);
    }

    /** The image type from the file signature, or null when it isn't JPEG/PNG/HEIC/HEIF. */
    static String sniff(byte[] b) {
        if (b.length >= 3 && (b[0] & 0xFF) == 0xFF && (b[1] & 0xFF) == 0xD8 && (b[2] & 0xFF) == 0xFF) {
            return JPEG;
        }
        if (b.length >= 8 && (b[0] & 0xFF) == 0x89 && b[1] == 'P' && b[2] == 'N' && b[3] == 'G'
                && b[4] == 0x0D && b[5] == 0x0A && b[6] == 0x1A && b[7] == 0x0A) {
            return PNG;
        }
        // ISO-BMFF: [4-byte box size]["ftyp"][4-byte major brand]
        if (b.length >= 12 && b[4] == 'f' && b[5] == 't' && b[6] == 'y' && b[7] == 'p') {
            String brand = new String(b, 8, 4, StandardCharsets.US_ASCII).toLowerCase();
            if (HEIC_BRANDS.contains(brand)) return HEIC;
            if (HEIF_BRANDS.contains(brand)) return HEIF;
        }
        return null;
    }

    /** The stored content-type, falling back to JPEG when missing. */
    public static String typeOrDefault(String type) {
        return type != null && !type.isBlank() ? type : DEFAULT_TYPE;
    }
}
