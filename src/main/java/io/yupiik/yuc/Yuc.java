package io.yupiik.yuc;

import io.yupiik.fusion.framework.api.main.Launcher;

import java.util.stream.Stream;

public final class Yuc {
    private Yuc() {
        // no-op
    }

    public static void main(final String... args) {
        // force default command for now
        final var implicitCommand = "default";
        Launcher.main(args.length == 0 ?
                new String[]{implicitCommand} :
                Stream.concat(Stream.of(implicitCommand), Stream.of(args)).toArray(String[]::new));
    }
}
