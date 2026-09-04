package com.streammate.tv.iptv.xmltv

import java.io.InputStream
import java.io.PushbackInputStream
import java.util.zip.GZIPInputStream

object CompressionAwareInputStream {
    fun wrap(input: InputStream): InputStream {
        val pushback = PushbackInputStream(input, 2)
        val signature = ByteArray(2)
        val bytesRead = pushback.read(signature)
        if (bytesRead > 0) pushback.unread(signature, 0, bytesRead)
        val isGzip = bytesRead == 2 &&
            signature[0].toInt() and 0xff == 0x1f &&
            signature[1].toInt() and 0xff == 0x8b
        return if (isGzip) GZIPInputStream(pushback) else pushback
    }
}
