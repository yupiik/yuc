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

import io.yupiik.yuc.command.configuration.ColorScheme;
import io.yupiik.yuc.formatter.SimpleWriter;

import static io.yupiik.yuc.formatter.impl.DefaultFormatter.State.IN_ARRAY;

public class PrettyFormatter extends DefaultFormatter {
    private static final String INDENT_STEP = "  "; // todo: make configurable?

    private String currentIndent = "";

    public PrettyFormatter(final SimpleWriter output, final ColorScheme color) {
        super(output, color);
    }

    @Override
    public void onStartArray() {
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
        super.onStartObject();
        afterStartStructure();
    }

    @Override
    public void onEndObject() {
        beforeEndStructure();
        super.onEndObject();
    }

    @Override
    public void onKey(final String name) {
        super.onKey(name);
        output.write(" ");
    }

    @Override
    protected void separator() {
        super.separator();
    }

    @Override
    protected void beforeKey() {
        if (!state.isEmpty() && state.getLast().name().startsWith("IN_")) {
            output.write(",\n");
        }
        output.write(currentIndent);
    }

    @Override
    protected void beforeValue() {
        super.beforeValue();
        if (!state.isEmpty() && state.getLast() == IN_ARRAY) {
            output.write("\n");
        }
        if (!state.isEmpty() && state.getLast().name().endsWith("_ARRAY")) {
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
}
