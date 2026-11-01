package fit.eveship.esf.internal

import fit.eveship.esf.EsfException
import fit.eveship.esf.State

internal const val SIGILS = ":+{!@/\""
internal const val UINT32_MAX = 0xFFFFFFFFL

internal fun isCount(token: String): Boolean =
    token.length > 1 && token.last() == 'x' && token.dropLast(1).all { it in '0'..'9' }

/** A position in one line of text (grammar.ebnf). */
internal class Cursor(
    val number: Int,
    private val s: String,
) {
    var i = 0

    fun error(message: String): Nothing = throw EsfException(message, number)

    fun peek(): Char? = s.getOrNull(i)

    fun token(): String {
        val end = s.indexOf(' ', i)
        return s.substring(i, if (end < 0) s.length else end)
    }

    fun skipSpaces(): Boolean {
        val start = i
        while (i < s.length && s[i] == ' ') {
            i++
        }
        return i > start
    }

    /** Step over the separating space; false at the end of the line or a comment. */
    fun nextElement(): Boolean {
        val spaced = skipSpaces()
        if (i >= s.length) {
            return false
        }
        if (s.startsWith("//", i)) {
            if (!spaced) {
                error("a comment needs a space before it")
            }
            i = s.length
            return false
        }
        if (!spaced) {
            error("expected a space before '${token()}'")
        }
        return true
    }

    fun finish() {
        if (nextElement()) {
            error("unexpected '${token()}'")
        }
    }

    fun positive(
        digits: String,
        what: String,
    ): Long {
        val significant = digits.trimStart('0')
        if (significant.isEmpty()) {
            error("$what must be at least 1")
        }
        if (significant.length > 10 || significant.toLong() > UINT32_MAX) {
            error("$what is too large")
        }
        return significant.toLong()
    }

    fun count(): Long? {
        val token = token()
        if (!isCount(token)) {
            return null
        }
        i += token.length
        if (!nextElement()) {
            error("a count must be followed by a type name")
        }
        return positive(token.dropLast(1), "a count")
    }

    fun quoted(): String {
        val out = StringBuilder()
        var j = i + 1
        while (true) {
            val end = s.indexOf('"', j)
            if (end < 0) {
                error("unterminated quoted string")
            }
            out.append(s, j, end)
            if (s.startsWith("\"\"", end)) {
                out.append('"')
                j = end + 2
                continue
            }
            i = end + 1
            break
        }
        if (out.isEmpty()) {
            error("a quoted string cannot be empty")
        }
        return out.toString()
    }

    fun word(): String = token().also { i += it.length }

    fun typeName(): String {
        val c = peek()
        if (c == '"') {
            return quoted()
        }
        if (endsName(c)) {
            error("expected a type name")
        }
        if (c == '-' || c == '%') {
            error("a name starting with '$c' must be quoted")
        }
        val words = mutableListOf(word())
        while (true) {
            val end = i
            if (!skipSpaces() || endsName(peek())) {
                i = end
                return words.joinToString(" ")
            }
            words.add(word())
        }
    }

    /** A location, and its slot number where it pins one. */
    fun location(): Pair<Place, Long?> {
        val nameStart = i + 1
        var j = nameStart
        while (j < s.length && s[j] in 'a'..'z') {
            j++
        }
        if (j == nameStart) {
            error("unknown location '${token()}'")
        }
        val name = s.substring(nameStart, j)
        val indexStart = j
        while (j < s.length && s[j] in '0'..'9') {
            j++
        }
        i = j
        val place = Place.written[name]
        if (indexStart < j) {
            if (place == null || !place.isRack) {
                error("unknown rack '$name'")
            }
            return place to positive(s.substring(indexStart, j), "a slot number")
        }
        return (place ?: error("unknown location '$name'")) to null
    }

    fun state(): State {
        val state = State.entries.firstOrNull { s.startsWith(it.name.lowercase(), i + 1) }
        if (state == null) {
            error("unknown state '${token()}'")
        }
        i += 1 + state.name.length
        return state
    }

    /** Attribute names and values, as written. */
    fun overrides(): List<Pair<String, String>> {
        i++
        skipSpaces()
        val pairs = mutableListOf<Pair<String, String>>()
        while (true) {
            val name = attributeName()
            if (name == null || !skipSpaces()) {
                error("malformed override; expected 'attribute value'")
            }
            val value = number() ?: error("malformed override; expected 'attribute value'")
            pairs.add(name to value)
            skipSpaces()
            when (peek()) {
                ',' -> {
                    i++
                    skipSpaces()
                }

                '}' -> {
                    i++
                    return pairs
                }

                else -> {
                    error("malformed overrides; expected ',' or '}'")
                }
            }
        }
    }

    private fun attributeName(): String? {
        val start = i
        if (peek()?.isAsciiLetter() != true) {
            return null
        }
        while (peek()?.let { it.isAsciiLetter() || it in '0'..'9' } == true) {
            i++
        }
        return s.substring(start, i)
    }

    /** `-?[0-9]+(\.[0-9]+)?` */
    private fun number(): String? {
        val start = i
        if (peek() == '-') {
            i++
        }
        if (digits() == 0) {
            return null
        }
        if (peek() == '.' && s.getOrNull(i + 1)?.let { it in '0'..'9' } == true) {
            i++
            digits()
        }
        return s.substring(start, i)
    }

    private fun digits(): Int {
        val start = i
        while (peek()?.let { it in '0'..'9' } == true) {
            i++
        }
        return i - start
    }

    private fun endsName(c: Char?): Boolean = c == null || c == ' ' || c in SIGILS

    private fun Char.isAsciiLetter(): Boolean = this in 'a'..'z' || this in 'A'..'Z'
}
