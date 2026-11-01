package fit.eveship.esf

import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.Arguments.arguments
import org.junit.jupiter.params.provider.MethodSource
import org.junit.jupiter.params.provider.ValueSource
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

private const val RIFTER = 587
private const val AUTOCANNON = 2889
private const val EMP = 21898
private const val SHIELD_EXTENDER = 3831
private const val SCRAMBLER = 448
private const val DAMAGE_CONTROL = 2048
private const val HOBGOBLIN = 2456
private const val DECAYED_DAMAGE_CONTROL_MUTAPLASMID = 52224
private const val AVATAR = 11567
private const val AVATAR_UNPUBLISHED = 50097
private const val SMALL_SECURE_CONTAINER = 3467

private fun fit(vararg entries: Entry) = EsfFit(RIFTER, entries = entries.toList())

private fun named(
    name: String,
    vararg entries: Entry,
) = EsfFit(RIFTER, name, entries = entries.toList())

class ApiTest {
    @Test
    fun saveCanonical() {
        val rifter =
            named(
                "Shield Buffer",
                Entry(DAMAGE_CONTROL),
                Entry(AUTOCANNON, charge = EMP),
                Entry(SHIELD_EXTENDER),
                Entry(AUTOCANNON, charge = EMP),
                Entry(null, location = Location.HIGH),
                Entry(SCRAMBLER, state = State.OFF),
                Entry(HOBGOBLIN, count = 2),
                Entry(EMP, count = 100, location = Location.CARGO),
            )
        assertEquals(
            """
            %esf/1
            Rifter "Shield Buffer"

            2x 200mm AutoCannon II :Republic Fleet EMP S

            Medium Shield Extender II
            Warp Scrambler II !off

            Damage Control II

            2x Hobgoblin II

            100x Republic Fleet EMP S

            """.trimIndent(),
            Esf.save(listOf(rifter), catalog),
        )
    }

    @Test
    fun loadCanonical() {
        val rifter = fit(Entry(AUTOCANNON), Entry(AUTOCANNON), Entry(EMP, location = Location.CARGO))
        assertEquals(
            listOf(fit(Entry(AUTOCANNON, count = 2), Entry(EMP))),
            Esf.load(Esf.save(listOf(rifter), catalog), catalog),
        )
    }

    @Test
    fun loadText() {
        assertEquals(listOf(EsfFit(RIFTER)), Esf.load("%esf/1\nRifter\n", catalog))
    }

    @Test
    fun mutaplasmidRolls() {
        val entry =
            Entry(DAMAGE_CONTROL, mutaplasmid = DECAYED_DAMAGE_CONTROL_MUTAPLASMID, overrides = mapOf(50 to 31.5))
        val (loaded) = Esf.load(Esf.save(listOf(fit(entry)), catalog), catalog)
        assertEquals(
            listOf(50 to 31.5, 974 to 0.6, 975 to 0.6, 976 to 0.6, 977 to 0.6),
            loaded.entries[0].overrides.toList(),
        )
    }

    @Test
    fun binaryRoundTrip() {
        val fits =
            listOf(
                fit(Entry(SMALL_SECURE_CONTAINER, fitName = "Loot", location = Location.CARGO)),
                EsfFit(SMALL_SECURE_CONTAINER, "Loot", entries = listOf(Entry(EMP, count = 10))),
            )
        assertEquals(Esf.load(Esf.save(fits, catalog), catalog), Esf.load(Esf.saveBinary(fits, catalog), catalog))
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("invalidFits")
    fun saveInvalid(
        @Suppress("UNUSED_PARAMETER") case: String,
        fits: List<EsfFit>,
        message: String,
    ) {
        val e = assertFailsWith<EsfException> { Esf.save(fits, catalog) }
        assertContains(e.message!!, message)
    }

    @Test
    fun errorLocation() {
        val e =
            assertFailsWith<EsfException> {
                Esf.save(
                    listOf(fit(), fit(Entry(AUTOCANNON), Entry(999_999_999))),
                    catalog,
                )
            }
        assertEquals(listOf(2, 2, null), listOf(e.fitNumber, e.entryNumber, e.lineNumber))
        assertTrue(e.message!!.startsWith("fit 2, entry 2: "))
    }

    @Test
    fun errorLocationOfAFit() {
        val e = assertFailsWith<EsfException> { Esf.save(listOf(fit(), EsfFit(RIFTER, mode = 999_999_999)), catalog) }
        assertEquals(listOf(2, null), listOf(e.fitNumber, e.entryNumber))
        assertTrue(e.message!!.startsWith("fit 2: "))
    }

    @Test
    fun errorLocationOfALine() {
        val e = assertFailsWith<EsfException> { Esf.load("%esf/1\nRifter\nNot A Type\n", catalog) }
        assertEquals(3, e.lineNumber)
        assertEquals("line 3: unknown type 'Not A Type'", e.message)
    }

    @Test
    fun emptyNameIsNoName() {
        assertEquals("%esf/1\nRifter\n", Esf.save(listOf(named("")), catalog))
    }

    @Test
    fun nameZeroIsAName() {
        assertEquals("%esf/1\nRifter \"0\"\n", Esf.save(listOf(named("0")), catalog))
    }

    @Test
    fun sharedNamePublishedWins() {
        val text = Esf.save(listOf(fit(Entry(AVATAR, location = Location.CARGO))), catalog)
        assertTrue(text.endsWith("Avatar\n"))
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("invalidDocuments")
    fun loadInvalid(
        @Suppress("UNUSED_PARAMETER") case: String,
        data: ByteArray,
        message: String,
    ) {
        val e = assertFailsWith<EsfException> { Esf.load(data, catalog) }
        assertContains(e.message!!, message)
    }

    @Test
    fun loadStringWithLoneSurrogate() {
        val e = assertFailsWith<EsfException> { Esf.load("%esf/1\n\nRifter\uD800\n", catalog) }
        assertEquals("line 3: not valid UTF-8", e.message)
    }

    @ParameterizedTest
    @ValueSource(strings = ["Hecate /Defense", "Rifter\nDamage Control II +Decayed"])
    fun catalogWithoutOptionalLookups(line: String) {
        val minimal =
            object : Catalog {
                override fun type(folded: String) = catalog.type(folded)

                override fun typeById(typeId: Int) = catalog.typeById(typeId)

                override fun attribute(folded: String) = catalog.attribute(folded)

                override fun attributeById(attributeId: Int) = catalog.attributeById(attributeId)
            }
        val e = assertFailsWith<EsfException> { Esf.load("%esf/1\n$line\n", minimal) }
        assertContains(e.message!!, Regex("no tactical modes|no mutaplasmid"))
    }

    @Test
    fun catalogMissingRollAttribute() {
        val broken =
            object : Catalog by catalog {
                override fun attributeById(attributeId: Int): AttributeRecord? = null
            }
        val e =
            assertFailsWith<IllegalStateException> { Esf.load("%esf/1\nRifter\nDamage Control II +Decayed\n", broken) }
        assertContains(e.message!!, "no attribute")
    }

    @Test
    fun base64Url() {
        val data = Esf.saveBinary(listOf(fit(Entry(AUTOCANNON))), catalog)
        val url = Esf.toBase64Url(data)
        assertFalse('=' in url)
        assertContentEquals(data, Esf.fromBase64Url(url))
        assertContentEquals(byteArrayOf(0xFB.toByte(), 0xFF.toByte()), Esf.fromBase64Url(" -_8\n"))
    }

    @ParameterizedTest
    @ValueSource(strings = ["not base64url!", "AA==", "AAAAA"])
    fun invalidBase64Url(encoded: String) {
        assertFailsWith<EsfException> { Esf.fromBase64Url(encoded) }
    }

    @Test
    fun isText() {
        assertTrue(Esf.isText("%esf/1\n".encodeToByteArray()))
        assertTrue(Esf.isText(" %esf/1\n".encodeToByteArray()))
        assertFalse(Esf.isText(byteArrayOf(0x0A, 0x02)))
        assertFalse(Esf.isText(byteArrayOf()))
    }

    @Test
    fun fold() {
        assertEquals("damage control ii", Esf.fold("Damage Control II"))
        assertEquals("ärger σσ", Esf.fold("ÄRGER Σς"))
    }

    companion object {
        @JvmStatic
        fun invalidFits(): List<Arguments> =
            listOf(
                arguments("no-fits", listOf<EsfFit>(), "at least one fit"),
                arguments("empty-no-rack", listOf(fit(Entry(null))), "an empty slot must name its rack"),
                arguments("negative-count", listOf(fit(Entry(AUTOCANNON, count = -1))), "at least 1"),
                arguments(
                    "zero-charge-count",
                    listOf(fit(Entry(AUTOCANNON, charge = EMP, chargeCount = 0))),
                    "at least 1",
                ),
                arguments("unknown-type", listOf(fit(Entry(999_999_999))), "unknown item type ID"),
                arguments("unknown-hull", listOf(EsfFit(999_999_999)), "unknown hull type ID"),
                arguments(
                    "unknown-attribute",
                    listOf(fit(Entry(DAMAGE_CONTROL, overrides = mapOf(999_999 to 1.0)))),
                    "unknown attribute ID",
                ),
                arguments(
                    "rack-on-item",
                    listOf(fit(Entry(AUTOCANNON, location = Location.HIGH))),
                    "only for an empty slot",
                ),
                arguments("charge-count-alone", listOf(fit(Entry(AUTOCANNON, chargeCount = 10))), "without a charge"),
                arguments(
                    "override-nan",
                    listOf(fit(Entry(DAMAGE_CONTROL, overrides = mapOf(50 to Double.NaN)))),
                    "not finite",
                ),
                arguments(
                    "override-infinite",
                    listOf(fit(Entry(DAMAGE_CONTROL, overrides = mapOf(50 to Double.POSITIVE_INFINITY)))),
                    "not finite",
                ),
                arguments(
                    "id-shadowed",
                    listOf(fit(Entry(AVATAR_UNPUBLISHED, location = Location.CARGO))),
                    "not what its name resolves to",
                ),
                arguments(
                    "override-ids-shadowed",
                    listOf(fit(Entry(DAMAGE_CONTROL, overrides = mapOf(2795 to 1.0)))),
                    "resolve to",
                ),
                arguments(
                    "contains-itself",
                    listOf(named("Buffer", Entry(RIFTER, fitName = "Buffer"))),
                    "cannot contain itself",
                ),
                arguments("name-newline", listOf(named("Two\nlines")), "CR or LF"),
                arguments("name-not-utf8", listOf(named("\uDC00")), "not valid UTF-8"),
            )

        @JvmStatic
        fun invalidDocuments(): List<Arguments> =
            listOf(
                arguments("empty", byteArrayOf(), "the document is empty"),
                arguments(
                    "lone-surrogate",
                    "%esf/1\nRifter".encodeToByteArray() +
                        byteArrayOf(0xED.toByte(), 0xA0.toByte(), 0x80.toByte(), 0x0A),
                    "line 2: not valid UTF-8",
                ),
                arguments("binary-without-fits", byteArrayOf(0x10, 0x01), "at least one fit"),
                arguments(
                    "merged-count-too-large",
                    "%esf/1\nRifter\n${"4294967295x Damage Control II\n".repeat(2)}".encodeToByteArray(),
                    "too large",
                ),
            )
    }
}
