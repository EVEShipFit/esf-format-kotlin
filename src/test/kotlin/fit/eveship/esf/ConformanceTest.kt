package fit.eveship.esf

import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.TestFactory
import java.io.File
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/** The esf-format test cases, the way its runner checks them. */
class ConformanceTest {
    @TestFactory
    fun cases(): List<DynamicTest> {
        val root = File(catalogPath).parentFile
        return listOf("valid", "invalid").flatMap { kind ->
            File(root, kind).listFiles { f -> f.isDirectory }!!.sorted().map { path ->
                DynamicTest.dynamicTest("$kind/${path.name}") { check(path, kind == "valid") }
            }
        }
    }

    private fun input(path: File): ByteArray {
        val esf = File(path, "input.esf")
        return if (esf.isFile) esf.readBytes() else Esf.fromBase64Url(File(path, "input.b64").readText())
    }

    private fun check(
        path: File,
        valid: Boolean,
    ) {
        val input = input(path)
        if (!valid) {
            assertFailsWith<EsfException> { Esf.load(input, catalog) }
            return
        }

        val canonical = File(path, "canonical.esf").readText()
        val binary = Esf.fromBase64Url(File(path, "canonical.b64").readText())
        val fits = Esf.load(input, catalog)
        assertEquals(canonical, Esf.save(fits, catalog), "canonical of input")
        assertEquals(canonical, Esf.save(Esf.load(canonical, catalog), catalog), "canonical of canonical.esf")
        assertContentEquals(binary, Esf.saveBinary(fits, catalog), "binary of input")
        assertEquals(canonical, Esf.save(Esf.load(binary, catalog), catalog), "canonical of canonical.b64")
    }
}
