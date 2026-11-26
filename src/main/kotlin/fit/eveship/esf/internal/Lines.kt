package fit.eveship.esf.internal

import fit.eveship.esf.State

// A text document as lines, before the Catalog (SPEC §4.1).

internal class FitBlock(
    val number: Int,
    val hullLine: Int,
) {
    var hull: TypeRef? = null
    var fitName: String? = null
    var mode: String? = null
    val lines = mutableListOf<Line>()
}

internal sealed interface Line {
    val number: Int
    val count: Long?
    val index: Long?
}

internal class EmptyLine(
    override val number: Int,
    val rack: Place,
    override val count: Long?,
    override val index: Long?,
) : Line

internal class ItemLine(
    override val number: Int,
    val type: TypeRef,
    override val count: Long?,
) : Line {
    var fitName: String? = null
    var charge: TypeRef? = null
    var chargeCount: Long? = null
    var mutaplasmid: String? = null
    var overrides: List<Pair<String, String>> = emptyList()
    var state: State? = null
    var location: Place? = null
    override var index: Long? = null
}

/** A type as written: its name, and the type ID after it if there is one (§6.13). */
internal class TypeRef(
    val name: String,
    val id: Long? = null,
)

/** A block whose header is not `%esf/N`, with its version as written. */
internal class ForeignBlock(
    val number: Int,
    val name: String,
    val version: String,
    val text: String,
)
