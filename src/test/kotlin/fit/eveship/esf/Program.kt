// The program the esf-format test runner calls: program check|canonical|binary.

package fit.eveship.esf

import kotlin.system.exitProcess

fun main(args: Array<String>) {
    val catalog = JsonCatalog(System.getenv("ESF_CATALOG"))
    try {
        val fits = Esf.load(System.`in`.readBytes(), catalog)
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
