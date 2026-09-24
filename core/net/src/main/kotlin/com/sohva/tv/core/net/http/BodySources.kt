package com.sohva.tv.core.net.http

import java.io.IOException
import okio.Buffer
import okio.BufferedSource
import okio.ForwardingSource
import okio.GzipSource
import okio.Source
import okio.buffer

/** A body passed its byte limit; [ProviderHttp] turns it into the viewer's message. */
internal class BodyTooLargeException : IOException("body over the limit")

/** Counts the bytes read through it and fails past [limit]; a runaway body stops, never fills memory. */
internal class CappedSource(delegate: Source, private val limit: Long) : ForwardingSource(delegate) {
    private var total = 0L

    override fun read(sink: Buffer, byteCount: Long): Long {
        val read = super.read(sink, byteCount)
        if (read > 0) {
            total += read
            if (total > limit) throw BodyTooLargeException()
        }
        return read
    }
}

object BodySources {
    private const val GZIP_MAGIC_1 = 0x1F.toByte()
    private const val GZIP_MAGIC_2 = 0x8B.toByte()

    /**
     * [source], or its gzip-decompressed form when it starts with `1F 8B` (a `.gz` file served as
     * is, SRC-FR-47). The decompressed bytes have their own [limit], so a small bomb cannot expand
     * without bound.
     */
    fun decompressed(source: BufferedSource, limit: Long): BufferedSource {
        if (!source.request(2)) return source
        val buffer = source.buffer
        if (buffer[0] != GZIP_MAGIC_1 || buffer[1] != GZIP_MAGIC_2) return source
        return CappedSource(GzipSource(source), limit).buffer()
    }
}
