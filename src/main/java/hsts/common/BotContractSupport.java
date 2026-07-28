package hsts.common;

import java.util.ArrayList;
import java.util.List;

final class BotContractSupport {
    private BotContractSupport() {
    }

    static int requirePositive(int value, String message) {
        if (value <= 0) {
            throw new IllegalArgumentException(message);
        }
        return value;
    }

    static int requireNonNegative(int value, String message) {
        if (value < 0) {
            throw new IllegalArgumentException(message);
        }
        return value;
    }

    static String requireNonBlank(String value, String message) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException(message);
        }
        return value.trim();
    }

    static <T> List<T> immutableList(List<T> values, String nullListMessage,
                                     String nullElementMessage) {
        if (values == null) {
            throw new IllegalArgumentException(nullListMessage);
        }
        List<T> copy = new ArrayList<>(values.size());
        for (T value : values) {
            if (value == null) {
                throw new IllegalArgumentException(nullElementMessage);
            }
            copy.add(value);
        }
        return List.copyOf(copy);
    }
}
