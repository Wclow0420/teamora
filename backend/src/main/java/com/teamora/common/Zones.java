package com.teamora.common;

import java.time.ZoneId;

/**
 * The business time zone. The server runs in UTC, so a bare {@code LocalDate.now(com.teamora.common.Zones.KL)}
 * is still "yesterday" for the first eight hours of every Malaysian day — always
 * ask for the date in this zone instead.
 */
public final class Zones {

    public static final ZoneId KL = ZoneId.of("Asia/Kuala_Lumpur");

    private Zones() {
    }
}
