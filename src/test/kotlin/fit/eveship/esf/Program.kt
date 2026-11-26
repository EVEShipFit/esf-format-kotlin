// The program the esf-format test runner calls: program check|canonical|binary|extra.

package fit.eveship.esf

import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlin.system.exitProcess

fun main(args: Array<String>) {
    val catalog = JsonCatalog(System.getenv("ESF_CATALOG"))
    try {
        val data = System.`in`.readBytes()
        if (args.firstOrNull() == "extra") {
            Esf
                .loadDocument(
                    data,
                    catalog,
                ).extra
                .forEach { System.out.write((json(it).toString() + "\n").encodeToByteArray()) }
            System.out.flush()
            return
        }
        val fits = Esf.load(data, catalog)
        when (args.firstOrNull()) {
            "canonical" -> System.out.write(Esf.save(fits, catalog).encodeToByteArray())
            "binary" -> System.out.write(Esf.saveBinary(fits, catalog))
        }
        System.out.flush()
    } catch (e: EsfException) {
        System.err.println("invalid: ${e.message}")
        exitProcess(1)
    }
}

private fun json(extra: Extra) =
    buildJsonObject {
        when (extra) {
            is Extra.Text -> {
                put("name", JsonPrimitive(extra.name))
                put("version", JsonPrimitive(extra.version))
                put("text", JsonPrimitive(extra.text))
            }

            is Extra.Binary -> {
                put("field", JsonPrimitive(extra.field))
                put("bytes", JsonPrimitive(Esf.toBase64Url(extra.bytes)))
            }
        }
    }
