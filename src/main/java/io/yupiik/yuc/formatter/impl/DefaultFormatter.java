package io.yupiik.yuc.formatter.impl;

import io.yupiik.fusion.json.internal.JsonStrings;
import io.yupiik.yuc.command.configuration.ColorScheme;
import io.yupiik.yuc.formatter.JsonVisitor;
import io.yupiik.yuc.formatter.SimpleWriter;

import java.util.LinkedList;

import static io.yupiik.yuc.formatter.impl.DefaultFormatter.State.IN_ARRAY;
import static io.yupiik.yuc.formatter.impl.DefaultFormatter.State.IN_OBJECT;
import static io.yupiik.yuc.formatter.impl.DefaultFormatter.State.START_ARRAY;
import static io.yupiik.yuc.formatter.impl.DefaultFormatter.State.START_OBJECT;

public class DefaultFormatter implements JsonVisitor {
    protected final SimpleWriter output;
    protected final ColorScheme color;
    protected final LinkedList<State> state = new LinkedList<>();

    public DefaultFormatter(final SimpleWriter output, final ColorScheme color) {
        this.output = output;
        this.color = color;
    }

    protected String openArray() {
        return color.onArray("[");
    }

    protected String closeArray() {
        return color.onArray("]");
    }

    protected String openObject() {
        return color.onObject("{");
    }

    protected String attributeSeparator() {
        return color.onComma();
    }

    protected String closeObject() {
        return color.onObject("}");
    }

    protected String key(final String name) {
        return color.onKey(JsonStrings.escape(name));
    }

    protected String string(final String name) {
        return color.onString(JsonStrings.escape(name));
    }

    protected String booleanValue(final boolean value) {
        return value ? color.onTrue() : color.onFalse();
    }

    protected String numberValue(final Number value) {
        return color.onNumber(String.valueOf(value));
    }

    protected String nullValue() {
        return color.onNull();
    }

    @Override
    public void onStartArray() {
        output.write(openArray());
        state.add(START_ARRAY);
    }

    @Override
    public void onEndArray() {
        output.write(closeArray());
        if (state.removeLast() == IN_ARRAY) {
            state.removeLast();
        }
    }

    @Override
    public void onStartObject() {
        output.write(openObject());
        state.add(START_OBJECT);
    }

    @Override
    public void onEndObject() {
        output.write(closeObject());
        if (state.removeLast() == IN_OBJECT) {
            state.removeLast();
        }
    }

    @Override
    public void onKey(final String name) {
        beforeKey();
        output.write(key(name) + ": ");
    }

    @Override
    public void onString(final String value) {
        output.write(string(value));
        updateStateAfterValueIfNeeded();
    }

    @Override
    public void onBoolean(final boolean value) {
        output.write(booleanValue(value));
        updateStateAfterValueIfNeeded();
    }

    @Override
    public void onNumber(final Number value) {
        output.write(numberValue(value));
        updateStateAfterValueIfNeeded();
    }

    @Override
    public void onNull() {
        output.write(nullValue());
        updateStateAfterValueIfNeeded();
    }

    private void updateStateAfterValueIfNeeded() {
        if (state.isEmpty()) {
            return;
        }
        final var last = state.getLast();
        switch (last) {
            case START_OBJECT -> state.add(IN_OBJECT);
            case START_ARRAY -> state.add(IN_ARRAY);
            default -> {
                // error?
            }
        }
    }

    protected void beforeKey() {
        if (!state.isEmpty() && state.getLast() == IN_OBJECT) {
            output.write(attributeSeparator() + " ");
        }
    }

    protected enum State {
        START_ARRAY, IN_ARRAY,
        START_OBJECT, IN_OBJECT
    }
}
