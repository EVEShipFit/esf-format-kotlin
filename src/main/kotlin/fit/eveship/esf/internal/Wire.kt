package fit.eveship.esf.internal

import fit.eveship.esf.EsfException
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

// The Protocol Buffers wire format.

internal const val VARINT = 0
internal const val I64 = 1
internal const val LEN = 2
internal const val SGROUP = 3
internal const val EGROUP = 4
internal const val I32 = 5

internal fun tag(
    field: Int,
    wire: Int,
): Long = field.toLong() shl 3 or wire.toLong()

internal class WireWriter {
    private val out = ByteArrayOutputStream()

    fun toByteArray(): ByteArray = out.toByteArray()

    private fun varint(value: Long) {
        var rest = value
        while (rest > 0x7F) {
            out.write((rest and 0x7F or 0x80).toInt())
            rest = rest ushr 7
        }
        out.write(rest.toInt())
    }

    fun uint(
        field: Int,
        value: Long,
    ) {
        varint(tag(field, VARINT))
        varint(value)
    }

    fun bytes(
        field: Int,
        payload: ByteArray,
    ) {
        varint(tag(field, LEN))
        varint(payload.size.toLong())
        out.write(payload)
    }

    fun string(
        field: Int,
        value: String,
    ) = bytes(field, value.encodeToByteArray())

    fun message(
        field: Int,
        build: WireWriter.() -> Unit,
    ) = bytes(field, WireWriter().apply(build).toByteArray())

    fun packedUints(
        field: Int,
        values: Collection<Int>,
    ) = message(field) { values.forEach { varint(it.toLong()) } }

    fun packedDoubles(
        field: Int,
        values: Collection<Double>,
    ) {
        val buffer = ByteBuffer.allocate(values.size * 8).order(ByteOrder.LITTLE_ENDIAN)
        values.forEach { buffer.putDouble(it) }
        bytes(field, buffer.array())
    }
}

/** A reader of Protocol Buffers records in data[start, end). */
internal class WireReader(
    private val data: ByteArray,
    private var pos: Int = 0,
    private val end: Int = data.size,
) {
    fun done(): Boolean = pos >= end

    private fun take(n: Long): WireReader {
        if (n < 0 || n > end - pos) {
            throw EsfException("binary: not valid Protocol Buffers (cut short)")
        }
        val start = pos
        pos += n.toInt()
        return WireReader(data, start, pos)
    }

    /** A varint, as the signed 64-bit integer with the same bits. */
    fun varint(): Long {
        var value = 0L
        for (shift in 0 until 64 step 7) {
            if (pos >= end) {
                throw EsfException("binary: not valid Protocol Buffers (cut short)")
            }
            val byte = data[pos++].toInt() and 0xFF
            value = value or ((byte and 0x7F).toLong() shl shift)
            if (byte < 0x80) {
                return value
            }
        }
        throw EsfException("binary: not valid Protocol Buffers (varint is too long)")
    }

    /** A record's key: its field number and wire type, as [tag] makes them. */
    fun key(): Long {
        val key = varint()
        if (key ushr 3 == 0L || key !in 0..UINT32_MAX || key and 7 > I32) {
            throw EsfException("binary: not valid Protocol Buffers (bad key ${key.toULong()})")
        }
        return key
    }

    fun message(): WireReader = take(varint())

    /** A string; null where it is not valid UTF-8. */
    fun string(): String? {
        val payload = message()
        return decodeUtf8(data, payload.pos, payload.end - payload.pos)
    }

    fun double(): Double = take(8).doubles().single()

    fun uints(): List<Long> {
        val out = mutableListOf<Long>()
        while (!done()) {
            out.add(varint())
        }
        return out
    }

    fun doubles(): List<Double> {
        if ((end - pos) % 8 != 0) {
            throw EsfException("binary: not valid Protocol Buffers (packed doubles cut short)")
        }
        val buffer = ByteBuffer.wrap(data, pos, end - pos).order(ByteOrder.LITTLE_ENDIAN)
        pos = end
        return List(buffer.remaining() / 8) { buffer.getDouble() }
    }

    /** Step over the value of a record that is not read. */
    fun skip(key: Long) {
        when ((key and 7).toInt()) {
            VARINT -> varint()
            I64 -> take(8)
            I32 -> take(4)
            LEN -> take(varint())
            SGROUP -> skipGroup(key ushr 3)
            else -> throw EsfException("binary: not valid Protocol Buffers (unmatched end group)")
        }
    }

    private fun skipGroup(field: Long) {
        val open = ArrayDeque(listOf(field))
        while (open.isNotEmpty()) {
            val key = key()
            when ((key and 7).toInt()) {
                SGROUP -> {
                    open.addLast(key ushr 3)
                }

                EGROUP -> {
                    if (open.removeLast() != key ushr 3) {
                        throw EsfException("binary: not valid Protocol Buffers (unmatched end group)")
                    }
                }

                else -> {
                    skip(key)
                }
            }
        }
    }
}
