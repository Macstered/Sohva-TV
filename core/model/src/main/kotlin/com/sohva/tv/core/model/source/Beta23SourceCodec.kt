package com.sohva.tv.core.model.source

import java.io.ByteArrayInputStream
import java.io.DataInputStream
import java.io.EOFException
import java.io.IOException
import java.util.Base64

/**
 * Reads beta 23's stored source list (spec 10 SRC-FR-108) for the one-time import of plan/04 §17
 * option B. Base64 (standard, padded) of: magic `0x53544D53`, version 1–3, count 0–100, then per
 * source id, name, type, enabled, connection limit, priority, five nullable address and credential
 * strings, scope (version ≥ 2) and EPG offset (version ≥ 3). Integers are big-endian 32-bit,
 * booleans one byte, strings a byte length (0…1 MiB) and UTF-8, nullable strings a presence byte
 * first. Version 1 reads as scope BOTH, versions 1–2 as offset 0. Duplicate ids, unknown names,
 * out-of-range values and trailing bytes are refused: a damaged store is reported, not guessed at.
 * Written new from the spec (AGENTS §3); beta 23's codec tests are the test cases.
 */
object Beta23SourceCodec {
    private const val MAGIC = 0x53544D53
    private const val MAX_STRING_BYTES = 1_048_576

    /** The sources, or [IllegalArgumentException] when [text] is not a valid list. */
    fun decode(text: String): List<SourceConfig> {
        val bytes = try {
            Base64.getDecoder().decode(text)
        } catch (e: IllegalArgumentException) {
            throw IllegalArgumentException("not Base64", e)
        }
        return try {
            DataInputStream(ByteArrayInputStream(bytes)).use { read(it) }
        } catch (e: IOException) {
            throw IllegalArgumentException("truncated source list", e)
        }
    }

    private fun read(input: DataInputStream): List<SourceConfig> {
        require(input.readInt() == MAGIC) { "not a source list" }
        val version = input.readInt()
        require(version in 1..3) { "unknown version $version" }
        val count = input.readInt()
        require(count in 0..SourceRules.MAX_SOURCES) { "count $count" }
        val sources = ArrayList<SourceConfig>(count)
        repeat(count) {
            val id = input.string()
            require(SourceRules.isValidId(id)) { "bad id" }
            val name = input.string()
            val storedType = input.string()
            val type = SourceType.entries.firstOrNull { it.name == storedType } ?: throw IllegalArgumentException("unknown type")
            val enabled = input.readBoolean()
            val limit = input.readInt()
            val priority = input.readInt()
            val secrets = SourceSecrets(input.nullable(), input.nullable(), input.nullable(), input.nullable(), input.nullable())
            val scope = if (version >= 2) {
                val stored = input.string()
                ImportScope.entries.firstOrNull { it.name == stored } ?: throw IllegalArgumentException("unknown scope")
            } else {
                ImportScope.BOTH
            }
            val offset = if (version >= 3) input.readInt() else 0
            require(limit in SourceRules.CONNECTION_LIMITS) { "limit $limit" }
            require(offset in SourceRules.EPG_OFFSETS && offset % SourceRules.EPG_OFFSET_STEP == 0) { "offset $offset" }
            sources += SourceConfig(Source(id, name, type, enabled, limit, priority, scope, offset), secrets)
        }
        require(input.read() == -1) { "trailing bytes" }
        require(sources.map { it.source.id }.toSet().size == sources.size) { "duplicate ids" }
        return sources
    }

    private fun DataInputStream.string(): String {
        val length = readInt()
        require(length in 0..MAX_STRING_BYTES) { "string length $length" }
        val bytes = ByteArray(length)
        try {
            readFully(bytes)
        } catch (e: EOFException) {
            throw IllegalArgumentException("truncated string", e)
        }
        return String(bytes, Charsets.UTF_8)
    }

    private fun DataInputStream.nullable(): String? = if (readBoolean()) string() else null
}
