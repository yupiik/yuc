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
package io.yupiik.yuc.command;

import io.yupiik.fusion.framework.build.api.cli.Command;
import io.yupiik.fusion.framework.build.api.configuration.Property;
import io.yupiik.fusion.framework.build.api.configuration.RootConfiguration;
import io.yupiik.fusion.json.JsonMapper;
import io.yupiik.fusion.json.internal.parser.BufferProvider;
import io.yupiik.fusion.json.internal.parser.JsonParser;
import io.yupiik.yuc.command.configuration.ColorScheme;
import io.yupiik.yuc.formatter.JsonVisitor;
import io.yupiik.yuc.formatter.SimpleWriter;
import io.yupiik.yuc.formatter.impl.DefaultFormatter;
import io.yupiik.yuc.formatter.impl.HandlebarFormatter;
import io.yupiik.yuc.formatter.impl.PrettyFormatter;
import io.yupiik.yuc.io.IO;
import org.fusesource.jansi.internal.CLibrary;

import java.io.FilterReader;
import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;
import java.io.Writer;
import java.nio.charset.Charset;
import java.util.function.Supplier;

@Command(name = "default", description = "" +
        "Format the output as a prettified JSON." +
        "Note that if no command is set as first parameter," +
        "this command is used.")
public class DefaultCommand implements Runnable {
    private final Conf conf;
    private final IO io;
    private final JsonMapper jsonMapper;

    public DefaultCommand(final Conf conf, final IO io, final JsonMapper jsonMapper) {
        this.conf = conf;
        this.io = io;
        this.jsonMapper = jsonMapper;
    }

    @Override
    public void run() {
        final var charset = Charset.forName(conf.charset());
        final var bufferProvider = new BufferProvider(conf.bufferProviderSize(), 8 /* could be 2 theorically but does not change much */);
        try (final var input = io.openInput(charset, conf.input());
             final var writer = io.openOutput(charset, conf.output())) {
            final var visitorFactory = newVisitor(writer, charset);
            if (!conf.ndjson()) {
                try (final var parser = newParser(bufferProvider, input)) {
                    onData(parser, writer, visitorFactory.get());
                }
                afterLine(writer);
            } else {
                String line;
                boolean addEol = true;
                while ((line = input.readLine()) != null) {
                    if (addEol) {
                        addEol = false;
                    } else {
                        writer.write('\n');
                    }
                    try {
                        if (isDataLine(line)) {
                            try (final var parser = newParser(bufferProvider, new StringReader(line.strip()))) {
                                onData(parser, writer, visitorFactory.get());
                            }
                        } else if (!conf.ndjsonIgnoreUnknownLines()) {
                            writer.write(line);
                        } else {
                            addEol = true;
                        }
                    } catch (final RuntimeException | IOException e) {
                        if (!conf.ndjsonIgnoreUnknownLines()) {
                            writer.write(line);
                        }
                    }
                }
                afterLine(writer);
            }
        } catch (final IOException e) {
            throw new IllegalStateException(e);
        }
    }

    private boolean isDataLine(final String line) {
        return (line.startsWith("{") && line.endsWith("}")) ||
                (line.startsWith("<") && line.endsWith(">"));
    }

    private JsonParser newParser(final BufferProvider bufferProvider, final Reader input) {
        return new JsonParser(new FilterReader(input) {
            @Override
            public void close() {
                // no-op
            }
        }, conf.bufferProviderSize(), bufferProvider, true);
    }

    private void onData(final JsonParser parser, final Writer writer, final JsonVisitor visitor) throws IOException {
        while (parser.hasNext()) {
            switch (parser.next()) {
                case START_ARRAY -> visitor.onStartArray();
                case END_ARRAY -> visitor.onEndArray();
                case START_OBJECT -> visitor.onStartObject();
                case END_OBJECT -> visitor.onEndObject();
                case KEY_NAME -> visitor.onKey(parser.getString());
                case VALUE_STRING -> visitor.onString(parser.getString());
                case VALUE_TRUE -> visitor.onBoolean(true);
                case VALUE_FALSE -> visitor.onBoolean(false);
                case VALUE_NUMBER -> visitor.onNumber(parser.getString());
                case VALUE_NULL -> visitor.onNull();
            }
        }
        visitor.onEnd();
    }

    private void afterLine(final Writer writer) throws IOException {
        if (conf.appendEol()) {
            writer.write('\n');
        }
    }

    private Supplier<JsonVisitor> newVisitor(final Writer writer, final Charset charset) {
        final var output = new SimpleWriter(writer);
        return switch (conf.outputType()) {
            case HANDLEBARS -> () -> new HandlebarFormatter(output, conf.handlebars(), jsonMapper, charset);
            case PRETTY -> () -> new PrettyFormatter(output, getColorScheme());
            default -> () -> new DefaultFormatter(output, getColorScheme());
        };
    }

    private ColorScheme getColorScheme() {
        return (conf.colored() != null && conf.colored()) || (conf.colored() == null && isTty()) ? conf.colorScheme() : ColorScheme.NONE;
    }

    private boolean isTty() {
        return !System.getProperty("os.name", "win").contains("win") &&
                (switch (conf.output()) {
                    case "&1", "-" -> CLibrary.isatty(1) == 1;
                    case "&2" -> CLibrary.isatty(2) == 1;
                    default -> false;
                });
    }

    public enum OutputType {
        INLINE,
        PRETTY,
        HANDLEBARS
    }

    @RootConfiguration("-")
    public record Conf(
            @Property(defaultValue = "\"{{this}}\"", documentation = "If `true` output is colorized. Default: `{{this}}`.") String handlebars,
            @Property(value = "color-scheme", defaultValue = "io.yupiik.yuc.command.configuration.ColorScheme.DEFAULT", documentation = "If `true` output is colorized. Default: `DEFAULT`.") ColorScheme colorScheme,
            @Property(defaultValue = "null", documentation = "If `true` output is colorized. Default is automatic, if a tty is detected colors are enabled else disabled.") Boolean colored,
            @Property(value = "append-eol", defaultValue = "true", documentation = "If `true` an EOL is appended to the output stream. Default: `true`.") boolean appendEol,
            @Property(value = "output-type", defaultValue = "DefaultCommand.OutputType.PRETTY", documentation = "Output type. Default: `PRETTY`.") OutputType outputType,
            @Property(value = "buffer-provider-size", defaultValue = "16384", documentation = "JSON parser buffer provider size. Default: `16384`.") int bufferProviderSize,
            @Property(defaultValue = "true", documentation = "Should the JSON be prettified. Default: `true`.") boolean pretty,
            @Property(defaultValue = "\"UTF-8\"", documentation = "Charset to use to read the input stream. Default: `UTF-8`.") String charset,
            @Property(defaultValue = "\"-\"", documentation = "Output the command should use, default to `stdout` if set to `-` else a file path. Default: `-`.") String output,
            @Property(defaultValue = "\"-\"", documentation = "Input the command should format, default to `stdin` if set to `-` else a file path. Default: `-`.") String input,
            @Property(defaultValue = "false", documentation = "If `true`, input is handled per line instead of globally.") boolean ndjson,
            @Property(value = "ndjson-ignore-unknown", defaultValue = "false", documentation = "If `true`, not JSON/XML lines are swallowed.") boolean ndjsonIgnoreUnknownLines) {
    }
}
