package com.teamora.common;

import com.teamora.common.exception.BadRequestException;

import java.util.Base64;

/**
 * Decodes photos sent inline in a JSON body (clock-in selfies, claim receipts).
 * Accepts bare base64 or a {@code data:image/...;base64,...} data URL, and
 * enforces the shared 2 MB decoded-size cap. Invalid input → 400.
 */
public final class PhotoCodec {

    /** Cap on the decoded photo size (~2 MB) — clients downscale before sending. */
    public static final int MAX_BYTES = 2 * 1024 * 1024;
    public static final String DEFAULT_TYPE = "image/jpeg";
    /** Matches the VARCHAR(32) content-type columns. */
    private static final int MAX_TYPE_LENGTH = 32;

    private PhotoCodec() {}

    /** Decoded bytes + content-type. */
    public record Photo(byte[] bytes, String contentType) {}

    /** Bare base64 or a data URL → decoded bytes + content-type. */
    public static Photo decode(String raw) {
        String data = raw.trim();
        String contentType = DEFAULT_TYPE;
        if (data.startsWith("data:")) {
            int comma = data.indexOf(',');
            if (comma < 0) {
                throw new BadRequestException("Invalid photo data URL");
            }
            String header = data.substring(5, comma); // e.g. "image/jpeg;base64"
            int semi = header.indexOf(';');
            String mime = (semi >= 0 ? header.substring(0, semi) : header).trim();
            if (mime.startsWith("image/") && mime.length() <= MAX_TYPE_LENGTH) {
                contentType = mime;
            }
            data = data.substring(comma + 1);
        }
        data = data.replaceAll("\\s", "");
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
            throw new BadRequestException("Photo too large (max 2 MB). Please retake.");
        }
        return new Photo(bytes, contentType);
    }

    /** The stored content-type, falling back to JPEG when missing. */
    public static String typeOrDefault(String type) {
        return type != null && !type.isBlank() ? type : DEFAULT_TYPE;
    }
}
