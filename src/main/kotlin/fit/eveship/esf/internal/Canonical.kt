package fit.eveship.esf.internal

import fit.eveship.esf.AttributeRecord
import fit.eveship.esf.Catalog
import fit.eveship.esf.Entry
import fit.eveship.esf.EsfException
import fit.eveship.esf.EsfFit
import fit.eveship.esf.Kind
import fit.eveship.esf.Location
import fit.eveship.esf.MutaplasmidRecord
import fit.eveship.esf.State
import fit.eveship.esf.TypeRecord

// Canonical form (SPEC §8).

internal class CanonFit(
    val hull: TypeRecord?,
    val name: String?,
    val mode: TypeRecord?,
    val modeText: String?,
    val groups: List<List<CanonLine>>,
) {
    fun appendTo(out: StringBuilder) {
        out.append("%esf/1\n").append(hull?.let { quoteType(it.name) } ?: "-")
        name?.let { out.append(' ').append(quote(it)) }
        modeText?.let { out.append(" /").append(quoteType(it)) }
        out.append('\n')
        for (group in groups) {
            out.append('\n')
            group.forEach { out.append(it.text()).append('\n') }
        }
    }

    fun toModel(): EsfFit = EsfFit(hull?.id, name, mode?.id, groups.flatten().map { it.toModel() })
}

internal class CanonLine(
    val type: TypeRecord?,
    var count: Long? = null,
    val location: Place? = null,
) {
    var fitName: String? = null
    var charge: TypeRecord? = null
    var chargeCount: Long? = null
    var mutaplasmid: MutaplasmidRecord? = null
    var mutaplasmidName: String? = null
    var overrides: List<Pair<AttributeRecord, Double>> = emptyList()
    var state: State? = null

    fun body(): String =
        buildString {
            append(type?.let { quoteType(it.name) } ?: "-")
            fitName?.let { append(' ').append(quote(it)) }
            charge?.let {
                append(" :")
                chargeCount?.let { count -> append(count).append("x ") }
                append(quoteType(it.name))
            }
            mutaplasmidName?.let { append(" +").append(quoteType(it)) }
            if (overrides.isNotEmpty()) {
                overrides.joinTo(this, ", ", " {", "}") { (attribute, value) ->
                    "${attribute.name} ${formatNumber(value)}"
                }
            }
            state?.let { append(" !").append(it.name.lowercase()) }
            location?.let { append(" @").append(it.text) }
        }

    fun text(): String = count?.let { "${it}x ${body()}" } ?: body()

    fun toModel(): Entry =
        Entry(
            type?.id,
            count,
            fitName,
            charge?.id,
            chargeCount,
            mutaplasmid?.type?.id,
            overrides.associate { (attribute, value) -> attribute.id to value },
            state,
            location?.let { Location.valueOf(it.name) },
        )
}

internal fun quote(s: String): String = "\"" + s.replace("\"", "\"\"") + "\""

internal fun needsQuotes(name: String): Boolean {
    val words = name.split(' ')
    val first = words[0].firstOrNull()
    if (first == null || first == '-' || first == '%' || isCount(words[0])) {
        return true
    }
    return words.any { it.isEmpty() || it[0] in SIGILS }
}

internal fun quoteType(name: String): String = if (needsQuotes(name)) quote(name) else name

internal fun canonicalFit(
    fit: Fit,
    catalog: Catalog,
): CanonFit {
    val byGroup = mutableMapOf<Place, MutableList<Item>>()
    for (item in fit.items) {
        if (item !is Item || item.place.isRack) {
            continue
        }
        val group =
            when {
                item.place != Place.BAY -> item.place
                item.type.kind == Kind.FIGHTER -> Place.FIGHTERS
                else -> Place.DRONES
            }
        byGroup.getOrPut(group) { mutableListOf() }.add(item)
    }

    val groups =
        Place.entries
            .map { group ->
                val items = byGroup[group].orEmpty()
                when {
                    group.isRack -> rack(fit, group, catalog)
                    group == Place.DRONES -> drones(items, fit, catalog)
                    group == Place.FIGHTERS -> fighters(items, fit, catalog)
                    else -> sorted(items, fit, catalog)
                }
            }.filter { it.isNotEmpty() }

    val modeText =
        if (fit.hull != null && fit.mode != null) {
            val own = modeName(fit.hull, fit.mode)
            val others = catalog.modes(fit.hull.id).map { modeName(fit.hull, it) }
            shortestUnique(own, others.withoutFirst(own))
        } else {
            null
        }

    return CanonFit(fit.hull, fit.name, fit.mode, modeText, groups)
}

internal fun render(fits: List<CanonFit>): String =
    buildString {
        for ((i, fit) in fits.withIndex()) {
            if (i > 0) {
                append('\n')
            }
            fit.appendTo(this)
        }
    }

private fun List<String>.withoutFirst(name: String): List<String> = toMutableList().apply { remove(name) }

/** The state an item has when its line names none; null is active. */
private fun defaultState(item: Item): State? = if (item.place.isPlugged || !item.type.active) State.ON else null

private fun line(
    item: Item,
    fit: Fit,
    catalog: Catalog,
): CanonLine {
    val line = item.line
    val location = item.place.takeIf { !it.isRack && it != Place.default(item.type.kind, fit.cargoOnly) }
    val out = CanonLine(item.type, location = location)
    out.fitName = item.reference?.name

    item.charge?.let { charge ->
        out.charge = charge
        if (line.chargeCount != null && line.chargeCount != fullLoad(item.type, charge)) {
            out.chargeCount = line.chargeCount
        }
    }

    val overrides = LinkedHashMap<Int, Pair<AttributeRecord, Double>>()
    for ((attribute, value) in item.overrides) {
        overrides[attribute.id] = attribute to (if (value == 0.0) 0.0 else value)
    }
    item.mutaplasmid?.let { mutaplasmid ->
        out.mutaplasmid = mutaplasmid
        val own = mutaplasmid.type.name
        val others = catalog.mutaplasmids(item.type.id).map { it.type.name }
        out.mutaplasmidName = shortestUnique(own, others.withoutFirst(own))
        for ((attributeId, base) in mutaplasmid.rolls) {
            overrides.getOrPut(attributeId) {
                val attribute = catalog.attributeById(attributeId) ?: error("the catalog has no attribute $attributeId")
                attribute to base
            }
        }
    }
    out.overrides =
        overrides.values
            .map { Triple(fold(it.first.name), it.first.name, it) }
            .sortedWith { a, b -> compareUtf8(a.first, b.first).takeIf { it != 0 } ?: compareUtf8(a.second, b.second) }
            .map { it.third }

    if (line.state != null && line.state != defaultState(item)) {
        out.state = line.state
    }
    return out
}

/** How many charges fit in an item (§6.5). */
private fun fullLoad(
    item: TypeRecord,
    charge: TypeRecord,
): Long? = if (item.capacity <= 0 || charge.volume <= 0) null else quotient(item.capacity, charge.volume)

/** A canonical line, and whether it may merge with the same line next to it. */
private class Collapsible(
    val line: CanonLine,
    val repeatable: Boolean,
)

/** Merge adjacent identical repeatable lines into one count. */
private fun collapse(lines: List<Collapsible>): List<CanonLine> {
    val out = mutableListOf<CanonLine>()
    var last: CanonLine? = null
    var lastBody: String? = null
    for (next in lines) {
        val line = next.line
        val body = if (next.repeatable) line.body() else null
        if (last != null && body != null && body == lastBody) {
            val count = (last.count ?: 1) + (line.count ?: 1)
            if (count > UINT32_MAX) {
                throw EsfException("lines collapse into a count of $count, which is too large")
            }
            last.count = count
            continue
        }
        last = line
        lastBody = body
        out.add(line)
    }
    out.filter { it.count == 1L }.forEach { it.count = null }
    return out
}

private fun rack(
    fit: Fit,
    rack: Place,
    catalog: Catalog,
): List<CanonLine> {
    val runs = fit.racks[rack].orEmpty().dropLastWhile { it.item == null }
    return collapse(
        runs.map { run ->
            val line = run.item?.let { line(it, fit, catalog) } ?: CanonLine(null, location = rack)
            line.count = run.length
            Collapsible(line, true)
        },
    )
}

private fun sorted(
    items: List<Item>,
    fit: Fit,
    catalog: Catalog,
): List<CanonLine> {
    class Sortable(
        val key: String,
        val text: String,
        val line: Collapsible,
    )

    val lines =
        items.map { item ->
            val line = line(item, fit, catalog)
            val repeatable = !item.place.isStored
            val count = item.line.count ?: 1
            val text =
                if (repeatable) {
                    line.count = count
                    line.body()
                } else {
                    line.count = count.takeIf { it != 1L }
                    line.text()
                }
            Sortable(fold(item.type.name), text, Collapsible(line, repeatable))
        }
    val order =
        Comparator<Sortable> { a, b ->
            compareUtf8(a.key, b.key).takeIf { it != 0 }
                ?: compareUtf8(a.text, b.text)
        }
    return collapse(lines.sortedWith(order).map { it.line })
}

private fun fighters(
    items: List<Item>,
    fit: Fit,
    catalog: Catalog,
): List<CanonLine> {
    val (tubes, bay) = items.partition { it.place == Place.FIGHTERS }
    val lines =
        tubes.map { item ->
            line(item, fit, catalog).apply {
                count = item.line.count?.takeIf { it != item.type.squadronSize?.toLong() }
            }
        }
    return lines + sorted(bay, fit, catalog)
}

private fun drones(
    items: List<Item>,
    fit: Fit,
    catalog: Catalog,
): List<CanonLine> {
    val (space, bay) = items.partition { it.place == Place.DRONES }
    return sorted(space, fit, catalog) + sorted(bay, fit, catalog)
}
