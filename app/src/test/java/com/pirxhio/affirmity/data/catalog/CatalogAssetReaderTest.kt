package com.pirxhio.affirmity.data.catalog

import android.content.Context
import android.content.res.AssetManager
import org.junit.Assert.assertEquals
import org.junit.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when` as whenever
import java.io.ByteArrayInputStream

class CatalogAssetReaderTest {

    private fun readerOver(vararg assets: Pair<String, String>): Pair<AndroidCatalogAssetReader, AssetManager> {
        val manager = mock(AssetManager::class.java)
        for ((name, body) in assets) {
            whenever(manager.open(name)).thenAnswer { ByteArrayInputStream(body.toByteArray()) }
        }
        val context = mock(Context::class.java)
        whenever(context.assets).thenReturn(manager)
        return AndroidCatalogAssetReader(context) to manager
    }

    @Test
    fun `reads the asset that belongs to the requested locale`() {
        val (reader, manager) = readerOver("catalog.v1.json" to "ES", "catalog.v1.en.json" to "EN")

        assertEquals("EN", reader.readCatalogJson(CatalogLocale.EN))
        assertEquals("ES", reader.readCatalogJson(CatalogLocale.ES))
        verify(manager).open("catalog.v1.en.json")
        verify(manager).open("catalog.v1.json")
    }
}
