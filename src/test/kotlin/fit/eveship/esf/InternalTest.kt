package fit.eveship.esf

import fit.eveship.esf.internal.WireWriter
import fit.eveship.esf.internal.compareUtf8
import fit.eveship.esf.internal.formatNumber
import fit.eveship.esf.internal.needsQuotes
import fit.eveship.esf.internal.quotient
import fit.eveship.esf.internal.shortestUnique
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.Arguments.arguments
import org.junit.jupiter.params.provider.MethodSource
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class InternalTest {
    @ParameterizedTest
    @MethodSource("numbers")
    fun formatNumber(
        value: Double,
        text: String,
    ) {
        assertEquals(text, formatNumber(value))
    }

    @ParameterizedTest
    @MethodSource("quotients")
    fun quotient(
        a: Double,
        b: Double,
        quotient: Long,
    ) {
        assertEquals(quotient, quotient(a, b))
    }

    @Test
    fun needsQuotes() {
        assertFalse(needsQuotes("Damage Control II"))
        assertTrue(needsQuotes(""))
        assertTrue(needsQuotes("-x"))
        assertTrue(needsQuotes("%x"))
        assertTrue(needsQuotes("3x Damage Control"))
        assertTrue(needsQuotes("Damage  Control"))
        assertTrue(needsQuotes("Damage @Control"))
    }

    @Test
    fun shortestUnique() {
        assertEquals("Decayed", shortestUnique("Decayed Damage Control", listOf("Gravid Damage Control")))
        assertEquals("A B", shortestUnique("A B", listOf("A B C")))
    }

    @Test
    fun foldIsSimpleCaseFolding() {
        assertEquals("ss", Esf.fold("SS"))
        assertEquals("ß", Esf.fold("ß"))
        assertEquals("İı", Esf.fold("İı"))
        assertEquals("ΐΰﬆ", Esf.fold("ΐΰﬅ"))
        assertEquals("ᎠᏵᎠ", Esf.fold("ᎠᏵꭰ"))
        assertEquals("𐐨", Esf.fold("𐐀"))
    }

    @Test
    fun compareUtf8IsByteOrder() {
        val strings = listOf("a", "ab", "b", "�", "😀", "", "é")
        val byBytes =
            strings.sortedWith { x, y ->
                val a = x.encodeToByteArray()
                val b = y.encodeToByteArray()
                (a zip b).firstOrNull { it.first != it.second }?.let { (p, q) ->
                    (p.toInt() and 0xFF) -
                        (q.toInt() and 0xFF)
                }
                    ?: (a.size - b.size)
            }
        assertEquals(byBytes, strings.sortedWith(::compareUtf8))
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("invalidBinary")
    fun invalidBinary(
        @Suppress("UNUSED_PARAMETER") case: String,
        data: ByteArray,
        message: String,
    ) {
        val e = assertFailsWith<EsfException> { Esf.load(data, catalog) }
        assertContains(e.message!!, message)
    }

    @Test
    fun binarySkipsUnknownFixed64() {
        val fit = Esf.saveBinary(Esf.load("%esf/1\nRifter\n", catalog), catalog)
        val fits = Esf.load(byteArrayOf(0x11) + ByteArray(8) + fit, catalog)
        assertEquals(587, fits[0].hull)
    }

    @Test
    fun binarySkipsUnknownFields() {
        val unknown = bytes(0x13, 0x18, 0x01, 0x23, 0x24, 0x14, 0x2D, 0, 0, 0, 0)
        val fits = Esf.load(unknown + WireWriter().apply { message(1) { uint(1, 1) } }.toByteArray(), catalog)
        assertEquals(listOf(EsfFit()), fits)
    }

    companion object {
        private fun fit(build: WireWriter.() -> Unit): ByteArray =
            WireWriter()
                .apply {
                    message(1) {
                        uint(1, 1)
                        build()
                    }
                }.toByteArray()

        private fun entry(build: WireWriter.() -> Unit): ByteArray = fit { message(5, build) }

        @JvmStatic
        fun numbers(): List<Arguments> =
            listOf(
                arguments(1e22, "10000000000000000000000"),
                arguments(1.5e-7, "0.00000015"),
                arguments(-0.0, "0"),
                arguments(0.1, "0.1"),
                arguments(Math.pow(2.0, -24.0), "0.00000005960464477539063"),
                arguments(0.3, "0.3"),
                arguments(123.456, "123.456"),
                arguments(-2.5, "-2.5"),
                arguments(100.0, "100"),
                arguments(9007199254740992.0, "9007199254740992"),
                arguments(5e-324, "0." + "0".repeat(323) + "5"),
                arguments(1.7976931348623157e308, "17976931348623157" + "0".repeat(292)),
                arguments(2.0e-3, "0.002"),
                arguments(5.0e-1, "0.5"),
                arguments(0.30000000000000004, "0.30000000000000004"),
            )

        @JvmStatic
        fun quotients(): List<Arguments> =
            listOf(
                arguments(0.3, 0.1, 3L),
                arguments(10.0, 0.1, 100L),
                arguments(400.0, 0.0125, 32000L),
                arguments(1.0, 3.0, 0L),
                arguments(1.0, Math.pow(2.0, -24.0), 16777215L),
                arguments(1000.0, 1e5, 0L),
                arguments(1e300, 1e-300, Long.MAX_VALUE),
            )

        private fun bytes(vararg values: Int) = ByteArray(values.size) { values[it].toByte() }

        @JvmStatic
        fun invalidBinary(): List<Arguments> =
            listOf(
                arguments("key-cut-short", bytes(0x0A), "cut short"),
                arguments("varint-cut-short", bytes(0x0A, 0x80), "cut short"),
                arguments("varint-too-long", bytes(0x10, *IntArray(10) { 0xFF }, 0x01), "varint is too long"),
                arguments("key-too-large", bytes(0x80, 0x80, 0x80, 0x80, 0x80, 0x01), "bad key 34359738368"),
                arguments("key-negative", bytes(*IntArray(9) { 0xFF }, 0x01), "bad key 18446744073709551615"),
                arguments("fits-wire-type", bytes(0x08, 0x01), "Document.fits has the wrong wire type"),
                arguments("end-group", bytes(0x14), "unmatched end group"),
                arguments("end-group-mismatch", bytes(0x13, 0x1C), "unmatched end group"),
                arguments("count-too-large", entry { uint(2, 1L shl 32) }, "Entry.count is out of range"),
                arguments("type-too-large", entry { uint(1, 1L shl 31) }, "Entry.type is out of range"),
                arguments("unknown-state", entry { uint(9, 4) }, "unknown State value 4"),
                arguments("unknown-field", entry { uint(11, 1) }, "Entry has a field that is not in the schema"),
                arguments("fit-unknown-field", fit { uint(6, 1) }, "Fit has a field that is not in the schema"),
                arguments(
                    "version",
                    WireWriter().apply { message(1) { uint(1, 2) } }.toByteArray(),
                    "unsupported Fit.version 2",
                ),
                arguments("name-not-utf8", fit { bytes(3, bytes(0xFF)) }, "Fit.name is not valid UTF-8"),
                arguments("doubles-cut-short", entry { bytes(8, bytes(0, 0, 0)) }, "packed doubles cut short"),
                arguments("double-cut-short", entry { bytes(7, bytes(50)) }, "different lengths"),
                arguments(
                    "overridden-twice",
                    entry {
                        packedUints(7, listOf(50, 50))
                        packedDoubles(8, listOf(1.0, 2.0))
                    },
                    "overridden twice",
                ),
            )
    }
}
