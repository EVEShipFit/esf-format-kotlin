package fit.eveship.esf.internal

import fit.eveship.esf.Kind
import fit.eveship.esf.Location

/** Where an item is in a fit (§5), in the order of the canonical groups (§8). */
internal enum class Place {
    SUB,
    HIGH,
    MID,
    LOW,
    RIG,
    SVC,
    DRONES,
    TUBE,
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
    val isRack get() = this <= SVC || this == TUBE
    val isStored get() = this == BAY || this in CARGO..SUBSYSTEM
    val isPlugged get() = this == IMPLANTS || this == BOOSTERS
    val location get() = locations.getValue(name)

    companion object {
        private val byName = entries.associateBy { it.name }
        private val locations = Location.entries.associateBy { it.name }

        fun of(location: Location) = byName.getValue(location.name)

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
                Kind.FIGHTER -> TUBE
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
