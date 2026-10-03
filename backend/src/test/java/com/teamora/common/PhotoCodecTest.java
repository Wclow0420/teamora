package com.teamora.common;

import com.teamora.common.exception.BadRequestException;
import org.junit.jupiter.api.Test;

import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Photo whitelist by magic bytes + size checks before decoding. */
class PhotoCodecTest {

    private static String b64(byte[] b) {
        return Base64.getEncoder().encodeToString(b);
    }

    private static byte[] heif(String brand) {
        byte[] b = new byte[16];
        b[3] = 0x18;
        System.arraycopy("ftyp".getBytes(), 0, b, 4, 4);
        System.arraycopy(brand.getBytes(), 0, b, 8, 4);
        return b;
    }

    @Test
    void sniffsJpegPngHeicHeif() {
        assertThat(PhotoCodec.decode(b64(new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0})).contentType())
                .isEqualTo("image/jpeg");
        assertThat(PhotoCodec.decode("data:image/png;base64,iVBORw0KGgo=").contentType()).isEqualTo("image/png");
        assertThat(PhotoCodec.decode(b64(heif("heic"))).contentType()).isEqualTo("image/heic");
        assertThat(PhotoCodec.decode("data:image/heif;base64," + b64(heif("mif1"))).contentType()).isEqualTo("image/heif");
    }

    @Test
    void declaredTypeNeverOverridesTheBytes() {
        // JPEG bytes declared as PNG are stored as JPEG.
        assertThat(PhotoCodec.decode("data:image/png;base64,/9j/2Q==").contentType()).isEqualTo("image/jpeg");
    }

    @Test
    void refusesOtherFormats_andNonImageDataUrls() {
        assertThatThrownBy(() -> PhotoCodec.decode(b64("GIF89a....".getBytes())))
                .isInstanceOf(BadRequestException.class).hasMessageContaining("JPEG, PNG or HEIC");
        assertThatThrownBy(() -> PhotoCodec.decode(b64("<svg onload=alert(1)>".getBytes())))
                .isInstanceOf(BadRequestException.class);
        assertThatThrownBy(() -> PhotoCodec.decode("data:image/svg+xml;base64,/9j/2Q=="))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    void oversizedIsRefusedBeforeDecoding() {
        // Not valid base64 at all — proves the length check runs first.
        String huge = "*".repeat(PhotoCodec.MAX_ENCODED_CHARS + 100_000);
        assertThatThrownBy(() -> PhotoCodec.decode(huge)).hasMessageContaining("too large");
    }
}
