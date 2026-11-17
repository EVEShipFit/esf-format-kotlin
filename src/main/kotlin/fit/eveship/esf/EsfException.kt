package fit.eveship.esf

/**
 * An esf/1 error, and where: a line of text, or a fit and entry, counting from 1.
 *
 * Every error is one of three kinds. Catch [EsfFormatException], [EsfCatalogException] or
 * [EsfRuleException] to handle one kind, or this class to handle them all.
 */
public sealed class EsfException(
    public val reason: String,
    public val lineNumber: Int?,
    public val fitNumber: Int?,
    public val entryNumber: Int?,
) : Exception(where(lineNumber, fitNumber, entryNumber) + reason) {
    internal fun at(
        fit: Int,
        entry: Int?,
    ): EsfException =
        when (this) {
            is EsfFormatException -> EsfFormatException(reason, fitNumber = fit, entryNumber = entry)
            is EsfCatalogException -> EsfCatalogException(reason, fitNumber = fit, entryNumber = entry)
            is EsfRuleException -> EsfRuleException(reason, fitNumber = fit, entryNumber = entry)
        }
}

/**
 * The data is not a well-formed esf/1 document.
 *
 * The text does not follow the grammar, the binary form or base64url is broken, or a fit name
 * cannot be written.
 */
public class EsfFormatException
    @JvmOverloads
    constructor(
        reason: String,
        lineNumber: Int? = null,
        fitNumber: Int? = null,
        entryNumber: Int? = null,
    ) : EsfException(reason, lineNumber, fitNumber, entryNumber)

/**
 * The catalog does not know a name or ID, or a name matches more than one type.
 *
 * This often means the catalog is older than the fit.
 */
public class EsfCatalogException
    @JvmOverloads
    constructor(
        reason: String,
        lineNumber: Int? = null,
        fitNumber: Int? = null,
        entryNumber: Int? = null,
    ) : EsfException(reason, lineNumber, fitNumber, entryNumber)

/**
 * The fit breaks a rule of esf/1.
 *
 * The document is well formed and the catalog knows every type, but the fit is not valid. For
 * example, a module is in the wrong rack.
 */
public class EsfRuleException
    @JvmOverloads
    constructor(
        reason: String,
        lineNumber: Int? = null,
        fitNumber: Int? = null,
        entryNumber: Int? = null,
    ) : EsfException(reason, lineNumber, fitNumber, entryNumber)

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
