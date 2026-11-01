package fit.eveship.esf

val catalogPath: String = System.getenv("ESF_CATALOG") ?: "esf-format/tests/catalog.json"

val catalog: JsonCatalog by lazy { JsonCatalog(catalogPath) }
