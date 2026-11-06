package com.pirxhio.affirmity.ui.meditation

import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import com.pirxhio.affirmity.ui.meditation.catalog.meditationCatalog
import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class MeditationCoverRenderTest {
    @get:Rule val compose = createComposeRule()

    @Test fun allFormsRenderAtShelfAndThumbnailSizes() {
        val ids = listOf("reset_rapido", "box_breathing", "calma", "zazen", "enfoque", "trataka",
            "vipassana", "body_scan", "walking_meditation", "dormir", "autocompasion_5", "metta")
        val entries = meditationCatalog().associateBy { it.id }
        compose.setContent {
            Column(Modifier.testTag("gallery").background(Color(0xFF07080A)).padding(8.dp)) {
                ids.chunked(3).forEach { row ->
                    Row {
                        row.forEach { id ->
                            Column(Modifier.padding(4.dp)) {
                                MeditationCover(entries.getValue(id), Modifier.size(108.dp).testTag(id))
                                Text(id.take(14), color = Color.White)
                            }
                        }
                    }
                }
                Row {
                    listOf(48, 64).forEach { size ->
                        MeditationCover(entries.getValue("trataka"), Modifier.size(size.dp).testTag("small$size"))
                    }
                }
            }
        }
        (ids + listOf("small48", "small64")).forEach { tag ->
            val pixels = compose.onNodeWithTag(tag).captureToImage().toPixelMap()
            var colored = 0
            for (y in 0 until pixels.height) for (x in 0 until pixels.width) {
                val p = pixels[x, y]
                if (maxOf(p.red, p.green, p.blue) > .35f) colored++
            }
            assertTrue("Visible vector artwork for $tag", colored > 10)
        }
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        File(context.cacheDir, "meditation-cover-gallery.png").outputStream().use {
            compose.onNodeWithTag("gallery").captureToImage().asAndroidBitmap()
                .compress(Bitmap.CompressFormat.PNG, 100, it)
        }
    }
}
