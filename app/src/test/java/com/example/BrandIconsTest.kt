package com.example

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.unit.dp
import com.example.ui.components.FitMangoAssetIcon
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class BrandIconsTest {
    @get:Rule val compose = createComposeRule()

    @Test fun allBrandVectorsLoadThroughComposeAndKeepDescriptions() {
        val icons = R.drawable::class.java.fields.filter { it.name.startsWith("fit_icon_") }
            .sortedBy { it.name }.map { it.name to it.getInt(null) }
        assertEquals(45, icons.size)
        compose.setContent {
            Column {
                icons.chunked(8).forEach { group ->
                    Row {
                        group.forEach { (name, resource) ->
                            FitMangoAssetIcon(resource, name, Modifier.size(32.dp))
                        }
                    }
                }
            }
        }
        icons.forEach { (name, _) -> compose.onNodeWithContentDescription(name).assertExists() }
    }
}
