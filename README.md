# esf-format

[![Maven Central](https://img.shields.io/maven-central/v/fit.eveship/esf-format.svg)](https://central.sonatype.com/artifact/fit.eveship/esf-format)
[![CI](https://github.com/EVEShipFit/esf-format-kotlin/actions/workflows/test.yml/badge.svg)](https://github.com/EVEShipFit/esf-format-kotlin/actions/workflows/test.yml)

[![Discord](https://img.shields.io/badge/Discord-Join-5865F2?logo=discord&logoColor=white)](https://discord.gg/S5V5BkvNf7)

Read and write [esf/1](https://github.com/EVEShipFit/esf-format), the
plain-text format for EVE Online ship fits. Text, canonical and binary forms.
No dependencies beyond the Kotlin standard library. Java 17 or later.

```kotlin
dependencies {
    implementation("fit.eveship:esf-format:1.0.0")
}
```

## Usage

```kotlin
import fit.eveship.esf.Entry
import fit.eveship.esf.Esf
import fit.eveship.esf.EsfFit
import fit.eveship.esf.Location
import fit.eveship.esf.State

val fit = EsfFit(
    hull = 587, // Rifter
    name = "Shield Buffer",
    entries = listOf(
        Entry(2889, count = 3, charge = 21898), // 3 guns, a full load of ammo each
        Entry(null, location = Location.HIGH), // an empty high slot
        Entry(448, state = State.OFF), // an offline module
        Entry(2048), // goes in its own rack
        Entry(2456, count = 5), // 5 drones
        Entry(21898, count = 1000, location = Location.CARGO),
    ),
)

val text = Esf.save(listOf(fit), catalog) // canonical text
val data = Esf.saveBinary(listOf(fit), catalog) // binary
val fits = Esf.load(data, catalog) // text or binary

val url = Esf.toBase64Url(data) // the binary form in a URL
val bytes = Esf.fromBase64Url(url)
```

`save()` and `saveBinary()` take entries in any order, and check every rule
of the spec. `load()` returns fits in canonical form. All three throw
`EsfException` when a rule is broken. It says where: `lineNumber` for text, or
`fitNumber` and `entryNumber` (counting from 1) for fits.

## Fits

An `EsfFit` is one fit: `hull`, `name`, tactical `mode` and its `entries`.
`hull` is `null` for a fit without a ship. A document can hold more than one
fit.

Each `Entry` is one line of the fit. `null` means the default:

| field | what it is | `null` means |
| --- | --- | --- |
| `type` | The type ID. | An empty slot. |
| `count` | How many. | One; for fighters, a full squadron. |
| `fitName` | The name of another fit in the list, which this ship or container carries. | Carries no fit. |
| `charge` | The charge loaded. | |
| `chargeCount` | How many charges. | A full load. |
| `mutaplasmid` | The mutaplasmid applied. | |
| `overrides` | Attribute values by attribute ID. | |
| `state` | `State.OFF`, `ON` or `HEAT`. | Active if the type can be, else on. |
| `location` | `CARGO`, `BAY` or another hold; a rack only for an empty slot. | Where the type goes by default. |

## The Catalog

The library holds no game data. You give it a `Catalog`: an object that looks
up types and attributes in your copy of the SDE.

```kotlin
class MyCatalog : Catalog {
    override fun type(folded: String): TypeRecord? = ...
    override fun typeById(typeId: Int): TypeRecord? = ...
    override fun attribute(folded: String): AttributeRecord? = ...
    override fun attributeById(attributeId: Int): AttributeRecord? = ...

    // Optional. Without them, no hull has tactical modes and no type has mutaplasmids.
    override fun modes(hullId: Int): List<TypeRecord> = ...
    override fun mutaplasmids(typeId: Int): List<MutaplasmidRecord> = ...
}
```

Names are looked up folded: index them with `Esf.fold(name)`. Where
names are shared, published wins, then the lowest ID (§4.2).
[JsonCatalog.kt](https://github.com/EVEShipFit/esf-format-kotlin/blob/main/src/test/kotlin/fit/eveship/esf/JsonCatalog.kt) is a complete example.

## Tests

The test cases live in [esf-format](https://github.com/EVEShipFit/esf-format):

```
git clone --branch v1.0 https://github.com/EVEShipFit/esf-format
./gradlew program
uv run --project esf-format/tools esf-test -- build/program
./gradlew ktlintCheck
./gradlew test koverLog
```
