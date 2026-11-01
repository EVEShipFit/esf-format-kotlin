package fit.eveship.esf.internal

import fit.eveship.esf.EsfException
import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException

// Reading the text form into lines, without a Catalog (SPEC §4.1, grammar.ebnf).

private val HEADER = Regex(" *%([a-z][a-z0-9-]*)/([0-9]+) *")

/** Strict UTF-8; null where the bytes are not valid. */
internal fun decodeUtf8(
    data: ByteArray,
    offset: Int = 0,
    length: Int = data.size,
): String? =
    try {
        Charsets.UTF_8
            .newDecoder()
            .decode(ByteBuffer.wrap(data, offset, length))
            .toString()
    } catch (_: CharacterCodingException) {
        null
    }

/** A text document as a string, or the line that is not valid UTF-8. */
internal fun textOf(data: ByteArray): String {
    decodeUtf8(data)?.let { return it }
    var start = 0
    var number = 1
    while (true) {
        val end = data.indexOf(start, '\n'.code.toByte())
        if (decodeUtf8(data, start, end - start) == null) {
            throw EsfException("not valid UTF-8", number)
        }
        start = end + 1
        number++
    }
}

/** A text document as UTF-8, or the line that cannot be. */
internal fun utf8Of(text: String): ByteArray {
    try {
        return text.encodeToByteArray(throwOnInvalidSequence = true)
    } catch (_: CharacterCodingException) {
        val number = text.lineSequence().indexOfFirst { it.hasLoneSurrogate() }
        throw EsfException("not valid UTF-8", number + 1)
    }
}

private fun ByteArray.indexOf(
    from: Int,
    byte: Byte,
): Int = (from until size).firstOrNull { this[it] == byte } ?: size

/** Whether a string holds a surrogate that is not half of a pair. */
internal fun String.hasLoneSurrogate(): Boolean = indices.any { isLoneSurrogate(it) }

private fun String.isLoneSurrogate(i: Int): Boolean =
    when {
        this[i].isHighSurrogate() -> getOrNull(i + 1)?.isLowSurrogate() != true
        this[i].isLowSurrogate() -> getOrNull(i - 1)?.isHighSurrogate() != true
        else -> false
    }

/** Split a text document into fits; other blocks are skipped. */
internal fun parseText(data: String): List<FitBlock> {
    val lines = data.split('\n').let { if (it.last().isEmpty()) it.dropLast(1) else it }
    val fits = mutableListOf<FitBlock>()
    var current: FitBlock? = null
    var header: Int? = null
    var inBlock = false
    for ((n, line) in lines.withIndex()) {
        val number = n + 1
        val raw = line.removeSuffix("\r")
        if ('\r' in raw) {
            throw EsfException("a CR must be followed by an LF", number)
        }

        val stripped = raw.trimStart(' ')
        if (stripped.startsWith('%')) {
            val match = HEADER.matchEntire(raw) ?: throw EsfException("malformed header", number)
            if (header != null) {
                throw EsfException("a fit needs a hull line", header)
            }
            inBlock = true
            current = null
            val (format, version) = match.destructured
            if (format == "esf") {
                if (version != "1") {
                    throw EsfException("unsupported version esf/$version", number)
                }
                header = number
            }
            continue
        }

        if (!inBlock) {
            throw EsfException("a document starts with a header", number)
        }
        if (stripped.isEmpty() || stripped.startsWith("//")) {
            continue
        }

        val cursor = Cursor(number, raw)
        if (header != null) {
            current = hull(header, cursor).also { fits.add(it) }
            header = null
        } else {
            current?.lines?.add(entry(cursor))
        }
    }

    if (header != null) {
        throw EsfException("a fit needs a hull line", header)
    }
    if (fits.isEmpty()) {
        throw EsfException("a document holds at least one fit")
    }
    return fits
}

private fun hull(
    header: Int,
    cur: Cursor,
): FitBlock {
    val block = FitBlock(header, cur.number)
    cur.skipSpaces()
    if (cur.token() == "-") {
        cur.i++
        if (cur.nextElement()) {
            if (cur.peek() != '"') {
                cur.error("a hull line of '-' takes only a fit name")
            }
            block.fitName = cur.quoted()
        }
        cur.finish()
        return block
    }

    if (isCount(cur.token())) {
        cur.error("the hull line cannot have a count")
    }
    block.hull = cur.typeName()
    var more = cur.nextElement()
    if (more && cur.peek() == '"') {
        block.fitName = cur.quoted()
        more = cur.nextElement()
    }
    if (more) {
        if (cur.peek() != '/') {
            cur.error("unexpected '${cur.token()}' on the hull line")
        }
        cur.i++
        block.mode = cur.typeName()
    }
    cur.finish()
    return block
}

private fun entry(cur: Cursor): Line {
    cur.skipSpaces()
    val count = cur.count()

    if (cur.token() == "-") {
        cur.i++
        if (!cur.nextElement() || cur.peek() != '@') {
            cur.error("an empty slot must name its rack")
        }
        val (rack, index) = cur.location()
        if (!rack.isRack) {
            cur.error("an empty slot must name a rack")
        }
        if (index != null && count != null) {
            cur.error("a count and a pinned slot are mutually exclusive")
        }
        cur.finish()
        return EmptyLine(cur.number, rack, count, index)
    }

    val line = ItemLine(cur.number, cur.typeName(), count)
    var more = cur.nextElement()

    if (more && cur.peek() == '"') {
        line.fitName = cur.quoted()
        if (cur.nextElement()) {
            if (cur.peek() != '@') {
                cur.error("a reference takes only a count and a location")
            }
            val (location, index) = cur.location()
            if (index != null || !location.isStored) {
                cur.error("a reference can only be stored")
            }
            line.location = location
            cur.finish()
        }
        return line
    }

    var stage = 0
    while (more) {
        val c = cur.peek()
        when {
            c == ':' && stage < 1 -> {
                cur.i++
                line.chargeCount = cur.count()
                line.charge = cur.typeName()
                stage = 1
            }

            c == '+' && stage < 2 -> {
                cur.i++
                line.mutaplasmid = cur.typeName()
                stage = 2
            }

            c == '{' && stage < 3 -> {
                line.overrides = cur.overrides()
                stage = 3
            }

            c == '!' && stage < 4 -> {
                line.state = cur.state()
                stage = 4
            }

            c == '@' && stage < 5 -> {
                val (location, index) = cur.location()
                if (index == null && !location.isStored) {
                    cur.error("an item names a rack only to pin a slot, which needs an index")
                }
                if (index != null && count != null) {
                    cur.error("a count and a pinned slot are mutually exclusive")
                }
                line.location = location
                line.index = index
                stage = 5
            }

            else -> {
                cur.error("unexpected '${cur.token()}'")
            }
        }
        more = cur.nextElement()
    }
    return line
}
