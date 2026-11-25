package com.sfi.scaffold.common.util;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

/**
 * 流拷贝工具：在流之间搬运字节，屏蔽客户端断开异常，按约定管理输入流生命周期。
 *
 * <p>输出流始终由调用方关闭（适配 Servlet 容器托管）；输入流由 closeIn 决定归属。</p>
 *
 * @author lianghang
 */
public final class StreamPipeUtils {

    private static final int BUFFER_SIZE = 8192;

    private StreamPipeUtils() {
    }

    /**
     * 拷贝字节，结束后关闭输入流，不关闭输出流。
     *
     * @param in  输入流
     * @param out 输出流
     * @throws IOException 非客户端断开的读写异常
     */
    public static void copy(InputStream in, OutputStream out) throws IOException {
        copy(in, out, true);
    }

    /**
     * 拷贝字节，按 closeIn 决定是否关闭输入流，始终不关闭输出流。
     * 客户端断开（Broken pipe / Connection reset / Aborted）时静默返回。
     *
     * @param in      输入流
     * @param out     输出流
     * @param closeIn true 结束后关闭输入流；false 仅借道读取，由框架负责关闭
     * @throws IOException 非客户端断开的读写异常
     */
    public static void copy(InputStream in, OutputStream out, boolean closeIn) throws IOException {
        if (in == null || out == null) {
            throw new IllegalArgumentException("输入流和输出流不能为空");
        }
        byte[] buffer = new byte[BUFFER_SIZE];
        try {
            int n;
            while ((n = in.read(buffer)) != -1) {
                out.write(buffer, 0, n);
            }
            out.flush();
        } catch (IOException e) {
            String msg = e.getMessage();
            if (msg == null || (!msg.contains("Broken pipe")
                    && !msg.contains("Connection reset") && !msg.contains("Aborted"))) {
                throw e;
            }
        } finally {
            if (closeIn) {
                try {
                    in.close();
                } catch (IOException ignored) {
                    // 关闭异常无需处理
                }
            }
        }
    }

    /**
     * 读取输入流为字节数组，读取后关闭输入流。
     *
     * @param in 输入流
     * @return 字节数组
     * @throws IOException 读取异常
     */
    public static byte[] toByteArray(InputStream in) throws IOException {
        return toByteArray(in, true);
    }

    /**
     * 读取输入流为字节数组，按 closeIn 决定是否关闭输入流。
     *
     * @param in      输入流
     * @param closeIn true 读取后关闭输入流；false 由框架负责关闭
     * @return 字节数组
     * @throws IOException 读取异常
     */
    public static byte[] toByteArray(InputStream in, boolean closeIn) throws IOException {
        if (in == null) {
            throw new IllegalArgumentException("输入流不能为空");
        }
        ByteArrayOutputStream sink = new ByteArrayOutputStream(BUFFER_SIZE);
        copy(in, sink, closeIn);
        return sink.toByteArray();
    }

    /**
     * 输入流转为 Base64 字符串，读取后关闭输入流。
     *
     * @param in 输入流
     * @return Base64 字符串
     * @throws IOException 读取异常
     */
    public static String toBase64(InputStream in) throws IOException {
        return toBase64(in, true);
    }

    /**
     * 输入流转为 Base64 字符串，按 closeIn 决定是否关闭输入流。
     *
     * @param in      输入流
     * @param closeIn true 读取后关闭输入流；false 由框架负责关闭
     * @return Base64 字符串
     * @throws IOException 读取异常
     */
    public static String toBase64(InputStream in, boolean closeIn) throws IOException {
        if (in == null) {
            throw new IllegalArgumentException("输入流不能为空");
        }
        ByteArrayOutputStream sink = new ByteArrayOutputStream(BUFFER_SIZE + (BUFFER_SIZE / 3));
        try (OutputStream base64Out = Base64.getEncoder().wrap(sink)) {
            copy(in, base64Out, closeIn);
        }
        return new String(sink.toByteArray(), StandardCharsets.UTF_8);
    }
}
