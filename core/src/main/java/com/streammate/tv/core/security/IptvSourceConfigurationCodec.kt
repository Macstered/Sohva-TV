package com.streammate.tv.core.security

import com.streammate.tv.core.model.IptvSourceConfiguration
import com.streammate.tv.core.model.IptvImportScope
import com.streammate.tv.core.model.IptvSourceType
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.nio.charset.StandardCharsets
import java.util.Base64

object IptvSourceConfigurationCodec {
    fun encode(sources: List<IptvSourceConfiguration>): String {
        require(sources.size <= MAX_SOURCES) { "Too many IPTV sources" }
        require(sources.map { it.id }.distinct().size == sources.size) { "Duplicate IPTV source ID" }
        val output = ByteArrayOutputStream()
        DataOutputStream(output).use { data ->
            data.writeInt(MAGIC)
            data.writeInt(VERSION)
            data.writeInt(sources.size)
            sources.forEach { source ->
                data.writeString(source.id)
                data.writeString(source.name)
                data.writeString(source.type.name)
                data.writeBoolean(source.enabled)
                data.writeInt(source.connectionLimit)
                data.writeInt(source.priority)
                data.writeNullableString(source.m3uUrl)
                data.writeNullableString(source.xmlTvUrl)
                data.writeNullableString(source.xtreamBaseUrl)
                data.writeNullableString(source.xtreamUsername)
                data.writeNullableString(source.xtreamPassword)
                data.writeString(source.importScope.name)
                data.writeInt(source.epgOffsetMinutes)
            }
        }
        return Base64.getEncoder().encodeToString(output.toByteArray())
    }

    fun decode(encoded: String): List<IptvSourceConfiguration> {
        val bytes = runCatching { Base64.getDecoder().decode(encoded) }
            .getOrElse { throw IllegalArgumentException("Invalid IPTV source payload", it) }
        return DataInputStream(ByteArrayInputStream(bytes)).use { data ->
            require(data.readInt() == MAGIC) { "Invalid IPTV source payload" }
            val version = data.readInt()
            require(version in LEGACY_VERSION..VERSION) { "Unsupported IPTV source payload version" }
            val sourceCount = data.readInt()
            require(sourceCount in 0..MAX_SOURCES) { "Invalid IPTV source count" }
            buildList(sourceCount) {
                repeat(sourceCount) {
                    val id = data.readString()
                    val name = data.readString()
                    val type = runCatching { IptvSourceType.valueOf(data.readString()) }
                        .getOrElse { throw IllegalArgumentException("Unknown IPTV source type", it) }
                    val enabled = data.readBoolean()
                    val connectionLimit = data.readInt()
                    val priority = data.readInt()
                    val m3uUrl = data.readNullableString()
                    val xmlTvUrl = data.readNullableString()
                    val xtreamBaseUrl = data.readNullableString()
                    val xtreamUsername = data.readNullableString()
                    val xtreamPassword = data.readNullableString()
                    val importScope = if (version >= VERSION_WITH_IMPORT_SCOPE) {
                        runCatching { IptvImportScope.valueOf(data.readString()) }
                            .getOrElse {
                                throw IllegalArgumentException("Unknown IPTV import scope", it)
                            }
                    } else {
                        IptvImportScope.BOTH
                    }
                    val epgOffsetMinutes = if (version >= VERSION_WITH_EPG_OFFSET) {
                        data.readInt()
                    } else {
                        IptvSourceConfiguration.DEFAULT_EPG_OFFSET_MINUTES
                    }
                    add(
                        IptvSourceConfiguration(
                            id = id,
                            name = name,
                            type = type,
                            enabled = enabled,
                            connectionLimit = connectionLimit,
                            priority = priority,
                            importScope = importScope,
                            epgOffsetMinutes = epgOffsetMinutes,
                            m3uUrl = m3uUrl,
                            xmlTvUrl = xmlTvUrl,
                            xtreamBaseUrl = xtreamBaseUrl,
                            xtreamUsername = xtreamUsername,
                            xtreamPassword = xtreamPassword,
                        ),
                    )
                }
            }.also { sources ->
                require(sources.map { it.id }.distinct().size == sources.size) {
                    "Duplicate IPTV source ID"
                }
                require(data.read() == -1) { "Trailing IPTV source payload data" }
            }
        }
    }

    private fun DataOutputStream.writeString(value: String) {
        val bytes = value.toByteArray(StandardCharsets.UTF_8)
        require(bytes.size <= MAX_STRING_BYTES) { "IPTV source value is too long" }
        writeInt(bytes.size)
        write(bytes)
    }

    private fun DataOutputStream.writeNullableString(value: String?) {
        writeBoolean(value != null)
        if (value != null) writeString(value)
    }

    private fun DataInputStream.readString(): String {
        val size = readInt()
        require(size in 0..MAX_STRING_BYTES) { "Invalid IPTV source value length" }
        val bytes = ByteArray(size)
        readFully(bytes)
        return String(bytes, StandardCharsets.UTF_8)
    }

    private fun DataInputStream.readNullableString(): String? =
        if (readBoolean()) readString() else null

    private const val MAGIC = 0x53544D53
    private const val LEGACY_VERSION = 1
    private const val VERSION_WITH_IMPORT_SCOPE = 2
    private const val VERSION_WITH_EPG_OFFSET = 3
    private const val VERSION = VERSION_WITH_EPG_OFFSET
    private const val MAX_SOURCES = 100
    private const val MAX_STRING_BYTES = 1_048_576
}
