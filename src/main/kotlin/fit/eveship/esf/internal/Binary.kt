package fit.eveship.esf.internal

import fit.eveship.esf.Entry
import fit.eveship.esf.EsfException
import fit.eveship.esf.EsfFit
import fit.eveship.esf.Location
import fit.eveship.esf.State

// The binary form (SPEC §10): esf.proto, with enum values in declaration order from 1.

internal fun encodeBinary(fits: List<EsfFit>): ByteArray {
    val out = WireWriter()
    fits.forEach { fit -> out.message(1) { encodeFit(fit) } }
    return out.toByteArray()
}

internal fun decodeBinary(data: ByteArray): List<EsfFit> {
    val fits = mutableListOf<EsfFit>()
    val reader = WireReader(data)
    while (!reader.done()) {
        val key = reader.key()
        if (key ushr 3 != 1L) {
            reader.skip(key)
            continue
        }
        if (key != tag(1, LEN)) {
            throw EsfException("binary: Document.fits has the wrong wire type")
        }
        fits.add(decodeFit(reader.message()))
    }
    if (fits.isEmpty()) {
        throw EsfException("binary: a document holds at least one fit")
    }
    return fits
}

private fun WireWriter.encodeFit(fit: EsfFit) {
    uint(1, 1)
    fit.hull?.let { uint(2, it.toLong()) }
    fit.name?.takeIf { it.isNotEmpty() }?.let { string(3, it) }
    fit.mode?.let { uint(4, it.toLong()) }
    fit.entries.forEach { entry -> message(5) { encodeEntry(entry) } }
}

private fun WireWriter.encodeEntry(entry: Entry) {
    entry.type?.let { uint(1, it.toLong()) }
    entry.count?.let { uint(2, it) }
    entry.fitName?.takeIf { it.isNotEmpty() }?.let { string(3, it) }
    entry.charge?.let { uint(4, it.toLong()) }
    entry.chargeCount?.let { uint(5, it) }
    entry.mutaplasmid?.let { uint(6, it.toLong()) }
    if (entry.overrides.isNotEmpty()) {
        packedUints(7, entry.overrides.keys)
        packedDoubles(8, entry.overrides.values)
    }
    entry.state?.let { uint(9, it.ordinal + 1L) }
    entry.location?.let { uint(10, it.ordinal + 1L) }
}

private fun decodeFit(reader: WireReader): EsfFit {
    var version = 0L
    var hull: Int? = null
    var name: String? = null
    var mode: Int? = null
    val entries = mutableListOf<Entry>()
    while (!reader.done()) {
        when (reader.key()) {
            tag(1, VARINT) -> version = uint32(reader.varint(), "Fit.version")
            tag(2, VARINT) -> hull = id(reader.varint(), "Fit.hull")
            tag(3, LEN) -> name = string(reader, "Fit.name")
            tag(4, VARINT) -> mode = id(reader.varint(), "Fit.tactical_mode")
            tag(5, LEN) -> entries.add(decodeEntry(reader.message()))
            else -> throw notInSchema("Fit")
        }
    }
    if (version != 1L) {
        throw EsfException("binary: unsupported Fit.version $version")
    }
    return EsfFit(hull, name, mode, entries)
}

private fun decodeEntry(reader: WireReader): Entry {
    var type: Int? = null
    var count: Long? = null
    var fitName: String? = null
    var charge: Int? = null
    var chargeCount: Long? = null
    var mutaplasmid: Int? = null
    val attributes = mutableListOf<Int>()
    val values = mutableListOf<Double>()
    var state: State? = null
    var location: Location? = null
    while (!reader.done()) {
        when (reader.key()) {
            tag(1, VARINT) -> type = id(reader.varint(), "Entry.type")
            tag(2, VARINT) -> count = uint32(reader.varint(), "Entry.count")
            tag(3, LEN) -> fitName = string(reader, "Entry.fit_name")
            tag(4, VARINT) -> charge = id(reader.varint(), "Entry.charge")
            tag(5, VARINT) -> chargeCount = uint32(reader.varint(), "Entry.charge_count")
            tag(6, VARINT) -> mutaplasmid = id(reader.varint(), "Entry.mutaplasmid")
            tag(7, VARINT) -> attributes.add(id(reader.varint(), "Entry.override_attributes"))
            tag(7, LEN) -> reader.message().uints().mapTo(attributes) { id(it, "Entry.override_attributes") }
            tag(8, I64) -> values.add(reader.double())
            tag(8, LEN) -> values.addAll(reader.message().doubles())
            tag(9, VARINT) -> state = enumValue(State.entries, reader.varint(), "State")
            tag(10, VARINT) -> location = enumValue(Location.entries, reader.varint(), "Location")
            else -> throw notInSchema("Entry")
        }
    }
    if (attributes.size != values.size) {
        throw EsfException("binary: override fields have different lengths")
    }
    val overrides = attributes.zip(values).toMap()
    if (overrides.size != attributes.size) {
        throw EsfException("binary: an attribute is overridden twice")
    }
    return Entry(type, count, fitName, charge, chargeCount, mutaplasmid, overrides, state, location)
}

private fun uint32(
    value: Long,
    what: String,
): Long {
    if (value !in 0..UINT32_MAX) {
        throw EsfException("binary: $what is out of range")
    }
    return value
}

/** A type or attribute ID, as an Int. */
private fun id(
    value: Long,
    what: String,
): Int {
    if (value !in 0..Int.MAX_VALUE) {
        throw EsfException("binary: $what is out of range")
    }
    return value.toInt()
}

private fun string(
    reader: WireReader,
    what: String,
): String? {
    val value = reader.string() ?: throw EsfException("binary: $what is not valid UTF-8")
    return value.ifEmpty { null }
}

/** An enum by its number, counting from 1; 0 is unset. */
private fun <T> enumValue(
    entries: List<T>,
    value: Long,
    what: String,
): T? =
    when (value) {
        0L -> null
        in 1L..entries.size.toLong() -> entries[value.toInt() - 1]
        else -> throw EsfException("binary: unknown $what value ${value.toULong()}")
    }

private fun notInSchema(what: String) = EsfException("binary: $what has a field that is not in the schema")
