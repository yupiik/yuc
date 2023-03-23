package io.yupiik.yuc.formatter;

import java.io.IOException;
import java.io.Writer;

public record SimpleWriter(Writer delegate) {
    public void write(final String value) {
        try {
            delegate.write(value);
        } catch (final IOException e) {
            throw new IllegalStateException(e);
        }
    }
}
