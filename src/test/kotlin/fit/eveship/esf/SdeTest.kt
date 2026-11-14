package fit.eveship.esf

import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.Arguments.arguments
import org.junit.jupiter.params.provider.MethodSource
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

private data class Named(
    val id: Int,
    val name: String,
    val published: Boolean,
)

class SdeTest {
    @ParameterizedTest(name = "{0}")
    @MethodSource("kinds")
    fun kind(
        name: String,
        categoryId: Int,
        groupId: Int,
        effectIds: List<Int>,
        expected: Kind,
    ) {
        assertEquals(expected, Sde.kind(categoryId, groupId, effectIds))
    }

    @Test
    fun activeNeedsAnActiveOrTargetEffect() {
        assertFalse(Sde.active(emptyMap()))
        assertFalse(Sde.active(mapOf(16 to 1, 2302 to 0)))
        assertTrue(Sde.active(mapOf(16 to 1, 4 to 1)))
        assertTrue(Sde.active(mapOf(16 to 1, 3174 to 2)))
    }

    @Test
    fun squadronSize() {
        assertEquals(6, Sde.squadronSize(mapOf(9 to 100.0, 2215 to 6.0)))
        assertNull(Sde.squadronSize(mapOf(9 to 240.0)))
    }

    @Test
    fun modeHullMatchesTheLongestShipName() {
        val ships = mapOf("hecate" to 35683, "hecate navy" to 1)
        assertEquals(35683, Sde.modeHull("Hecate Sharpshooter Mode", ships))
        assertEquals(1, Sde.modeHull("Hecate Navy Defense Mode", ships))
        assertNull(Sde.modeHull("Hecate", ships))
        assertNull(Sde.modeHull("Rifter Defense Mode", ships))
    }

    @Test
    fun nameIndexFoldsNamesAndPicksPublishedThenLowestId() {
        val index =
            Sde.nameIndex(
                listOf(
                    Named(50097, "Avatar", false),
                    Named(11567, "Avatar", true),
                    Named(30, "Twin", false),
                    Named(20, "Twin", false),
                    Named(587, "Rifter", true),
                ),
                Named::id,
                Named::name,
                Named::published,
            )
        assertEquals(mapOf("avatar" to 11567, "twin" to 20, "rifter" to 587), index)
    }

    companion object {
        @JvmStatic
        fun kinds(): List<Arguments> =
            listOf(
                arguments("Rifter", 6, 25, listOf(5779), Kind.HULL),
                arguments("Astrahus", 65, 1657, listOf(6565), Kind.HULL),
                arguments("Small Secure Container", 2, 340, listOf(649), Kind.CONTAINER),
                arguments("Hecate Sharpshooter Mode", 7, 1306, listOf(6010), Kind.MODE),
                arguments("Tengu Defensive - Covert Reconfiguration", 32, 954, listOf(896), Kind.SUB),
                arguments("200mm AutoCannon II", 7, 55, listOf(16, 12, 34), Kind.HIGH),
                arguments("Medium Shield Extender II", 7, 38, listOf(13, 16), Kind.MID),
                arguments("Damage Control II", 7, 60, listOf(11, 16), Kind.LOW),
                arguments("Standup Ballistic Control System I", 66, 1429, listOf(11, 16), Kind.LOW),
                arguments("Small Core Defense Field Extender I", 7, 774, listOf(446, 2663), Kind.RIG),
                arguments("Standup Cloning Center I", 66, 1321, listOf(16, 6306), Kind.SVC),
                arguments("module without a slot", 7, 1, listOf(16), Kind.OTHER),
                arguments("Hobgoblin II", 18, 100, listOf(10), Kind.DRONE),
                arguments("Templar II", 87, 1652, listOf(6431), Kind.FIGHTER),
                arguments("Republic Fleet EMP S", 8, 83, listOf(596), Kind.CHARGE),
                arguments("Genolution Core Augmentation CA-1", 20, 1730, listOf(302), Kind.IMPLANT),
                arguments("Strong Blue Pill Booster", 20, 303, listOf(2737), Kind.BOOSTER),
                arguments("Veldspar", 25, 462, emptyList<Int>(), Kind.OTHER),
            )
    }
}
