/*
 * Copyright 2012 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package io.netty5.handler.codec.http;

import io.netty5.buffer.Buffer;
import io.netty5.channel.ChannelHandlerContext;
import io.netty5.channel.ChannelPipeline;
import io.netty5.util.AsciiString;

/**
 * Decodes {@link Buffer}s into {@link HttpRequest}s and {@link HttpContent}s.
 *
 * <h3>Parameters that prevents excessive memory consumption</h3>
 * <table border="1">
 * <tr>
 * <th>Name</th><th>Meaning</th>
 * </tr>
 * <tr>
 * <td>{@code maxInitialLineLength}</td>
 * <td>The maximum length of the initial line (e.g. {@code "GET / HTTP/1.0"})
 *     If the length of the initial line exceeds this value, a
 *     {@link TooLongHttpLineException} will be raised.</td>
 * </tr>
 * <tr>
 * <td>{@code maxHeaderSize}</td>
 * <td>The maximum length of all headers.  If the sum of the length of each
 *     header exceeds this value, a {@link TooLongHttpHeaderException} will be raised.</td>
 * </tr>
 * <tr>
 * <td>{@code maxChunkSize}</td>
 * <td>The maximum length of the content or each chunk.  If the content length
 *     exceeds this value, the transfer encoding of the decoded request will be
 *     converted to 'chunked' and the content will be split into multiple
 *     {@link HttpContent}s.  If the transfer encoding of the HTTP request is
 *     'chunked' already, each chunk will be split into smaller chunks if the
 *     length of the chunk exceeds this value.  If you prefer not to handle
 *     {@link HttpContent}s in your handler, insert {@link HttpObjectAggregator}
 *     after this decoder in the {@link ChannelPipeline}.</td>
 * </tr>
 * </table>
 *
 * <h3>Header Validation</h3>
 *
 * It is recommended to always enable header validation.
 * <p>
 * Without header validation, your system can become vulnerable to
 * <a href="https://cwe.mitre.org/data/definitions/113.html">
 *     CWE-113: Improper Neutralization of CRLF Sequences in HTTP Headers ('HTTP Response Splitting')
 * </a>.
 * <p>
 * This recommendation stands even when both peers in the HTTP exchange are trusted,
 * as it helps with defence-in-depth.
 */
public class HttpRequestDecoder extends HttpObjectDecoder {

    private static final AsciiString Accept = AsciiString.cached("Accept");
    private static final AsciiString Host = AsciiString.cached("Host");
    private static final AsciiString Connection = AsciiString.cached("Connection");
    private static final AsciiString ContentType = AsciiString.cached("Content-Type");
    private static final AsciiString ContentLength = AsciiString.cached("Content-Length");

    private static final int GET_AS_INT = (int) charsToLong("GET");
    private static final int POST_AS_INT = (int) charsToLong("POST");
    private static final long HTTP_1_1_AS_LONG = charsToLong("HTTP/1.1");
    private static final long HTTP_1_0_AS_LONG = charsToLong("HTTP/1.0");;

    private static final int HOST_AS_INT = (int) charsToLong("Host");;

    private static final long CONNECTION_AS_LONG_0 = charsToLong("Connecti");

    private static final short CONNECTION_AS_SHORT_1 = (short) charsToLong("on");

    private static final long CONTENT_AS_LONG = charsToLong("Content-");;

    private static final int TYPE_AS_INT = (int) charsToLong("Type");

    private static final long LENGTH_AS_LONG = charsToLong("Length");

    private static final long ACCEPT_AS_LONG = charsToLong("Accept");

    private static long charsToLong(String cs) {
        long result = cs.charAt(0);
        int shift = 0;
        for (int i = 1; i < cs.length(); i++) {
            result |= (long) cs.charAt(i) << (shift += 8);
        }
        return result;
    }

    /**
     * Creates a new instance with the default
     * {@code maxInitialLineLength} ({@value DEFAULT_MAX_INITIAL_LINE_LENGTH}),
     * {@code maxHeaderSize} ({@value DEFAULT_MAX_HEADER_SIZE}),
     * and {@code chunkedSupported} ({@value DEFAULT_CHUNKED_SUPPORTED}).
     */
    public HttpRequestDecoder() {
    }

    /**
     * Creates a new instance with the specified parameters.
     *
     * @param maxInitialLineLength the initial size of the temporary buffer used when parsing the lines of the
     * HTTP headers.
     * @param maxHeaderSize the maximum permitted combined size of all headers in any one request.
     * @see HttpDecoderConfig HttpDecoderConfig API documentation for detailed descriptions of
     * the configuration parameters.
     */
    public HttpRequestDecoder(
            int maxInitialLineLength, int maxHeaderSize) {
        super(new HttpDecoderConfig()
                .setMaxInitialLineLength(maxInitialLineLength)
                .setMaxHeaderSize(maxHeaderSize));
    }

    /**
     * Create a new instance with the specified configuration.
     * @param config The configuration for the request decoder.
     * @see HttpDecoderConfig HttpDecoderConfig API documentation for detailed descriptions of
     * the configuration parameters.
     */
    public HttpRequestDecoder(HttpDecoderConfig config) {
        super(config);
    }

    @Override
    protected HttpMessage createMessage(String[] initialLine) throws Exception {
        return new DefaultHttpRequest(
                // Do strict version checking
                HttpVersion.valueOf(initialLine[2], true),
                HttpMethod.valueOf(initialLine[0]), initialLine[1], headersFactory);
    }

    @Override
    protected AsciiString splitHeaderName(final byte[] sb, final int start, final int length) {
        final byte firstChar = sb[start];
        if (firstChar == 'H') {
            if (length == 4 && isHost(sb, start)) {
                return Host;
            }
        } else if (firstChar == 'A') {
            if (length == 6 && isAccept(sb, start)) {
                return Accept;
            }
        } else if (firstChar == 'C') {
            if (length == 10) {
                if (isConnection(sb, start)) {
                    return Connection;
                }
            } else if (length == 12) {
                if (isContentType(sb, start)) {
                    return ContentType;
                }
            } else if (length == 14) {
                if (isContentLength(sb, start)) {
                    return ContentLength;
                }
            }
        }
        return super.splitHeaderName(sb, start, length);
    }

    private static boolean isAccept(byte[] sb, int start) {
        final long maybeAccept = sb[start] |
                sb[start + 1] << 8 |
                sb[start + 2] << 16 |
                sb[start + 3] << 24 |
                (long) sb[start + 4] << 32 |
                (long) sb[start + 5] << 40;
        return maybeAccept == ACCEPT_AS_LONG;
    }

    private static boolean isHost(byte[] sb, int start) {
        final int maybeHost = sb[start] |
                sb[start + 1] << 8 |
                sb[start + 2] << 16 |
                sb[start + 3] << 24;
        return maybeHost == HOST_AS_INT;
    }

    private static boolean isConnection(byte[] sb, int start) {
        final long maybeConnecti = sb[start] |
                sb[start + 1] << 8 |
                sb[start + 2] << 16 |
                sb[start + 3] << 24 |
                (long) sb[start + 4] << 32 |
                (long) sb[start + 5] << 40 |
                (long) sb[start + 6] << 48 |
                (long) sb[start + 7] << 56;
        if (maybeConnecti != CONNECTION_AS_LONG_0) {
            return false;
        }
        final short maybeOn = (short) (sb[start + 8] | sb[start + 9] << 8);
        return maybeOn == CONNECTION_AS_SHORT_1;
    }

    private static boolean isContentType(byte[] sb, int start) {
        final long maybeContent = sb[start] |
                sb[start + 1] << 8 |
                sb[start + 2] << 16 |
                sb[start + 3] << 24 |
                (long) sb[start + 4] << 32 |
                (long) sb[start + 5] << 40 |
                (long) sb[start + 6] << 48 |
                (long) sb[start + 7] << 56;
        if (maybeContent != CONTENT_AS_LONG) {
            return false;
        }
        final int maybeType = sb[start + 8] |
                sb[start + 9] << 8 |
                sb[start + 10] << 16 |
                sb[start + 11] << 24;
        return maybeType == TYPE_AS_INT;
    }

    private static boolean isContentLength(byte[] sb, int start) {
        final long maybeContent = sb[start] |
                sb[start + 1] << 8 |
                sb[start + 2] << 16 |
                sb[start + 3] << 24 |
                (long) sb[start + 4] << 32 |
                (long) sb[start + 5] << 40 |
                (long) sb[start + 6] << 48 |
                (long) sb[start + 7] << 56;
        if (maybeContent != CONTENT_AS_LONG) {
            return false;
        }
        final long maybeLength = sb[start + 8] |
                sb[start + 9] << 8 |
                sb[start + 10] << 16 |
                sb[start + 11] << 24 |
                (long) sb[start + 12] << 32 |
                (long) sb[start + 13] << 40;
        return maybeLength == LENGTH_AS_LONG;
    }

    private static boolean isGetMethod(final byte[] sb, int start) {
        final int maybeGet = sb[start] |
                sb[start + 1] << 8 |
                sb[start + 2] << 16;
        return maybeGet == GET_AS_INT;
    }

    private static boolean isPostMethod(final byte[] sb, int start) {
        final int maybePost = sb[start] |
                sb[start + 1] << 8 |
                sb[start + 2] << 16 |
                sb[start + 3] << 24;
        return maybePost == POST_AS_INT;
    }

    @Override
    protected String splitFirstWordInitialLine(final byte[] sb, final int start, final int length) {
        if (length == 3) {
            if (isGetMethod(sb, start)) {
                return HttpMethod.GET.name();
            }
        } else if (length == 4) {
            if (isPostMethod(sb, start)) {
                return HttpMethod.POST.name();
            }
        }
        return super.splitFirstWordInitialLine(sb, start, length);
    }

    @Override
    protected String splitThirdWordInitialLine(final byte[] sb, final int start, final int length) {
        if (length == 8) {
            final long maybeHttp1_x = sb[start] |
                    sb[start + 1] << 8 |
                    sb[start + 2] << 16 |
                    sb[start + 3] << 24 |
                    (long) sb[start + 4] << 32 |
                    (long) sb[start + 5] << 40 |
                    (long) sb[start + 6] << 48 |
                    (long) sb[start + 7] << 56;
            if (maybeHttp1_x == HTTP_1_1_AS_LONG) {
                return HttpVersion.HTTP_1_1_STRING;
            } else if (maybeHttp1_x == HTTP_1_0_AS_LONG) {
                return HttpVersion.HTTP_1_0_STRING;
            }
        }
        return super.splitThirdWordInitialLine(sb, start, length);
    }

    @Override
    protected HttpMessage createInvalidMessage(ChannelHandlerContext ctx) {
        return new DefaultFullHttpRequest(HttpVersion.HTTP_1_0, HttpMethod.GET, "/bad-request",
                ctx.bufferAllocator().allocate(0), headersFactory, trailersFactory);
    }

    @Override
    protected boolean isDecodingRequest() {
        return true;
    }

    @Override
    protected boolean isContentAlwaysEmpty(final HttpMessage msg) {
        // fast-path to save expensive O(n) checks; users can override createMessage
        // and extends DefaultHttpRequest making implementing HttpResponse:
        // this is why we cannot use instanceof DefaultHttpRequest here :(
        if (msg.getClass() == DefaultHttpRequest.class) {
            return false;
        }
        return super.isContentAlwaysEmpty(msg);
    }
}
