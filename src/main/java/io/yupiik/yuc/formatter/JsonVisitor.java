package io.yupiik.yuc.formatter;

public interface JsonVisitor {
    void onStartArray();

    void onEndArray();

    void onStartObject();

    void onEndObject();

    void onKey(String name);

    void onString(String value);

    void onBoolean(boolean value);

    void onNumber(String value);

    void onNull();

    default void onEnd() {
        // no-op
    }
}
