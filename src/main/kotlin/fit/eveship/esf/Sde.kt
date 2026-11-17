package fit.eveship.esf

import java.util.function.Function
import java.util.function.Predicate
import java.util.function.ToIntFunction

/**
 * Helpers to build a [Catalog] from the SDE, for the fields that take more than a lookup.
 *
 * Each helper takes plain values from the SDE files, so it works however you load them.
 */
public object Sde {
    private const val CATEGORY_SHIP = 6
    private const val CATEGORY_MODULE = 7
    private const val CATEGORY_CHARGE = 8
    private const val CATEGORY_DRONE = 18
    private const val CATEGORY_IMPLANT = 20
    private const val CATEGORY_SUBSYSTEM = 32
    private const val CATEGORY_STRUCTURE = 65
    private const val CATEGORY_STRUCTURE_MODULE = 66
    private const val CATEGORY_FIGHTER = 87

    private const val GROUP_BOOSTER = 303
    private const val GROUP_SHIP_MODIFIERS = 1306
    private val GROUP_CONTAINERS = setOf(12, 340, 448, 649)

    private const val EFFECT_ONLINE = 16
    private const val EFFECT_CATEGORY_ACTIVE = 1
    private const val EFFECT_CATEGORY_TARGET = 2
    private val SLOT_EFFECTS =
        mapOf(12 to Kind.HIGH, 13 to Kind.MID, 11 to Kind.LOW, 2663 to Kind.RIG, 6306 to Kind.SVC)

    private const val ATTRIBUTE_SQUADRON_SIZE = 2215

    /**
     * Works out what a type is. See [Kind] for each rule.
     *
     * [categoryId] is the `categoryID` of the type's group, in `groups.jsonl`.
     * [groupId] is the type's `groupID`, in `types.jsonl`.
     * [effectIds] are the `effectID`s in the type's `dogmaEffects`, in `typeDogma.jsonl`.
     */
    @JvmStatic
    public fun kind(
        categoryId: Int,
        groupId: Int,
        effectIds: Collection<Int>,
    ): Kind =
        when {
            groupId == GROUP_SHIP_MODIFIERS -> {
                Kind.MODE
            }

            groupId in GROUP_CONTAINERS -> {
                Kind.CONTAINER
            }

            groupId == GROUP_BOOSTER -> {
                Kind.BOOSTER
            }

            else -> {
                when (categoryId) {
                    CATEGORY_SHIP, CATEGORY_STRUCTURE -> {
                        Kind.HULL
                    }

                    CATEGORY_MODULE, CATEGORY_STRUCTURE_MODULE -> {
                        effectIds.firstNotNullOfOrNull { SLOT_EFFECTS[it] } ?: Kind.OTHER
                    }

                    CATEGORY_SUBSYSTEM -> {
                        Kind.SUB
                    }

                    CATEGORY_DRONE -> {
                        Kind.DRONE
                    }

                    CATEGORY_FIGHTER -> {
                        Kind.FIGHTER
                    }

                    CATEGORY_CHARGE -> {
                        Kind.CHARGE
                    }

                    CATEGORY_IMPLANT -> {
                        Kind.IMPLANT
                    }

                    else -> {
                        Kind.OTHER
                    }
                }
            }
        }

    /**
     * Works out whether a type runs when it is turned on (§6.4), for [TypeRecord.active].
     *
     * [effects] maps each `effectID` in the type's `dogmaEffects` (in `typeDogma.jsonl`)
     * to that effect's `effectCategoryID` (in `dogmaEffects.jsonl`).
     *
     * A type runs when one of its effects is in category 1 (active) or 2 (target).
     * The SDE puts the `online` effect in category 1 too, but going online is not
     * running, so that one does not count.
     */
    @JvmStatic
    public fun active(effects: Map<Int, Int>): Boolean =
        effects.any { (effectId, category) ->
            effectId != EFFECT_ONLINE && (category == EFFECT_CATEGORY_ACTIVE || category == EFFECT_CATEGORY_TARGET)
        }

    /**
     * Reads how many fighters make up a full squadron, for [TypeRecord.squadronSize].
     *
     * [attributes] maps each `attributeID` in the type's `dogmaAttributes`
     * (in `typeDogma.jsonl`) to its `value`. Returns null when the type has no
     * `fighterSquadronMaxSize` (2215), which only fighters have.
     */
    @JvmStatic
    public fun squadronSize(attributes: Map<Int, Double>): Int? = attributes[ATTRIBUTE_SQUADRON_SIZE]?.toInt()

    /**
     * Finds which hull a tactical mode belongs to, for [Catalog.modes].
     *
     * The SDE does not link a mode to its hull. Instead, a mode's name starts with
     * its hull's name: "Hecate Sharpshooter Mode" belongs to the Hecate. This tries
     * the longest start of [modeName] first, a whole word at a time.
     *
     * [shipNames] is a [nameIndex] of every type in category `Ship` (6).
     * Returns the hull's type ID, or null when no ship name matches.
     */
    @JvmStatic
    public fun modeHull(
        modeName: String,
        shipNames: Map<String, Int>,
    ): Int? {
        val words = modeName.split(" ")
        return (words.size - 1 downTo 1).firstNotNullOfOrNull { shipNames[Esf.fold(words.take(it).joinToString(" "))] }
    }

    /**
     * Builds the name store that [Catalog.type] and [Catalog.attribute] look names up in.
     *
     * Give it every type, or every dogma attribute, including unpublished ones. Each name is
     * case-folded with [Esf.fold], the same way the library does with the names in a fit.
     * Where names are shared, the published one wins, then the lowest ID (§4.2).
     * In the SDE, a type or attribute without `published` counts as not published.
     *
     * Returns a map from case-folded name to ID.
     */
    @JvmStatic
    public fun <T> nameIndex(
        entries: Iterable<T>,
        id: ToIntFunction<T>,
        name: Function<T, String>,
        published: Predicate<T>,
    ): Map<String, Int> {
        val index = mutableMapOf<String, Int>()
        entries
            .sortedWith(compareBy({ !published.test(it) }, { id.applyAsInt(it) }))
            .forEach { index.putIfAbsent(Esf.fold(name.apply(it)), id.applyAsInt(it)) }
        return index
    }
}
