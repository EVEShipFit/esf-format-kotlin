package fit.eveship.esf.internal

// Name matching rules (SPEC §6.10, §6.1, §6.6).

/** Unicode simple case folding. */
internal fun fold(s: String): String {
    if (s.all { it < '\u0080' }) {
        return s.lowercase()
    }
    val out = StringBuilder(s.length)
    s.codePoints().forEach { out.appendCodePoint(foldCodePoint(it)) }
    return out.toString()
}

/** Simple case folding of one code point: lower case of upper case, except where CaseFolding.txt differs. */
private fun foldCodePoint(c: Int): Int =
    when (c) {
        0x130, 0x131 -> c
        0x1FD3 -> 0x390
        0x1FE3 -> 0x3B0
        0xFB05 -> 0xFB06
        in 0x13A0..0x13F5 -> c
        in 0x13F8..0x13FD, in 0xAB70..0xABBF -> Character.toUpperCase(c)
        else -> Character.toLowerCase(Character.toUpperCase(c))
    }

/** Whether [prefix] is [name] with zero or more whole words dropped from the end. */
internal fun isWordPrefix(
    prefix: String,
    name: String,
): Boolean {
    val words = fold(prefix)
    val full = fold(name)
    return full.startsWith(words) && (full.length == words.length || full[words.length] == ' ')
}

/** The fewest leading words of [name] that are a word prefix of no name in [others]. */
internal fun shortestUnique(
    name: String,
    others: List<String>,
): String {
    val words = name.split(' ')
    for (n in 1..words.size) {
        val candidate = words.subList(0, n).joinToString(" ")
        if (others.none { isWordPrefix(candidate, it) }) {
            return candidate
        }
    }
    return name
}

/** Compare strings by code point, the order of their UTF-8 bytes. */
internal fun compareUtf8(
    a: String,
    b: String,
): Int {
    val length = minOf(a.length, b.length)
    for (i in 0 until length) {
        val x = a[i]
        val y = b[i]
        if (x != y) {
            if (x >= '\uD800' && y >= '\uD800') {
                return codePointOrder(x) - codePointOrder(y)
            }
            return x - y
        }
    }
    return a.length - b.length
}

/** Surrogates above the rest of the BMP, as their code points are. */
private fun codePointOrder(c: Char): Int =
    when {
        c >= '' -> c.code - 0x800
        else -> c.code + 0x2000
    }
