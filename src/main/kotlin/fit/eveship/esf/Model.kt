package fit.eveship.esf

/** The state of an item, when not its default (§6.4). */
public enum class State {
    OFF,
    ON,
    HEAT,
}

/** Where an entry is, when not where its type goes by default (§5). */
public enum class Location {
    HIGH,
    MID,
    LOW,
    RIG,
    SUB,
    SVC,
    CARGO,
    BAY,
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
}

/** One line of a fit. */
public data class Entry
    @JvmOverloads
    constructor(
        val type: Int?,
        val count: Long? = null,
        val fitName: String? = null,
        val charge: Int? = null,
        val chargeCount: Long? = null,
        val mutaplasmid: Int? = null,
        val overrides: Map<Int, Double> = emptyMap(),
        val state: State? = null,
        val location: Location? = null,
    )

/** A fit: its hull and its entries. */
public data class EsfFit
    @JvmOverloads
    constructor(
        val hull: Int? = null,
        val name: String? = null,
        val mode: Int? = null,
        val entries: List<Entry> = emptyList(),
    )
