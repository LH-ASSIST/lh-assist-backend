package com.lh.assist.support;

public final class ReflectionTestUtils {

    private ReflectionTestUtils() {
    }

    public static void setField(
            Object target,
            String fieldName,
            Object value
    ) {
        try {
            var field = target.getClass().getDeclaredField(fieldName);
            field.setAccessible(true);
            field.set(target, value);
        } catch (NoSuchFieldException | IllegalAccessException ex) {
            throw new IllegalStateException("Failed to set " + fieldName, ex);
        }
    }
}