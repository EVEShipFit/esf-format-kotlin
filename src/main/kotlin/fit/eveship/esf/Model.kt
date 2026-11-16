package fit.eveship.esf

/**
 * Whether an item is offline, online or running, when that is not its default (esf/1 spec §6.4).
 *
 * By default, a fitted or deployed item runs when its type is [TypeRecord.active], and is
 * online otherwise. An implant or booster is online by default, and only takes [OFF].
 * A stored item, in cargo, a bay or a hold, has no state at all.
 */
public enum class State {
    /** Offline: fitted, but not using powergrid or CPU. */
    OFF,

    /** Online, but not running, like a gun that is not shooting. */
    ON,

    /** Overloaded, which also means running. */
    HEAT,
}

/**
 * Where an entry goes, when not where its type goes by default (esf/1 spec §5).
 *
 * An item goes where its [Kind] says, so most entries leave this null. Set it to store an
 * item somewhere else, like a spare Damage Control II in [CARGO].
 * So a drone with no location is out in space, and a fighter is in a launch tube. Set
 * [BAY] to keep either one in its bay.
 *
 * The racks, [HIGH] to [SVC], are for empty slots, and for an item put in a given
 * [Entry.slot]. Otherwise, an item never names its rack.
 * The holds, [AMMO] to [SUBSYSTEM], only make sense on a hull that has that hold, but the
 * library does not check that.
 */
public enum class Location {
    /** The high slot rack. */
    HIGH,

    /** The mid slot rack. */
    MID,

    /** The low slot rack. */
    LOW,

    /** The rig rack. */
    RIG,

    /** The subsystem rack. Not the same as [SUBSYSTEM], which is a hold. */
    SUB,

    /** The structure service rack. */
    SVC,

    /** The cargo hold. */
    CARGO,

    /** The drone bay or fighter bay, for drones and fighters that are not out in space or in a tube. */
    BAY,

    /** The Ammo Hold. */
    AMMO,

    /** The Booster Hold. */
    BOOSTER,

    /** The Command Center Hold. */
    COMMAND,

    /** The Corpse Hold. */
    CORPSE,

    /** The Mobile Depot Hold. */
    DEPOT,

    /** The Expedition Hold. */
    EXPEDITION,

    /** The Fleet Hangar. */
    FLEET,

    /** The Frigate Escape Bay. */
    FRIGATE,

    /** The Fuel Bay. */
    FUEL,

    /** The Gas Hold. */
    GAS,

    /** The Ice Hold. */
    ICE,

    /** The Infrastructure Hold. */
    INFRASTRUCTURE,

    /** The Ship Maintenance Bay. */
    MAINTENANCE,

    /** The Mineral Hold. */
    MINERAL,

    /** The Mining Hold. */
    MINING,

    /** The Moon Material Output Bay. */
    MOON,

    /** The Planetary Commodities Hold. */
    PLANETARY,

    /** The Quafe Hold. */
    QUAFE,

    /** The Subsystem Hold, for spare subsystems. Not the same as [SUB], which is the rack. */
    SUBSYSTEM,
}

/**
 * One line of a fit: an item, an empty slot, or another fit carried by this one.
 *
 * Only [type] is needed; every other field is null or empty when it is the default.
 * Entries can be in any order, except within a rack: there, the order is the slot order,
 * unless an entry sets [slot].
 */
public data class Entry
    @JvmOverloads
    constructor(
        /**
         * The type ID of the item, like 2889 for a 200mm AutoCannon II.
         *
         * Null for an empty slot. An empty slot must set [location] to its rack, like [Location.HIGH].
         */
        val type: Int?,
        /**
         * How many there are. Null means one.
         *
         * What a count means depends on where the item goes (esf/1 spec §6.2). For a fitted
         * module or a deployed drone, it is that many of the same line: 3 guns take 3 slots.
         * For a stored item, it is the size of one stack. For a fighter, it is how many are
         * in the squadron; null means a full squadron. A plugged-in implant or booster takes
         * no count.
         */
        val count: Long? = null,
        /**
         * The name of another fit in the same list, which this entry carries.
         *
         * Only for a ship, structure or container. The other fit must have this entry's
         * [type] as its [EsfFit.hull] and this as its [EsfFit.name], ignoring case. For example,
         * a Heron with fit name "Scout" and [Location.FRIGATE] carries that fit in the
         * Frigate Escape Bay. Null means an unfitted ship or an empty container.
         */
        val fitName: String? = null,
        /** The type ID of the charge loaded into this item, like ammunition or a script. Null for none. */
        val charge: Int? = null,
        /**
         * How many of [charge] are loaded into each item.
         *
         * Null means a full load: the item's capacity divided by the charge's volume, rounded
         * down. Only set together with [charge].
         */
        val chargeCount: Long? = null,
        /**
         * The type ID of the mutaplasmid applied to this item, which makes it abyssal.
         *
         * [type] is then the base type the mutaplasmid applies to, and [overrides] holds the
         * rolled values. Null for a normal item.
         */
        val mutaplasmid: Int? = null,
        /**
         * Attribute values that replace the type's own, by attribute ID, like 20 (speedFactor) to 524.0.
         *
         * Each value is the new base value, not a change on top of it. Attributes not listed
         * keep the type's value. Empty for none.
         */
        val overrides: Map<Int, Double> = emptyMap(),
        /** Whether the item is offline, online or running. Null means its default; see [State]. */
        val state: State? = null,
        /** Where the item goes. Null means where its type goes by default; see [Location]. */
        val location: Location? = null,
        /**
         * The slot in the [location] rack, counting from 1, like 3 for the third high slot.
         *
         * Set [location] to the rack too. Null means the next free slot. [Esf.load] never sets
         * this: it gives the rack in slot order, with empty slots in between.
         */
        val slot: Long? = null,
    )

/**
 * One fit: a ship and everything on it, or a plain list of items.
 *
 * A list of fits is one document. A fit that another fit carries, through [Entry.fitName],
 * exists only there. Any other fit stands on its own.
 */
public data class EsfFit
    @JvmOverloads
    constructor(
        /**
         * The type ID of the ship, structure or container, like 587 for the Rifter.
         *
         * Null for a fit without a ship, like the items in a contract: then every entry is
         * stored in cargo, and the fit has no slots and no [mode].
         */
        val hull: Int? = null,
        /** The name of the fit, like "Shield Buffer". Null for no name. */
        val name: String? = null,
        /**
         * The type ID of the tactical mode the ship is in, like the Hecate's Sharpshooter Mode.
         *
         * Only for a hull that has tactical modes; see [Catalog.modes]. Null for none.
         */
        val mode: Int? = null,
        /** The lines of the fit: items, empty slots and fits it carries. */
        val entries: List<Entry> = emptyList(),
    )
