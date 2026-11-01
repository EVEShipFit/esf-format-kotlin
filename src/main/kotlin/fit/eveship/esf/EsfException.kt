package fit.eveship.esf

/** A broken rule of esf/1, and where: a line of text, or a fit and entry, counting from 1. */
public class EsfException
    @JvmOverloads
    constructor(
        public val reason: String,
        public val lineNumber: Int? = null,
        public val fitNumber: Int? = null,
        public val entryNumber: Int? = null,
    ) : Exception(where(lineNumber, fitNumber, entryNumber) + reason)

private fun where(
    line: Int?,
    fit: Int?,
    entry: Int?,
): String =
    when {
        fit != null && entry != null -> "fit $fit, entry $entry: "
        fit != null -> "fit $fit: "
        line != null -> "line $line: "
        else -> ""
    }
