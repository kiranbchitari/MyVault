package com.myvault.app

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * The app handles nothing but secrets, so production code does not log at all. This guard fails
 * the build if a logging or printing call is introduced in either module's main sources.
 */
class NoLoggingTest {
    private val forbidden = listOf(
        Regex("""\bLog\.[vdiwe]\("""),
        Regex("""\bprintln\("""),
        Regex("""\bprint\("""),
        Regex("""printStackTrace\("""),
        Regex("""\bTimber\."""),
        Regex("""System\.(out|err)\b"""),
    )

    @Test
    fun `production sources contain no logging calls`() {
        val roots = listOf(File("src/main"), File("../crypto/src/main"))
        roots.forEach { assertTrue("missing source root ${it.absolutePath}", it.isDirectory) }

        val sources = roots.flatMap { root -> root.walkTopDown().filter { it.extension == "kt" || it.extension == "java" } }
        assertTrue("expected to scan production sources", sources.size > 10)

        val offences = sources.flatMap { file ->
            file.readLines().mapIndexedNotNull { index, line ->
                if (forbidden.any { it.containsMatchIn(line) }) "${file.path}:${index + 1}: ${line.trim()}" else null
            }
        }
        assertTrue("Logging found in production code:\n" + offences.joinToString("\n"), offences.isEmpty())
    }
}
