package com.bank.ecl.common.util;

import java.util.UUID;

/**
 * UUID and business code generation utilities.
 */
public class UuidGenerator {

    /**
     * Generate a UUID string without hyphens.
     */
    public static String uuid() {
        return UUID.randomUUID().toString().replace("-", "");
    }

    /**
     * Generate a business code with prefix and zero-padded sequence.
     * <p>
     * Examples: SCH_001, GRP_001, GRP_042
     */
    public static String generateBizCode(String prefix, long seq) {
        return String.format("%s_%03d", prefix, seq);
    }

    private UuidGenerator() {
        // utility class
    }
}
