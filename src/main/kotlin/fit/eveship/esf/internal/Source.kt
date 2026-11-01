package fit.eveship.esf.internal

import fit.eveship.esf.Catalog
import fit.eveship.esf.Entry
import fit.eveship.esf.EsfException
import fit.eveship.esf.EsfFit

// Fits as the text they stand for, and the check that their IDs match it (SPEC §10.2).

/** The fit and entry a line of source text stands for. */
internal class Where(
    val fit: Int,
    val entry: Int?,
)

/** The text document the fits stand for, and where each line comes from. */
internal fun source(
    fits: List<EsfFit>,
    catalog: Catalog,
): Pair<String, List<Where>> {
    if (fits.isEmpty()) {
        throw EsfException("a document holds at least one fit")
    }
    val out = StringBuilder()
    val where = mutableListOf<Where>()
    for ((i, fit) in fits.withIndex()) {
        val n = i + 1
        val hull = located(n, null) { hull(fit, catalog) }
        out.append("%esf/1\n").append(hull).append('\n')
        where.add(Where(n, null))
        where.add(Where(n, null))
        for ((j, entry) in fit.entries.withIndex()) {
            out.append(located(n, j + 1) { entry(entry, catalog) }).append('\n')
            where.add(Where(n, j + 1))
        }
    }
    return out.toString() to where
}

/** Check that every ID is the one its name resolves to. */
internal fun verify(
    resolved: List<Fit>,
    fits: List<EsfFit>,
) {
    for ((i, fit) in resolved.withIndex()) {
        val given = fits[i]
        located(i + 1, null) {
            same(fit.hull?.id, given.hull, "hull")
            same(fit.mode?.id, given.mode, "tactical mode")
        }
        for ((j, item) in fit.items.withIndex()) {
            if (item !is Item) {
                continue
            }
            val entry = given.entries[j]
            located(i + 1, j + 1) {
                same(item.type.id, entry.type, "type")
                same(item.charge?.id, entry.charge, "charge")
                same(item.mutaplasmid?.type?.id, entry.mutaplasmid, "mutaplasmid")
                val got = item.overrides.map { it.first.id }
                val want = entry.overrides.keys.toList()
                if (got != want) {
                    throw EsfException(
                        "attribute IDs ${want.joinToString(
                            ", ",
                            "[",
                            "]",
                        )} resolve to ${got.joinToString(", ", "[", "]")}",
                    )
                }
            }
        }
    }
}

/** Run [block], and say which fit and entry an error is about. */
internal inline fun <T> located(
    fit: Int,
    entry: Int?,
    block: () -> T,
): T =
    try {
        block()
    } catch (e: EsfException) {
        throw EsfException(e.reason, fitNumber = fit, entryNumber = entry)
    }

private fun name(text: String): String {
    if ('\r' in text || '\n' in text) {
        throw EsfException("a fit name contains CR or LF")
    }
    if (text.hasLoneSurrogate()) {
        throw EsfException("a fit name is not valid UTF-8")
    }
    return quote(text)
}

private fun typeName(
    catalog: Catalog,
    typeId: Int,
    what: String,
): String {
    val type = catalog.typeById(typeId) ?: throw EsfException("unknown $what type ID $typeId")
    return quoteType(type.name)
}

private fun number(value: Double): String {
    if (!value.isFinite()) {
        throw EsfException("an override value is not finite")
    }
    return formatNumber(value)
}

private fun entry(
    e: Entry,
    catalog: Catalog,
): String {
    if ((e.count != null && e.count < 1) || (e.chargeCount != null && e.chargeCount < 1)) {
        throw EsfException("a count must be at least 1")
    }
    if (e.type != null && e.location != null && Place.valueOf(e.location.name).isRack) {
        throw EsfException("a rack location is only for an empty slot")
    }
    if (e.chargeCount != null && e.charge == null) {
        throw EsfException("a charge count is set without a charge")
    }

    val parts = mutableListOf<String>()
    e.count?.let { parts.add("${it}x") }
    parts.add(e.type?.let { typeName(catalog, it, "item") } ?: "-")
    e.fitName?.takeIf { it.isNotEmpty() }?.let { parts.add(name(it)) }
    e.charge?.let { charge ->
        val count = e.chargeCount?.let { "${it}x " } ?: ""
        parts.add(":" + count + typeName(catalog, charge, "charge"))
    }
    e.mutaplasmid?.let { parts.add("+" + typeName(catalog, it, "mutaplasmid")) }
    if (e.overrides.isNotEmpty()) {
        val pairs =
            e.overrides.map { (attributeId, value) ->
                val attribute =
                    catalog.attributeById(attributeId) ?: throw EsfException("unknown attribute ID $attributeId")
                "${attribute.name} ${number(value)}"
            }
        parts.add(pairs.joinToString(", ", "{", "}"))
    }
    e.state?.let { parts.add("!" + it.name.lowercase()) }
    e.location?.let { parts.add("@" + it.name.lowercase()) }
    return parts.joinToString(" ")
}

private fun hull(
    fit: EsfFit,
    catalog: Catalog,
): String {
    val parts = mutableListOf(fit.hull?.let { typeName(catalog, it, "hull") } ?: "-")
    fit.name?.takeIf { it.isNotEmpty() }?.let { parts.add(name(it)) }
    fit.mode?.let { parts.add("/" + typeName(catalog, it, "tactical mode")) }
    return parts.joinToString(" ")
}

private fun same(
    got: Int?,
    want: Int?,
    what: String,
) {
    if (got != want) {
        throw EsfException("$what ID $want is not what its name resolves to ($got)")
    }
}
