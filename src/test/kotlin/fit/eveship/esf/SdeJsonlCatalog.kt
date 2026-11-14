package fit.eveship.esf

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.double
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.File

/** A Catalog read from the official SDE in JSONL, unpacked into [dir]. */
class SdeJsonlCatalog(
    dir: File,
) : Catalog {
    private class Named(
        val id: Int,
        val name: String,
        val published: Boolean,
    )

    private class Dogma(
        val effects: List<Int>,
        val squadronSize: Int?,
        val rollable: Map<Int, Double>,
    )

    private val types = mutableMapOf<Int, TypeRecord>()
    private val attributes = mutableMapOf<Int, AttributeRecord>()
    private val typeNames: Map<String, Int>
    private val attributeNames: Map<String, Int>
    private val modes: Map<Int, List<TypeRecord>>
    private val mutaplasmids: Map<Int, List<MutaplasmidRecord>>

    init {
        fun rows(
            name: String,
            each: (JsonObject) -> Unit,
        ) = File(dir, name).forEachLine { each(Json.parseToJsonElement(it).jsonObject) }

        val categoryOfGroup = mutableMapOf<Int, Int>()
        rows("groups.jsonl") { categoryOfGroup[it.key] = it.int("categoryID") }

        val effectCategory = mutableMapOf<Int, Int>()
        rows("dogmaEffects.jsonl") { effectCategory[it.key] = it.intOrNull("effectCategoryID") ?: 0 }

        class Mutaplasmid(
            val id: Int,
            val bases: Set<Int>,
            val attributes: List<Int>,
        )
        val mutaplasmidRows = mutableListOf<Mutaplasmid>()
        rows("dynamicItemAttributes.jsonl") { m ->
            val bases =
                m
                    .getValue("inputOutputMapping")
                    .jsonArray
                    .flatMap { it.jsonObject.getValue("applicableTypes").jsonArray }
                    .map { it.jsonPrimitive.int }
                    .toSet()
            mutaplasmidRows.add(
                Mutaplasmid(m.key, bases, m.getValue("attributeIDs").jsonArray.map { it.jsonObject.key }),
            )
        }

        val rollable = mutaplasmidRows.flatMap { it.attributes }.toSet()
        val dogma = mutableMapOf<Int, Dogma>()
        rows("typeDogma.jsonl") { row ->
            val effects = row["dogmaEffects"]?.jsonArray?.map { it.jsonObject.int("effectID") }.orEmpty()
            val values =
                row["dogmaAttributes"]
                    ?.jsonArray
                    ?.associate { it.jsonObject.int("attributeID") to it.jsonObject.double("value") }
                    .orEmpty()
            dogma[row.key] = Dogma(effects, Sde.squadronSize(values), values.filterKeys { it in rollable })
        }

        val typeRows = mutableListOf<Named>()
        val ships = mutableListOf<Named>()
        rows("types.jsonl") { t ->
            val name =
                t["name"]
                    ?.jsonObject
                    ?.get("en")
                    ?.jsonPrimitive
                    ?.content ?: return@rows
            val groupId = t.int("groupID")
            val categoryId = categoryOfGroup.getValue(groupId)
            val d = dogma[t.key]
            val effects = d?.effects.orEmpty()
            types[t.key] =
                TypeRecord(
                    id = t.key,
                    name = name,
                    kind = Sde.kind(categoryId, groupId, effects),
                    active = Sde.active(effects.associateWith { effectCategory[it] ?: 0 }),
                    capacity = t.double("capacity"),
                    volume = t.double("volume"),
                    squadronSize = d?.squadronSize,
                )
            val named = Named(t.key, name, t.published)
            typeRows.add(named)
            if (categoryId == 6) {
                ships.add(named)
            }
        }
        typeNames = Sde.nameIndex(typeRows, { it.id }, { it.name }, { it.published })

        val attributeRows = mutableListOf<Named>()
        val defaults = mutableMapOf<Int, Double>()
        rows("dogmaAttributes.jsonl") { a ->
            val name = a.getValue("name").jsonPrimitive.content
            attributes[a.key] = AttributeRecord(a.key, name)
            attributeRows.add(Named(a.key, name, a.published))
            defaults[a.key] = a.double("defaultValue")
        }
        attributeNames = Sde.nameIndex(attributeRows, { it.id }, { it.name }, { it.published })

        val shipNames = Sde.nameIndex(ships, { it.id }, { it.name }, { it.published })
        modes =
            types.values
                .filter { it.kind == Kind.MODE }
                .mapNotNull { mode -> Sde.modeHull(mode.name, shipNames)?.let { it to mode } }
                .groupBy({ it.first }, { it.second })

        mutaplasmids =
            mutaplasmidRows
                .sortedBy { it.id }
                .flatMap { m ->
                    m.bases.map { base ->
                        val values = dogma[base]?.rollable.orEmpty()
                        val rolls = m.attributes.associateWith { values[it] ?: defaults[it] ?: 0.0 }
                        base to MutaplasmidRecord(types.getValue(m.id), rolls)
                    }
                }.groupBy({ it.first }, { it.second })
    }

    override fun type(foldedName: String): TypeRecord? = typeNames[foldedName]?.let(types::get)

    override fun typeById(typeId: Int): TypeRecord? = types[typeId]

    override fun attribute(foldedName: String): AttributeRecord? = attributeNames[foldedName]?.let(attributes::get)

    override fun attributeById(attributeId: Int): AttributeRecord? = attributes[attributeId]

    override fun modes(hullId: Int): List<TypeRecord> = modes[hullId].orEmpty()

    override fun mutaplasmids(typeId: Int): List<MutaplasmidRecord> = mutaplasmids[typeId].orEmpty()

    private companion object {
        val JsonObject.key: Int get() = int("_key")

        val JsonObject.published: Boolean get() = this["published"]?.jsonPrimitive?.boolean ?: false

        fun JsonObject.int(key: String): Int = getValue(key).jsonPrimitive.int

        fun JsonObject.intOrNull(key: String): Int? = this[key]?.jsonPrimitive?.int

        fun JsonObject.double(key: String): Double = this[key]?.jsonPrimitive?.double ?: 0.0
    }
}
