package jp.mufg.api.util;

import net.openhft.lang.io.Bytes;
import org.jetbrains.annotations.NotNull;

import java.util.function.Consumer;

// TODO daniels do we need these as they are not called.
public enum BytesUtils {
    ;

    public static void writeBounded(@NotNull Bytes bytes, @NotNull Consumer<Bytes> bytesConsumer) {
        long position = bytes.position();
        bytes.writeUnsignedShort(0);

        bytesConsumer.accept(bytes);

        long length = bytes.position() - position - 2;
        if (length >= 1 << 16)
            throw new AssertionError("length: " + length);
        bytes.writeUnsignedShort(position, (int) length);
    }

    public static void readBounded(@NotNull Bytes bytes, @NotNull Consumer<Bytes> bytesConsumer) {
        // support future schema changes by length.
        int length = bytes.readUnsignedShort();
        long limit = bytes.limit();
        long limit2 = bytes.position() + length;
        assert limit2 <= limit;
        // TODO doesn't work due to JLANG-51
//        bytes.limit(limit2);
        try {
            bytesConsumer.accept(bytes);
            assert bytes.position() <= limit2;
        } finally {
            bytes.limit(limit);
            bytes.position(limit2);
        }
    }
}