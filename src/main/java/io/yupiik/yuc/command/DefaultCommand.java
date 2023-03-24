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

import java.io.IOException;
import java.io.Writer;
import java.nio.charset.Charset;

@Command(name = "default", description = "Format the output as a prettified JSON.")
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
        final var input = io.openInput(charset, conf.input());
        try (final var parser = new JsonParser(input, conf.bufferProviderSize(), new BufferProvider(conf.bufferProviderSize()), true);
             final var writer = io.openOutput(charset, conf.output())) {
            final var visitor = newVisitor(writer, charset);
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
            if (conf.appendEol()) {
                writer.write('\n');
            }
        } catch (final IOException e) {
            throw new IllegalStateException(e);
        }
    }

    private JsonVisitor newVisitor(final Writer writer, final Charset charset) {
        final var output = new SimpleWriter(writer);
        return switch (conf.outputType()) {
            case HANDLEBAR -> new HandlebarFormatter(output, conf.handlebar(), jsonMapper, charset);
            case PRETTY -> new PrettyFormatter(output, getColorScheme());
            default -> new DefaultFormatter(output, getColorScheme());
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
        HANDLEBAR
    }

    @RootConfiguration("-")
    public record Conf(
            @Property(defaultValue = "\"{{this}}\"", documentation = "If `true` output is colorized.") String handlebar,
            @Property(defaultValue = "io.yupiik.yuc.command.configuration.ColorScheme.DEFAULT", documentation = "If `true` output is colorized.") ColorScheme colorScheme,
            @Property(defaultValue = "null", documentation = "If `true` output is colorized.") Boolean colored,
            @Property(defaultValue = "true", documentation = "If `true` an EOL is appended to the output stream.") boolean appendEol,
            @Property(defaultValue = "DefaultCommand.OutputType.PRETTY", documentation = "Output type.") OutputType outputType,
            @Property(defaultValue = "16384", documentation = "JSON parser buffer provider size.") int bufferProviderSize,
            @Property(defaultValue = "true", documentation = "Should the JSON be prettified.") boolean pretty,
            @Property(defaultValue = "\"UTF-8\"", documentation = "Charset to use to read the input stream.") String charset,
            @Property(defaultValue = "\"-\"", documentation = "Output the command should use, default to `stdout` if set to `-` else a file path.") String output,
            @Property(defaultValue = "\"-\"", documentation = "Input the command should format, default to `stdin` if set to `-` else a file path.") String input) {
    }
}
