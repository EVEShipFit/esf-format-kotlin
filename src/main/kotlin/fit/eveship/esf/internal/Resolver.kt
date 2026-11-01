package fit.eveship.esf.internal

import fit.eveship.esf.AttributeRecord
import fit.eveship.esf.Catalog
import fit.eveship.esf.EsfException
import fit.eveship.esf.Kind
import fit.eveship.esf.MutaplasmidRecord
import fit.eveship.esf.State
import fit.eveship.esf.TypeRecord
import java.util.EnumMap

// Resolving names against the Catalog, and the semantic rules (SPEC §4.2, §5, §6).

internal class Fit(
    val block: FitBlock,
    val hull: TypeRecord?,
    val mode: TypeRecord?,
    val cargoOnly: Boolean,
) {
    val items = mutableListOf<Placed>()
    val racks = EnumMap<Place, List<Run>>(Place::class.java)
    val name get() = block.fitName

    /** Each item that carries a fit, and that fit. */
    val references get() = items.mapNotNull { item -> (item as? Item)?.reference?.let { item to it } }
}

internal sealed interface Placed {
    val line: Line
    val place: Place
}

internal class EmptySlot(
    override val line: EmptyLine,
) : Placed {
    override val place get() = line.rack
}

internal class Item(
    override val line: ItemLine,
    val type: TypeRecord,
    override var place: Place,
) : Placed {
    var charge: TypeRecord? = null
    var mutaplasmid: MutaplasmidRecord? = null
    val overrides = mutableListOf<Pair<AttributeRecord, Double>>()
    var reference: Fit? = null
}

/** Consecutive slots of a rack holding the same item; null for empty slots. */
internal class Run(
    val start: Long,
    val length: Long,
    val item: Item?,
)

/** A tactical mode's name without the hull's name. */
internal fun modeName(
    hull: TypeRecord,
    mode: TypeRecord,
): String = mode.name.drop(hull.name.length).trimStart(' ')

internal fun resolve(
    blocks: List<FitBlock>,
    catalog: Catalog,
): List<Fit> {
    val fits = blocks.map { hull(it, catalog) }
    val named = mutableMapOf<Pair<Int, String>, MutableList<Fit>>()
    for (fit in fits) {
        val name = fit.name
        if (fit.hull != null && name != null) {
            named.getOrPut(fit.hull.id to fold(name)) { mutableListOf() }.add(fit)
        }
    }
    for (fit in fits) {
        fit.block.lines.mapTo(fit.items) { line(it, fit, named, catalog) }
        val plugged = mutableSetOf<Int>()
        for (item in fit.items) {
            if (item is Item && item.place.isPlugged && !plugged.add(item.type.id)) {
                throw EsfException("'${item.type.name}' is plugged in twice", item.line.number)
            }
        }
    }
    checkCycles(fits)
    for (fit in fits) {
        val byRack = fit.items.filter { it.place.isRack }.groupBy { it.place }
        byRack.mapValuesTo(fit.racks) { (_, items) -> layout(items) }
    }
    return fits
}

private fun type(
    catalog: Catalog,
    name: String,
    line: Int,
): TypeRecord = catalog.type(fold(name)) ?: throw EsfException("unknown type '$name'", line)

private fun <T> pick(
    candidates: List<T>,
    name: (T) -> String,
    given: String,
    what: String,
    line: Int,
): T {
    if (candidates.isEmpty()) {
        throw EsfException("no $what matches '$given'", line)
    }
    if (candidates.size > 1) {
        val names = candidates.joinToString(", ") { "'${name(it)}'" }
        throw EsfException("'$given' matches more than one $what: $names", line)
    }
    return candidates.single()
}

private fun hull(
    block: FitBlock,
    catalog: Catalog,
): Fit {
    val hullName = block.hull ?: return Fit(block, null, null, true)

    val hull = type(catalog, hullName, block.hullLine)
    if (hull.kind != Kind.HULL && hull.kind != Kind.CONTAINER) {
        throw EsfException("'${hull.name}' is not a ship, structure or container", block.hullLine)
    }

    val mode =
        block.mode?.let { given ->
            val modes = catalog.modes(hull.id)
            if (modes.isEmpty()) {
                throw EsfException("'${hull.name}' has no tactical modes", block.hullLine)
            }
            val matches = modes.filter { isWordPrefix(given, it.name) || isWordPrefix(given, modeName(hull, it)) }
            pick(matches, TypeRecord::name, given, "tactical mode of '${hull.name}'", block.hullLine)
        }

    return Fit(block, hull, mode, hull.kind == Kind.CONTAINER)
}

private fun line(
    line: Line,
    fit: Fit,
    named: Map<Pair<Int, String>, List<Fit>>,
    catalog: Catalog,
): Placed {
    val n = line.number
    if (line is EmptyLine) {
        if (fit.cargoOnly) {
            throw EsfException("a fit without a ship has no slots to keep empty", n)
        }
        return EmptySlot(line)
    }
    line as ItemLine

    val t = type(catalog, line.name, n)
    val kind = t.kind
    if (kind == Kind.MODE) {
        throw EsfException("a tactical mode is only written on the hull line", n)
    }

    val item = Item(line, t, Place.default(kind, fit.cargoOnly))

    line.fitName?.let { fitName ->
        if (kind != Kind.HULL && kind != Kind.CONTAINER) {
            throw EsfException("'${t.name}' cannot take a fit name", n)
        }
        val matches = named[t.id to fold(fitName)].orEmpty()
        if (matches.size != 1) {
            val what = if (matches.isEmpty()) "no fit" else "more than one fit"
            throw EsfException("$what matches ${t.name} '$fitName'", n)
        }
        item.reference = matches.single()
    }

    line.location?.let { location ->
        if (fit.cargoOnly && location != Place.CARGO) {
            throw EsfException("a fit without a ship stores everything in cargo", n)
        }
        if (line.index != null && Place.ofShip(kind) != location) {
            throw EsfException("'${t.name}' does not go in the ${location.text} rack", n)
        }
        if (location == Place.BAY && kind != Kind.DRONE && kind != Kind.FIGHTER) {
            throw EsfException("@bay is only for drones and fighters", n)
        }
        item.place = location
    }

    val plugged = item.place.isPlugged
    if (plugged && line.count != null) {
        throw EsfException("a plugged-in implant or booster takes no count", n)
    }

    line.state?.let { state ->
        if (item.place.isStored) {
            throw EsfException("a stored item has no state", n)
        }
        if (plugged && state != State.OFF) {
            throw EsfException("an implant or booster takes !off only", n)
        }
    }

    line.charge?.let { name ->
        val charge = type(catalog, name, n)
        if (charge.kind != Kind.CHARGE) {
            throw EsfException("'${charge.name}' is not a charge", n)
        }
        item.charge = charge
    }

    line.mutaplasmid?.let { given ->
        val matches = catalog.mutaplasmids(t.id).filter { isWordPrefix(given, it.type.name) }
        item.mutaplasmid = pick(matches, { it.type.name }, given, "mutaplasmid for '${t.name}'", n)
    }

    val seen = mutableSetOf<Int>()
    for ((name, value) in line.overrides) {
        val attribute = catalog.attribute(fold(name)) ?: throw EsfException("unknown attribute '$name'", n)
        if (!seen.add(attribute.id)) {
            throw EsfException("attribute '${attribute.name}' is overridden twice", n)
        }
        val number = value.toDouble()
        if (!number.isFinite()) {
            throw EsfException("value '$value' is out of range", n)
        }
        item.overrides.add(attribute to number)
    }

    return item
}

/** A fit cannot contain itself, directly or through other fits. */
private fun checkCycles(fits: List<Fit>) {
    class Frame(
        val fit: Fit,
    ) {
        val references = fit.references
        var at = 0
    }

    val done = mutableSetOf<Fit>()
    for (start in fits) {
        if (start in done) {
            continue
        }
        val path = mutableSetOf(start)
        val stack = ArrayDeque(listOf(Frame(start)))
        while (stack.isNotEmpty()) {
            val top = stack.last()
            if (top.at == top.references.size) {
                stack.removeLast()
                path.remove(top.fit)
                done.add(top.fit)
                continue
            }
            val (item, ref) = top.references[top.at++]
            if (ref in path) {
                throw EsfException("a fit cannot contain itself", item.line.number)
            }
            if (ref !in done) {
                path.add(ref)
                stack.addLast(Frame(ref))
            }
        }
    }
}

/** Assign slots in one rack (§6.3). */
private fun layout(items: List<Placed>): List<Run> {
    val pinned = sortedMapOf<Long, Placed>()
    for (item in items) {
        val index = item.line.index ?: continue
        if (pinned.put(index, item) != null) {
            throw EsfException("slot $index is pinned twice", item.line.number)
        }
    }

    val runs = pinned.map { (index, item) -> Run(index, 1, item as? Item) }.toMutableList()
    val pins = pinned.keys.toList()
    var nextPin = 0
    var pos = 1L
    for (item in items) {
        if (item.line.index != null) {
            continue
        }
        var remaining = item.line.count ?: 1
        while (remaining > 0) {
            while (pos in pinned) {
                pos++
            }
            while (nextPin < pins.size && pins[nextPin] < pos) {
                nextPin++
            }
            val room = if (nextPin < pins.size) pins[nextPin] - pos else remaining
            val take = minOf(remaining, room)
            runs.add(Run(pos, take, item as? Item))
            pos += take
            remaining -= take
        }
    }
    runs.sortBy { it.start }

    val gapless = mutableListOf<Run>()
    pos = 1
    for (run in runs) {
        if (run.start > pos) {
            gapless.add(Run(pos, run.start - pos, null))
        }
        gapless.add(run)
        pos = run.start + run.length
    }
    return gapless
}
