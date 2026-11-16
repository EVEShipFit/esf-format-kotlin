package fit.eveship.esf

/**
 * Helpers to turn an ESI `location_flag` into an [Entry.location] and [Entry.slot].
 *
 * ESI gives a `location_flag` for each item in a ship, in the assets and fittings
 * endpoints. Use both helpers on the same flag:
 *
 * ```
 * Entry(typeId, location = Esi.location(flag) ?: Location.CARGO, slot = Esi.slot(flag))
 * ```
 *
 * Only the flags of things inside a ship are known. For any other flag, like `Hangar`,
 * both return null. The example above puts those items in the cargo hold.
 */
public object Esi {
    private val racks =
        listOf(
            Triple("HiSlot", Location.HIGH, 8),
            Triple("MedSlot", Location.MID, 8),
            Triple("LoSlot", Location.LOW, 8),
            Triple("RigSlot", Location.RIG, 8),
            Triple("SubSystemSlot", Location.SUB, 8),
            Triple("ServiceSlot", Location.SVC, 8),
            Triple("FighterTube", Location.TUBE, 5),
        )

    private val slots: Map<String, Pair<Location, Long>> =
        buildMap {
            for ((prefix, location, size) in racks) repeat(size) { put("$prefix$it", location to it + 1L) }
        }

    private val holds =
        mapOf(
            "Cargo" to Location.CARGO,
            "DroneBay" to Location.BAY,
            "FighterBay" to Location.BAY,
            "SpecializedAmmoHold" to Location.AMMO,
            "BoosterBay" to Location.BOOSTER,
            "SpecializedCommandCenterHold" to Location.COMMAND,
            "CorpseBay" to Location.CORPSE,
            "MobileDepotHold" to Location.DEPOT,
            "ExpeditionHold" to Location.EXPEDITION,
            "FleetHangar" to Location.FLEET,
            "FrigateEscapeBay" to Location.FRIGATE,
            "SpecializedFuelBay" to Location.FUEL,
            "SpecializedGasHold" to Location.GAS,
            "SpecializedIceHold" to Location.ICE,
            "InfrastructureHangar" to Location.INFRASTRUCTURE,
            "ShipHangar" to Location.MAINTENANCE,
            "SpecializedMineralHold" to Location.MINERAL,
            "SpecializedOreHold" to Location.MINING,
            "MoonMaterialBay" to Location.MOON,
            "SpecializedPlanetaryCommoditiesHold" to Location.PLANETARY,
            "QuafeBay" to Location.QUAFE,
            "SubSystemBay" to Location.SUBSYSTEM,
        )

    /**
     * Gives the [Location] for an ESI `location_flag`.
     *
     * A slot flag gives its rack: `HiSlot2` gives [Location.HIGH], and `FighterTube0`
     * gives [Location.TUBE]. `DroneBay` and `FighterBay` give [Location.BAY]. In a fitting
     * from ESI, `DroneBay` also holds the drones that are out in space. Leave [Entry.location]
     * null for those.
     *
     * Returns null for a flag that is not inside a ship, like `Hangar`.
     */
    @JvmStatic
    public fun location(flag: String): Location? = slots[flag]?.first ?: holds[flag]

    /**
     * Gives the [Entry.slot] for an ESI `location_flag`, counting from 1.
     *
     * ESI counts slots from 0, so `HiSlot2` is slot 3, and `FighterTube0` is tube 1.
     * Returns null for a flag that is not a slot, like `Cargo` or `Hangar`.
     */
    @JvmStatic
    public fun slot(flag: String): Long? = slots[flag]?.second
}
