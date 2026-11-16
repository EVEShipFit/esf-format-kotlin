package fit.eveship.esf

import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.Arguments.arguments
import org.junit.jupiter.params.provider.MethodSource
import org.junit.jupiter.params.provider.ValueSource
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class EsiTest {
    @ParameterizedTest(name = "{0}")
    @MethodSource("slots")
    fun slot(
        flag: String,
        location: Location,
        slot: Long,
    ) {
        assertEquals(location, Esi.location(flag))
        assertEquals(slot, Esi.slot(flag))
    }

    @Test
    fun everyHold() {
        val flags =
            listOf(
                "Cargo",
                "DroneBay",
                "SpecializedAmmoHold",
                "BoosterBay",
                "SpecializedCommandCenterHold",
                "CorpseBay",
                "MobileDepotHold",
                "ExpeditionHold",
                "FleetHangar",
                "FrigateEscapeBay",
                "SpecializedFuelBay",
                "SpecializedGasHold",
                "SpecializedIceHold",
                "InfrastructureHangar",
                "ShipHangar",
                "SpecializedMineralHold",
                "SpecializedOreHold",
                "MoonMaterialBay",
                "SpecializedPlanetaryCommoditiesHold",
                "QuafeBay",
                "SubSystemBay",
            )
        val racks =
            setOf(Location.HIGH, Location.MID, Location.LOW, Location.RIG, Location.SUB, Location.SVC, Location.TUBE)
        assertEquals(Location.entries.filter { it !in racks }, flags.map(Esi::location))
        assertTrue(flags.all { Esi.slot(it) == null })
        assertEquals(Location.BAY, Esi.location("FighterBay"))
    }

    @ParameterizedTest
    @ValueSource(
        strings = ["Hangar", "AutoFit", "HiSlot8", "FighterTube5", "hislot0", "HiSlot01", "HiSlot0\n", "toString", ""],
    )
    fun unknown(flag: String) {
        assertNull(Esi.location(flag))
        assertNull(Esi.slot(flag))
    }

    companion object {
        @JvmStatic
        fun slots(): List<Arguments> =
            listOf(
                arguments("HiSlot0", Location.HIGH, 1L),
                arguments("MedSlot7", Location.MID, 8L),
                arguments("LoSlot2", Location.LOW, 3L),
                arguments("RigSlot1", Location.RIG, 2L),
                arguments("SubSystemSlot3", Location.SUB, 4L),
                arguments("ServiceSlot5", Location.SVC, 6L),
                arguments("FighterTube4", Location.TUBE, 5L),
            )
    }
}
