package com.pirxhio.affirmity.data.catalog

import org.junit.Assert.assertEquals
import org.junit.Test

class CatalogLocaleTest {

    private var deviceCalls = 0
    private fun device(language: String?): () -> String? = {
        deviceCalls++
        language
    }

    @Test
    fun `app en with device es resolves EN`() {
        assertEquals(CatalogLocale.EN, CatalogLocale.resolve("en", device("es")))
    }

    @Test
    fun `app es with device en resolves ES - app locale wins`() {
        assertEquals(CatalogLocale.ES, CatalogLocale.resolve("es", device("en")))
    }

    @Test
    fun `unsupported app locale resolves ES and never consults the device`() {
        assertEquals(CatalogLocale.ES, CatalogLocale.resolve("fr", device("en")))
        assertEquals(0, deviceCalls)
    }

    @Test
    fun `unset app locale falls to the device language`() {
        assertEquals(CatalogLocale.EN, CatalogLocale.resolve(null, device("en")))
        assertEquals(CatalogLocale.ES, CatalogLocale.resolve(null, device("es")))
        assertEquals(CatalogLocale.ES, CatalogLocale.resolve(null, device("fr")))
        assertEquals(CatalogLocale.ES, CatalogLocale.resolve(null, device(null)))
    }

    @Test
    fun `blank app tag is treated as unset`() {
        assertEquals(CatalogLocale.EN, CatalogLocale.resolve("  ", device("en")))
        assertEquals(CatalogLocale.EN, CatalogLocale.resolve("", device("en")))
    }

    @Test
    fun `fromLanguageTag maps en regions to EN and everything else to ES`() {
        assertEquals(CatalogLocale.EN, CatalogLocale.fromLanguageTag("en"))
        assertEquals(CatalogLocale.EN, CatalogLocale.fromLanguageTag("en-GB"))
        assertEquals(CatalogLocale.ES, CatalogLocale.fromLanguageTag("es-AR"))
        assertEquals(CatalogLocale.ES, CatalogLocale.fromLanguageTag("fr"))
        assertEquals(CatalogLocale.ES, CatalogLocale.fromLanguageTag(null))
    }

    @Test
    fun `tag and asset name per locale`() {
        assertEquals("es" to "catalog.v1.json", CatalogLocale.ES.tag to CatalogLocale.ES.assetName)
        assertEquals("en" to "catalog.v1.en.json", CatalogLocale.EN.tag to CatalogLocale.EN.assetName)
    }
}
