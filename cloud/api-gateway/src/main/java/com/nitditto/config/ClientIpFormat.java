package com.nitditto.config;

/** Kiểm tra chuỗi là địa chỉ IPv4/IPv6 literal (không phân giải DNS) trước khi tin giá trị X-Forwarded-For. */
final class ClientIpFormat {

    private ClientIpFormat() {
    }

    static boolean isIpLiteral(final String value) {
        if (value == null || value.isEmpty() || value.length() > 45) {
            return false;
        }
        if (value.indexOf(':') < 0) {
            final String[] parts = value.split("[.]", -1);
            if (parts.length != 4) {
                return false;
            }
            for (final String part : parts) {
                if (part.isEmpty() || part.length() > 3 || !part.chars().allMatch(Character::isDigit)
                        || Integer.parseInt(part) > 255) {
                    return false;
                }
            }
            return true;
        }
        if (!value.chars().allMatch(c -> Character.digit(c, 16) >= 0 || c == ':' || c == '.')) {
            return false;
        }
        try {
            java.net.InetAddress.getByName(value);
            return true;
        } catch (final java.net.UnknownHostException ex) {
            return false;
        }
    }
}
