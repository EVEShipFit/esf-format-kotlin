package fit.eveship.esf.internal

import fit.eveship.esf.Kind

/** Where an item is in a fit (§5), in the order of the canonical groups (§8). */
internal enum class Place {
    SUB,
    HIGH,
    MID,
    LOW,
    RIG,
    SVC,
    DRONES,
    FIGHTERS,
    CARGO,
    AMMO,
    BOOSTER,
    COMMAND,
    CORPSE,
    DEPOT,
    EXPEDITION,
    FLEET,
    FRIGATE,
    FUEL,
    GAS,
    ICE,
    INFRASTRUCTURE,
    MAINTENANCE,
    MINERAL,
    MINING,
    MOON,
    PLANETARY,
    QUAFE,
    SUBSYSTEM,
    IMPLANTS,
    BOOSTERS,
    BAY,
    ;

    val text = name.lowercase()
    val isRack get() = this <= SVC
    val isStored get() = this == BAY || this in CARGO..SUBSYSTEM
    val isPlugged get() = this == IMPLANTS || this == BOOSTERS

    companion object {
        /** The places a line can name with `@`, by name. */
        val written = entries.filter { it.isRack || it.isStored }.associateBy { it.text }

        /** Where a type goes in a ship, or null for cargo. */
        fun ofShip(kind: Kind): Place? =
            when (kind) {
                Kind.SUB -> SUB
                Kind.HIGH -> HIGH
                Kind.MID -> MID
                Kind.LOW -> LOW
                Kind.RIG -> RIG
                Kind.SVC -> SVC
                Kind.DRONE -> DRONES
                Kind.FIGHTER -> FIGHTERS
                Kind.IMPLANT -> IMPLANTS
                Kind.BOOSTER -> BOOSTERS
                else -> null
            }

        fun default(
            kind: Kind,
            cargoOnly: Boolean,
        ): Place = if (cargoOnly) CARGO else ofShip(kind) ?: CARGO
    }
}
