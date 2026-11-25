package com.sfi.scaffold.common.util;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StreamPipeUtilsTest {

    @Test
    void copyTransfersAllBytesAndKeepsOutputStreamOpen() throws IOException {
        byte[] data = "hello-stream-pipe".getBytes(StandardCharsets.UTF_8);
        TrackingInputStream in = new TrackingInputStream(data);
        TrackingOutputStream out = new TrackingOutputStream();

        StreamPipeUtils.copy(in, out);

        assertArrayEquals(data, out.toByteArray());
        assertTrue(in.closed, "默认关闭输入流");
        assertFalse(out.closed, "绝不关闭输出流");
    }

    @Test
    void copyWithCloseInFalseLeavesInputStreamOpen() throws IOException {
        TrackingInputStream in = new TrackingInputStream("x".getBytes(StandardCharsets.UTF_8));
        TrackingOutputStream out = new TrackingOutputStream();

        StreamPipeUtils.copy(in, out, false);

        assertFalse(in.closed, "closeIn=false 时由框架负责关闭输入流");
        assertFalse(out.closed);
    }

    @Test
    void copySwallowsClientAbortExceptions() throws IOException {
        byte[] data = "abc".getBytes(StandardCharsets.UTF_8);
        InputStream in = new ByteArrayInputStream(data);
        OutputStream out = new OutputStream() {
            @Override
            public void write(int b) throws IOException {
                throw new IOException("Broken pipe");
            }
        };

        StreamPipeUtils.copy(in, out);
    }

    @Test
    void copyRethrowsRealIOException() {
        InputStream in = new ByteArrayInputStream("abc".getBytes(StandardCharsets.UTF_8));
        OutputStream out = new OutputStream() {
            @Override
            public void write(int b) throws IOException {
                throw new IOException("disk full");
            }
        };

        assertThrows(IOException.class, () -> StreamPipeUtils.copy(in, out));
    }

    @Test
    void toByteArrayAndToBase64() throws IOException {
        byte[] data = "stream".getBytes(StandardCharsets.UTF_8);
        assertArrayEquals(data, StreamPipeUtils.toByteArray(new ByteArrayInputStream(data)));
        assertEquals(java.util.Base64.getEncoder().encodeToString(data),
                StreamPipeUtils.toBase64(new ByteArrayInputStream(data)));
    }

    private static class TrackingInputStream extends ByteArrayInputStream {
        boolean closed;

        TrackingInputStream(byte[] data) {
            super(data);
        }

        @Override
        public void close() throws IOException {
            closed = true;
            super.close();
        }
    }

    private static class TrackingOutputStream extends ByteArrayOutputStream {
        boolean closed;

        @Override
        public void close() throws IOException {
            closed = true;
            super.close();
        }
    }
}
