package fit.eveship.esf

import fit.eveship.esf.internal.CanonFit
import fit.eveship.esf.internal.FitBlock
import fit.eveship.esf.internal.canonicalFit
import fit.eveship.esf.internal.decodeBinary
import fit.eveship.esf.internal.encodeBinary
import fit.eveship.esf.internal.parseText
import fit.eveship.esf.internal.render
import fit.eveship.esf.internal.resolve
import fit.eveship.esf.internal.source
import fit.eveship.esf.internal.textOf
import fit.eveship.esf.internal.utf8Of
import fit.eveship.esf.internal.verify
import java.util.Base64
import fit.eveship.esf.internal.fold as foldName

/** Read and write esf/1 (https://github.com/EVEShipFit/esf-format). */
public object Esf {
    /** Whether a document is text, by its first character (§10). */
    @JvmStatic
    public fun isText(data: ByteArray): Boolean =
        data.isNotEmpty() && (data[0] == '%'.code.toByte() || data[0] == ' '.code.toByte())

    /** Read a text or binary document, in canonical form. */
    @JvmStatic
    public fun load(
        data: ByteArray,
        catalog: Catalog,
    ): List<EsfFit> {
        if (data.isEmpty()) {
            throw EsfException("the document is empty")
        }
        val fits =
            if (isText(data)) {
                canonical(parseText(textOf(data)), catalog)
            } else {
                checked(decodeBinary(data), catalog)
            }
        return fits.map { it.toModel() }
    }

    /** Read a text document, in canonical form. */
    @JvmStatic
    public fun load(
        data: String,
        catalog: Catalog,
    ): List<EsfFit> = load(utf8Of(data), catalog)

    /** Write fits as canonical text (§8). */
    @JvmStatic
    public fun save(
        fits: List<EsfFit>,
        catalog: Catalog,
    ): String = render(checked(fits, catalog))

    /** Write fits in the binary form (§10). */
    @JvmStatic
    public fun saveBinary(
        fits: List<EsfFit>,
        catalog: Catalog,
    ): ByteArray = encodeBinary(checked(fits, catalog).map { it.toModel() })

    /** The binary form as base64url without padding, for URLs (§10.4). */
    @JvmStatic
    public fun toBase64Url(data: ByteArray): String = Base64.getUrlEncoder().withoutPadding().encodeToString(data)

    /** The binary form from base64url without padding (§10.4). */
    @JvmStatic
    public fun fromBase64Url(encoded: String): ByteArray {
        val trimmed = encoded.trim()
        if (trimmed.length % 4 == 1 || !trimmed.all { it in BASE64URL }) {
            throw EsfException("not valid base64url")
        }
        return Base64.getUrlDecoder().decode(trimmed)
    }

    /** Unicode simple case folding, for indexing a [Catalog] by name (§6.10). */
    @JvmStatic
    public fun fold(name: String): String = foldName(name)

    private const val BASE64URL = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-_"

    private fun canonical(
        blocks: List<FitBlock>,
        catalog: Catalog,
        given: List<EsfFit>? = null,
    ): List<CanonFit> {
        val fits = resolve(blocks, catalog)
        given?.let { verify(fits, it) }
        return fits.map { canonicalFit(it, catalog) }
    }

    private fun checked(
        fits: List<EsfFit>,
        catalog: Catalog,
    ): List<CanonFit> {
        val (text, where) = source(fits, catalog)
        try {
            return canonical(parseText(text), catalog, fits)
        } catch (e: EsfException) {
            val line = e.lineNumber ?: throw e
            val at = where[line - 1]
            throw EsfException(e.reason, fitNumber = at.fit, entryNumber = at.entry)
        }
    }
}
