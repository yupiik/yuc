package io.yupiik.yuc.io;

import io.yupiik.fusion.framework.api.scope.ApplicationScoped;

import java.io.FilterInputStream;
import java.io.FilterOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintStream;
import java.io.PushbackReader;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;

@ApplicationScoped
public class IO {
    public Reader openInput(final Charset charset, final String value) {
        try {
            final var rawReader = switch (value) {
                case "&0", "-" -> new InputStreamReader(new FilterInputStream(System.in) {
                    @Override
                    public void close() {
                        // no-op
                    }
                }, charset);
                default -> Files.newBufferedReader(Path.of(value), charset);
            };
            final var pushbackReader = new PushbackReader(rawReader);
            final int first = pushbackReader.read();
            pushbackReader.unread(first);
            if (first == '<') { // assume xml
                return new Xml2JsonReader(pushbackReader);
            }
            return pushbackReader;
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
