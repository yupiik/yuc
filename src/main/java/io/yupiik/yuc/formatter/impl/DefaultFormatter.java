/*
 * Copyright (c) 2023 - Yupiik SAS - https://www.yupiik.com
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
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

    protected String numberValue(final String value) {
        return color.onNumber(value);
    }

    protected String nullValue() {
        return color.onNull();
    }

    @Override
    public void onStartArray() {
        beforeValue();
        output.write(openArray());
        state.add(START_ARRAY);
    }

    @Override
    public void onEndArray() {
        output.write(closeArray());
        if (state.removeLast() == IN_ARRAY) {
            state.removeLast(); // START_ARRAY
        }
        updateStateAfterValueIfNeeded();
    }

    @Override
    public void onStartObject() {
        beforeValue();
        output.write(openObject());
        state.add(START_OBJECT);
    }

    @Override
    public void onEndObject() {
        output.write(closeObject());
        if (state.removeLast() == IN_OBJECT) {
            state.removeLast(); // START_OBJECT
        }
        updateStateAfterValueIfNeeded();
    }

    @Override
    public void onKey(final String name) {
        beforeKey();
        output.write(key(name) + ":");
    }

    @Override
    public void onString(final String value) {
        beforeValue();
        output.write(string(value));
        updateStateAfterValueIfNeeded();
    }

    @Override
    public void onBoolean(final boolean value) {
        beforeValue();
        output.write(booleanValue(value));
        updateStateAfterValueIfNeeded();
    }

    @Override
    public void onNumber(final String value) {
        beforeValue();
        output.write(numberValue(value));
        updateStateAfterValueIfNeeded();
    }

    @Override
    public void onNull() {
        beforeValue();
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

    protected void beforeValue() {
        if (!state.isEmpty() && state.getLast() == IN_ARRAY) {
            separator();
        }
    }

    protected void beforeKey() {
        if (!state.isEmpty() && state.getLast() == IN_OBJECT) {
            separator();
        }
    }

    protected void separator() {
        output.write(attributeSeparator());
    }

    protected enum State {
        START_ARRAY, IN_ARRAY,
        START_OBJECT, IN_OBJECT
    }
}
