package com.pirxhio.affirmity.data.catalog

import android.content.Context

/**
 * Narrow contract extracted so `CatalogSeeder` can be unit-tested with a fake, without needing an
 * Android `AssetManager` (mirrors `GroupSelectionPreferences`'s testability split, design risk
 * #4). The real implementation reads the bundled `assets/catalog.v1.json` (design D2).
 */
fun interface CatalogAssetReader {
    fun readCatalogJson(): String
}

class AndroidCatalogAssetReader(private val context: Context, private val assetName: String = "catalog.v1.json") :
    CatalogAssetReader {
    override fun readCatalogJson(): String =
        context.assets.open(assetName).bufferedReader(Charsets.UTF_8).use { it.readText() }
}
