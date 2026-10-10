package com.pirxhio.affirmity.data.catalog

import android.content.Context

/**
 * Narrow contract extracted so `CatalogSeeder` can be unit-tested with a fake, without needing an
 * Android `AssetManager` (mirrors `GroupSelectionPreferences`'s testability split, design risk
 * #4). The real implementation reads the bundled per-locale asset (design D2).
 */
fun interface CatalogAssetReader {
    fun readCatalogJson(locale: CatalogLocale): String
}

class AndroidCatalogAssetReader(private val context: Context) : CatalogAssetReader {
    override fun readCatalogJson(locale: CatalogLocale): String =
        context.assets.open(locale.assetName).bufferedReader(Charsets.UTF_8).use { it.readText() }
}
