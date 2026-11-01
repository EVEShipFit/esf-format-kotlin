package fit.eveship.esf

/** What a type is (§5). */
public enum class Kind {
    HULL,
    CONTAINER,
    MODE,
    SUB,
    HIGH,
    MID,
    LOW,
    RIG,
    SVC,
    DRONE,
    FIGHTER,
    CHARGE,
    IMPLANT,
    BOOSTER,
    OTHER,
}

public data class TypeRecord
    @JvmOverloads
    constructor(
        val id: Int,
        val name: String,
        val kind: Kind,
        val active: Boolean,
        val capacity: Double,
        val volume: Double,
        val squadronSize: Int? = null,
    )

public data class AttributeRecord(
    val id: Int,
    val name: String,
)

/** A mutaplasmid for one base type; [rolls] is the base value by attribute ID. */
public data class MutaplasmidRecord(
    val type: TypeRecord,
    val rolls: Map<Int, Double>,
)

/** Types and attributes, by folded name (§6.10) or by ID. */
public interface Catalog {
    public fun type(folded: String): TypeRecord?

    public fun typeById(typeId: Int): TypeRecord?

    public fun attribute(folded: String): AttributeRecord?

    public fun attributeById(attributeId: Int): AttributeRecord?

    /** The tactical modes of a hull. */
    public fun modes(hullId: Int): List<TypeRecord> = emptyList()

    /** The mutaplasmids that apply to a type. */
    public fun mutaplasmids(typeId: Int): List<MutaplasmidRecord> = emptyList()
}
