package io.yupiik.yuc.formatter.impl;

import io.yupiik.yuc.command.configuration.ColorScheme;
import io.yupiik.yuc.formatter.SimpleWriter;

import static io.yupiik.yuc.formatter.impl.DefaultFormatter.State.IN_ARRAY;
import static io.yupiik.yuc.formatter.impl.DefaultFormatter.State.IN_OBJECT;

public class PrettyFormatter extends DefaultFormatter {
    private static final String INDENT_STEP = "  "; // todo: make configurable?

    private String currentIndent = "";

    public PrettyFormatter(final SimpleWriter output, final ColorScheme color) {
        super(output, color);
    }

    @Override
    public void onStartArray() {
        beforeStartStructure();
        super.onStartArray();
        afterStartStructure();
    }

    @Override
    public void onEndArray() {
        beforeEndStructure();
        super.onEndArray();
    }

    @Override
    public void onStartObject() {
        beforeStartStructure();
        super.onStartObject();
        afterStartStructure();
    }

    @Override
    public void onEndObject() {
        beforeEndStructure();
        super.onEndObject();
    }

    @Override
    protected void beforeKey() {
        if (!state.isEmpty() && state.getLast() == IN_OBJECT) {
            output.write(",\n");
        }
        output.write(currentIndent);
    }

    @Override
    public void onString(final String value) {
        beforeValue();
        super.onString(value);
    }

    @Override
    public void onBoolean(final boolean value) {
        beforeValue();
        super.onBoolean(value);
    }

    @Override
    public void onNumber(final String value) {
        beforeValue();
        super.onNumber(value);
    }

    @Override
    public void onNull() {
        beforeValue();
        super.onNull();
    }

    private void beforeStartStructure() {
        if (!state.isEmpty() && state.getLast() == IN_ARRAY) {
            output.write(",\n");
            output.write(currentIndent);
        }
    }

    private void afterStartStructure() {
        output.write("\n");
        currentIndent += INDENT_STEP;
    }

    private void beforeEndStructure() {
        if (!currentIndent.isEmpty()) {
            currentIndent = currentIndent.substring(0, currentIndent.length() - INDENT_STEP.length());
            output.write("\n");
            output.write(currentIndent);
        }
    }

    private void beforeValue() {
        if (!state.isEmpty() && state.getLast().name().endsWith("_ARRAY")) {
            output.write(currentIndent);
        }
    }
}
