package io.yupiik.yuc.formatter.impl;

import io.yupiik.fusion.framework.handlebars.HandlebarsCompiler;
import io.yupiik.fusion.framework.handlebars.compiler.accessor.MapAccessor;
import io.yupiik.fusion.json.JsonMapper;
import io.yupiik.yuc.command.configuration.ColorScheme;
import io.yupiik.yuc.formatter.SimpleWriter;

import java.io.IOException;
import java.io.StringWriter;
import java.io.Writer;

public class HandlebarFormatter extends DefaultFormatter {
    private final String template;
    private final Writer writer;
    private final JsonMapper jsonMapper;

    public HandlebarFormatter(final SimpleWriter output, final String template, final JsonMapper jsonMapper) {
        super(new SimpleWriter(new StringWriter()), ColorScheme.NONE);
        this.writer = output.delegate();
        this.template = template;
        this.jsonMapper = jsonMapper;
    }

    @Override
    public void onEnd() {
        try {
            output.delegate().close();
            final var hb = new HandlebarsCompiler(new MapAccessor())
                    .compile(new HandlebarsCompiler.CompilationContext(new HandlebarsCompiler.Settings(), template));
            final var json = output.delegate().toString();
            final var object = jsonMapper.fromString(Object.class, json);
            final var rendered = hb.render(object);
            writer.write(rendered);
        } catch (final IOException ioe) {
            throw new IllegalStateException(ioe);
        }
    }
}
