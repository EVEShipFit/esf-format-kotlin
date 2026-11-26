package fit.eveship.esf

import fit.eveship.esf.internal.CanonFit
import fit.eveship.esf.internal.FitBlock
import fit.eveship.esf.internal.ForeignBlock
import fit.eveship.esf.internal.UINT32_MAX
import fit.eveship.esf.internal.canonicalFit
import fit.eveship.esf.internal.decodeBinary
import fit.eveship.esf.internal.encodeBinary
import fit.eveship.esf.internal.encodeField
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
    ): List<EsfFit> = read(data, catalog).fits

    /** Read a text document, in canonical form. */
    @JvmStatic
    public fun load(
        data: String,
        catalog: Catalog,
    ): List<EsfFit> = load(utf8Of(data), catalog)

    /**
     * Read a text or binary document, with the data that is not a fit.
     *
     * [load] skips text blocks with a header other than `%esf/N`, and binary `Document`
     * fields other than `fits`. This returns them too, in [Document.extra]. Its fits are
     * the same as what [load] returns. It throws the same errors as [load], and one more:
     * a [EsfFormatException] for a block whose version is larger than 4294967295, which
     * [load] skips.
     */
    @JvmStatic
    public fun loadDocument(
        data: ByteArray,
        catalog: Catalog,
    ): Document {
        val read = read(data, catalog)
        return Document(read.fits, read.foreign.map { it.toExtra() } + read.binary)
    }

    /** Read a text document, with the data that is not a fit. */
    @JvmStatic
    public fun loadDocument(
        data: String,
        catalog: Catalog,
    ): Document = loadDocument(utf8Of(data), catalog)

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

    /**
     * A binary `Document` field with number [field] and content [bytes], as in [Extra.Binary].
     *
     * Append it to the output of [saveBinary] to store other data next to the fits.
     * [field] must be 2 or more, and at most 536870911: field 1 holds the fits.
     * Throws [IllegalArgumentException] otherwise.
     */
    @JvmStatic
    public fun binaryField(
        field: Int,
        bytes: ByteArray,
    ): ByteArray {
        require(field in 2..MAX_FIELD) { "field must be from 2 to $MAX_FIELD, not $field" }
        return encodeField(field, bytes)
    }

    /** The binary form as base64url without padding, for URLs (§10.4). */
    @JvmStatic
    public fun toBase64Url(data: ByteArray): String = Base64.getUrlEncoder().withoutPadding().encodeToString(data)

    /** The binary form from base64url without padding (§10.4). */
    @JvmStatic
    public fun fromBase64Url(encoded: String): ByteArray {
        val trimmed = encoded.trim()
        if (trimmed.length % 4 == 1 || !trimmed.all { it in BASE64URL }) {
            throw EsfFormatException("not valid base64url")
        }
        return Base64.getUrlDecoder().decode(trimmed)
    }

    /** Unicode simple case folding, for indexing a [Catalog] by name (§6.10). */
    @JvmStatic
    public fun fold(name: String): String = foldName(name)

    private const val MAX_FIELD = (1 shl 29) - 1

    private const val BASE64URL = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-_"

    private fun read(
        data: ByteArray,
        catalog: Catalog,
    ): Read {
        if (data.isEmpty()) {
            throw EsfFormatException("the document is empty")
        }
        if (isText(data)) {
            val (blocks, foreign) = parseText(textOf(data))
            return Read(canonical(blocks, catalog).map { it.toModel() }, foreign, emptyList())
        }
        val (fits, binary) = decodeBinary(data)
        return Read(checked(fits, catalog).map { it.toModel() }, emptyList(), binary)
    }

    private class Read(
        val fits: List<EsfFit>,
        val foreign: List<ForeignBlock>,
        val binary: List<Extra>,
    )

    private fun ForeignBlock.toExtra(): Extra {
        val number = version.toLongOrNull()?.takeIf { it <= UINT32_MAX }
        return Extra.Text(
            name,
            number ?: throw EsfFormatException("the version of %$name is too large", this.number),
            text,
        )
    }

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
            return canonical(parseText(text).first, catalog, fits)
        } catch (e: EsfException) {
            val line = e.lineNumber ?: throw e
            val at = where[line - 1]
            throw e.at(at.fit, at.entry)
        }
    }
}
