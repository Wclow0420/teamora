package com.teamora.common.web;

import java.io.IOException;

/** The request body grew past {@code teamora.max-request-bytes} while being read → 413. */
public class PayloadTooLargeException extends IOException {
    public PayloadTooLargeException(long limit) {
        super("Request body exceeds " + limit + " bytes");
    }
}
