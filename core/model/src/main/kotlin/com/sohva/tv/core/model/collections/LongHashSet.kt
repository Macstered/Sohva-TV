package com.sohva.tv.core.model.collections

/**
 * An open-addressing set of `Long`s without boxing: the keys an import has seen, as 64-bit hashes
 * (spec 10 SRC-L-04). About 16 bytes per key at the 0.5 load factor, so 200,000 films cost ~3 MB
 * instead of ~20 MB of Strings. Grows by doubling; never shrinks (it lives for one import).
 */
class LongHashSet(expected: Int = 1_024) {
    private var keys = LongArray(capacityFor(expected))
    private var used = BooleanArray(keys.size)
    private var zeroPresent = false

    var size: Int = 0
        private set

    /** True when [value] was not in the set. */
    fun add(value: Long): Boolean {
        if (value == 0L) {
            if (zeroPresent) return false
            zeroPresent = true
            size++
            return true
        }
        if ((size + 1) * 2 > keys.size) grow()
        return insert(value)
    }

    operator fun contains(value: Long): Boolean {
        if (value == 0L) return zeroPresent
        val mask = keys.size - 1
        var i = mix(value) and mask
        while (used[i]) {
            if (keys[i] == value) return true
            i = (i + 1) and mask
        }
        return false
    }

    private fun insert(value: Long): Boolean {
        val mask = keys.size - 1
        var i = mix(value) and mask
        while (used[i]) {
            if (keys[i] == value) return false
            i = (i + 1) and mask
        }
        used[i] = true
        keys[i] = value
        size++
        return true
    }

    private fun grow() {
        val oldKeys = keys
        val oldUsed = used
        keys = LongArray(oldKeys.size * 2)
        used = BooleanArray(keys.size)
        size = if (zeroPresent) 1 else 0
        for (i in oldKeys.indices) if (oldUsed[i]) insert(oldKeys[i])
    }

    private companion object {
        fun capacityFor(expected: Int): Int = Integer.highestOneBit(maxOf(16, expected * 2 - 1)) shl 1

        // Keys are already hashes; one multiply spreads sequential or low-entropy ones.
        fun mix(value: Long): Int {
            val h = value * -0x61c8864680b583ebL
            return (h xor (h ushr 32)).toInt()
        }
    }
}
