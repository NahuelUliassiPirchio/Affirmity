package com.pirxhio.affirmity.auth

import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Resources resolved by name (`Resources.getIdentifier`) are invisible to the resource shrinker, so
 * optimized builds strip them unless they are listed in `res/raw/keep.xml`.
 */
class ResourceKeepRulesTest {

    private val moduleDir: File =
        File(".").absoluteFile.let { cwd ->
            if (File(cwd, "src/main").isDirectory) cwd else File(cwd, "app")
        }

    private fun keptResources(): Set<String> {
        val keepFile = File(moduleDir, "src/main/res/raw/keep.xml")
        assertTrue("Missing ${keepFile.path}: resources looked up by name get shrunk in release.", keepFile.isFile)
        val factory = DocumentBuilderFactory.newInstance().apply { isNamespaceAware = true }
        val root = factory.newDocumentBuilder().parse(keepFile).documentElement
        return root.getAttributeNS(TOOLS_NS, "keep")
            .split(",")
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .toSet()
    }

    @Test
    fun keepFile_listsDefaultWebClientId() {
        assertTrue(
            "res/raw/keep.xml must contain tools:keep=\"@string/$DEFAULT_WEB_CLIENT_ID_RESOURCE\". " +
                "Without it the resource shrinker strips the id in release/benchmark builds and " +
                "Google sign-in breaks with ConfigurationMissing.",
            "@string/$DEFAULT_WEB_CLIENT_ID_RESOURCE" in keptResources(),
        )
    }

    @Test
    fun everyStringLookedUpByNameIsKept() {
        val kept = keptResources()
        val literal = Regex("""getIdentifier\(\s*"([^"]+)"\s*,\s*"string"""")
        val missing = File(moduleDir, "src/main/java").walkTopDown()
            .filter { it.extension == "kt" || it.extension == "java" }
            .flatMap { file -> literal.findAll(file.readText()).map { it.groupValues[1] } }
            .filter { "@string/$it" !in kept }
            .toSet()
        assertTrue(
            "String resources looked up via getIdentifier but missing from res/raw/keep.xml: $missing",
            missing.isEmpty(),
        )
    }

    private companion object {
        const val TOOLS_NS = "http://schemas.android.com/tools"
    }
}
