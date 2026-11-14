package fit.eveship.esf

/**
 * What a type is, which decides where it goes in a fit (esf/1 spec §5).
 *
 * Check the group rules first ([MODE], [CONTAINER], [BOOSTER]), then the category rules.
 * [Sde.kind] works this out from the SDE for you.
 */
public enum class Kind {
    /** A ship or a structure: SDE category `Ship` (6) or `Structure` (65). */
    HULL,

    /**
     * A container that can carry another fit, like a Small Secure Container.
     *
     * SDE group `Cargo Container` (12), `Secure Cargo Container` (340),
     * `Audit Log Secure Container` (448) or `Freight Container` (649).
     */
    CONTAINER,

    /** A tactical mode, like the Hecate's Sharpshooter Mode: SDE group `Ship Modifiers` (1306). */
    MODE,

    /** A subsystem, like on a Tengu: SDE category `Subsystem` (32). */
    SUB,

    /** A high slot module, like a gun: SDE category `Module` (7) or `Structure Module` (66), with effect `hiPower` (12). */
    HIGH,

    /** A mid slot module, like a shield extender: category `Module` or `Structure Module`, with effect `medPower` (13). */
    MID,

    /** A low slot module, like a damage control: category `Module` or `Structure Module`, with effect `loPower` (11). */
    LOW,

    /** A rig: category `Module` or `Structure Module`, with effect `rigSlot` (2663). */
    RIG,

    /** A structure service module: category `Module` or `Structure Module`, with effect `serviceSlot` (6306). */
    SVC,

    /** A drone: SDE category `Drone` (18). */
    DRONE,

    /** A fighter: SDE category `Fighter` (87). */
    FIGHTER,

    /** Ammunition, a script or anything else loaded into a module: SDE category `Charge` (8). */
    CHARGE,

    /** An implant: SDE category `Implant` (20), except the boosters in it. */
    IMPLANT,

    /** A booster, also called a drug: SDE group `Booster` (303), inside category `Implant`. */
    BOOSTER,

    /** Anything else, like ore or a module without a slot. It goes in the cargo. */
    OTHER,
}

/**
 * One type, like "Rifter" or "200mm AutoCannon II", as the library needs it.
 *
 * With the SDE, most fields come from `types.jsonl`. [kind], [active] and [squadronSize]
 * also need `groups.jsonl`, `typeDogma.jsonl` and `dogmaEffects.jsonl`, through the helpers in [Sde].
 * Include every type in `types.jsonl`. A type with no row in `typeDogma.jsonl` has no effects
 * and no attributes: give the helpers an empty collection or map.
 */
public data class TypeRecord
    @JvmOverloads
    constructor(
        /** The type ID, like 587 for the Rifter. In the SDE this is `_key`. */
        val id: Int,
        /** The English name, like "Rifter". In the SDE this is `name.en`. */
        val name: String,
        /** What it is, which decides where it goes in a fit. Use [Sde.kind] to work this out. */
        val kind: Kind,
        /**
         * Whether the type runs when it is turned on, like a gun or a shield booster.
         *
         * A plate or a damage control only goes online, so it is false.
         * Use [Sde.active] to work this out from the type's effects.
         */
        val active: Boolean,
        /**
         * How much the type holds, in m³; for a module, how much ammunition fits in it.
         *
         * In the SDE this is `capacity`. Use 0 when the type has none.
         */
        val capacity: Double,
        /** How much room one of it takes, in m³. In the SDE this is `volume`. Use 0 when the type has none. */
        val volume: Double,
        /**
         * For a fighter, how many make up a full squadron. Null for anything else.
         *
         * In the SDE this is the attribute `fighterSquadronMaxSize`; [Sde.squadronSize] reads it for you.
         */
        val squadronSize: Int? = null,
    )

/**
 * One dogma attribute, like "speedFactor", as the library needs it.
 *
 * With the SDE, both fields come from `dogmaAttributes.jsonl`.
 */
public data class AttributeRecord(
    /** The attribute ID, like 20 for speedFactor. In the SDE this is `_key`. */
    val id: Int,
    /** The name, like "speedFactor". In the SDE this is `name`; it is not translated. */
    val name: String,
)

/**
 * One mutaplasmid that can be applied to a base type, like "Unstable 5MN Microwarpdrive Mutaplasmid".
 *
 * With the SDE, these come from `dynamicItemAttributes.jsonl`. Each entry there is one mutaplasmid.
 * Its `inputOutputMapping` is a list; the base types it applies to are all the `applicableTypes`
 * across that list.
 */
public data class MutaplasmidRecord(
    /** The mutaplasmid's own type: the type whose ID is the entry's `_key`. */
    val type: TypeRecord,
    /**
     * The attributes the mutaplasmid changes, each with the base type's value before the change.
     *
     * The keys are the `_key` of each item in the entry's `attributeIDs`; its `min` and `max`
     * are not needed. Each value is the base type's value of that attribute in `typeDogma.jsonl`.
     * When the base type does not have it, use the attribute's `defaultValue` in
     * `dogmaAttributes.jsonl`, or 0 when that is missing too.
     */
    val rolls: Map<Int, Double>,
)

/**
 * Where the library looks up game data. You write this, on top of your copy of the SDE.
 *
 * The library holds no game data itself. Whenever it meets a name or an ID in a fit,
 * it asks the catalog what that is. [Sde] has helpers for the parts that take more
 * than a lookup.
 *
 * The `§` numbers point to sections of the esf/1 spec:
 * https://github.com/EVEShipFit/esf-format/blob/main/SPEC.md
 */
public interface Catalog {
    /**
     * Finds a type by its name.
     *
     * The name comes from a fit, so a user may have typed it in any case.
     * The library already case-folded it with [Esf.fold], so "Rifter", "RIFTER"
     * and "rifter" all reach you as "rifter". Case folding is like lowercase, but
     * gives the same result in every language, so use [Esf.fold], not `lowercase()`.
     * Store your type names the same way, by calling [Esf.fold] on each one,
     * and look this name up in that store.
     *
     * Include unpublished types too. Some names belong to more than one type.
     * Then return the published one; if that is still more than one, the lowest ID.
     * [Sde.nameIndex] builds this store for you.
     *
     * Return null if no type has this name.
     */
    public fun type(foldedName: String): TypeRecord?

    /** Finds a type by its ID. Return null if there is no type with this ID. */
    public fun typeById(typeId: Int): TypeRecord?

    /**
     * Finds an attribute by its name, like "speedfactor".
     *
     * This works the same way as [type]: the name is already case-folded with [Esf.fold],
     * unpublished attributes count too, and where names are shared, the published one
     * wins, then the lowest ID. [Sde.nameIndex] builds this store for you.
     *
     * Return null if no attribute has this name.
     */
    public fun attribute(foldedName: String): AttributeRecord?

    /** Finds an attribute by its ID. Return null if there is no attribute with this ID. */
    public fun attributeById(attributeId: Int): AttributeRecord?

    /**
     * The tactical modes a hull can be in, like the Hecate's Defense, Propulsion and Sharpshooter Mode.
     *
     * Return an empty list for a hull without tactical modes. In the SDE, modes are the types
     * of kind [Kind.MODE]; [Sde.modeHull] tells you which hull each one belongs to.
     *
     * Leave this out and no hull has tactical modes.
     */
    public fun modes(hullId: Int): List<TypeRecord> = emptyList()

    /**
     * The mutaplasmids that can be applied to a type. See [MutaplasmidRecord] for where they come from.
     *
     * Return an empty list for a type no mutaplasmid applies to.
     *
     * Leave this out and no type has mutaplasmids.
     */
    public fun mutaplasmids(typeId: Int): List<MutaplasmidRecord> = emptyList()
}
