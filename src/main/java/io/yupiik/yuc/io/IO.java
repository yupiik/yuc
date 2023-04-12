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
package io.yupiik.yuc.io;

import io.yupiik.fusion.framework.api.scope.ApplicationScoped;

import java.io.BufferedReader;
import java.io.FilterInputStream;
import java.io.FilterOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintStream;
import java.io.PushbackReader;
import java.io.Writer;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;

@ApplicationScoped
public class IO {
    // todo: optimize buffer usages (+ config from CLI - already there anyway, just needs to be propagated)
    public BufferedReader openInput(final Charset charset, final String value, final int bufferSize) {
        try {
            final var rawReader = switch (value) {
                case "&0", "-" -> new InputStreamReader(new FilterInputStream(System.in) {
                    @Override
                    public void close() {
                        // no-op
                    }
                }, charset);
                default -> new BufferedReader(new InputStreamReader(Files.newInputStream(Path.of(value)), charset), bufferSize);
            };
            final var pushbackReader = new PushbackReader(rawReader);
            final int first = pushbackReader.read();
            pushbackReader.unread(first);
            if (first == '<') { // assume xml
                return new BufferedReader(new Xml2JsonReader(pushbackReader));
            }
            return new BufferedReader(pushbackReader);
        } catch (final IOException e) {
            throw new IllegalArgumentException("Invalid input: '" + value + "'", e);
        }
    }

    public Writer openOutput(final Charset charset, final String value) {
        try {
            return switch (value) {
                case "&1", "-" -> wrapOut(System.out, charset);
                case "&2" -> wrapOut(System.err, charset);
                default -> {
                    final var out = Path.of(value);
                    if (out.getParent() != null) {
                        Files.createDirectories(out.getParent());
                    }
                    yield Files.newBufferedWriter(out, charset);
                }
            };
        } catch (final IOException e) {
            throw new IllegalArgumentException("Invalid input: '" + value + "'", e);
        }
    }

    private Writer wrapOut(final PrintStream stream, final Charset charset) {
        return new OutputStreamWriter(new FilterOutputStream(stream) {
            @Override
            public void close() {
                try {
                    flush();
                } catch (final IOException e) {
                    // no-op, best effort
                }
            }
        }, charset);
    }
}
