package fit.eveship.esf

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.double
import kotlinx.serialization.json.int
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.File

/** A Catalog read from the esf-format test catalog. */
class JsonCatalog(
    path: String,
) : Catalog {
    private val types: Map<Int, TypeRecord>
    private val attributes: Map<Int, AttributeRecord>
    private val typeNames: Map<String, Int>
    private val attributeNames: Map<String, Int>
    private val modes: Map<Int, List<TypeRecord>>
    private val mutaplasmids: Map<Int, List<MutaplasmidRecord>>

    init {
        val data = Json.parseToJsonElement(File(path).readText()).jsonObject
        val typeList = data.list("types")
        val attributeList = data.list("attributes")
        types =
            typeList.associate {
                it.int("id") to
                    TypeRecord(
                        it.int("id"),
                        it.string("name"),
                        Kind.valueOf(it.string("kind").uppercase()),
                        it.getValue("active").jsonPrimitive.boolean,
                        it.getValue("capacity").jsonPrimitive.double,
                        it.getValue("volume").jsonPrimitive.double,
                        it["squadron_size"]?.jsonPrimitive?.intOrNull,
                    )
            }
        attributes = attributeList.associate { it.int("id") to AttributeRecord(it.int("id"), it.string("name")) }
        typeNames = byName(typeList)
        attributeNames = byName(attributeList)
        modes =
            data.list("modes").associate { m ->
                m.int("hull") to m.getValue("modes").jsonArray.map { types.getValue(it.jsonPrimitive.int) }
            }
        mutaplasmids =
            data.list("mutaplasmids").groupBy({ it.int("base") }) { m ->
                val rolls =
                    m.getValue("rolls").jsonArray.associate { roll ->
                        val (attributeId, base) = roll.jsonArray
                        attributeId.jsonPrimitive.int to base.jsonPrimitive.double
                    }
                MutaplasmidRecord(types.getValue(m.int("mutaplasmid")), rolls)
            }
    }

    override fun type(folded: String): TypeRecord? = typeNames[folded]?.let(types::get)

    override fun typeById(typeId: Int): TypeRecord? = types[typeId]

    override fun attribute(folded: String): AttributeRecord? = attributeNames[folded]?.let(attributes::get)

    override fun attributeById(attributeId: Int): AttributeRecord? = attributes[attributeId]

    override fun modes(hullId: Int): List<TypeRecord> = modes[hullId].orEmpty()

    override fun mutaplasmids(typeId: Int): List<MutaplasmidRecord> = mutaplasmids[typeId].orEmpty()

    private companion object {
        fun JsonObject.list(key: String): List<JsonObject> = getValue(key).jsonArray.map { it.jsonObject }

        fun JsonObject.int(key: String): Int = getValue(key).jsonPrimitive.int

        fun JsonObject.string(key: String): String = getValue(key).jsonPrimitive.content

        /** Where names are shared, published wins, then the lowest ID (§4.2). */
        fun byName(entries: List<JsonObject>): Map<String, Int> {
            val index = mutableMapOf<String, Int>()
            entries
                .sortedWith(compareBy({ !it.getValue("published").jsonPrimitive.boolean }, { it.int("id") }))
                .forEach { index.putIfAbsent(Esf.fold(it.string("name")), it.int("id")) }
            return index
        }
    }
}
