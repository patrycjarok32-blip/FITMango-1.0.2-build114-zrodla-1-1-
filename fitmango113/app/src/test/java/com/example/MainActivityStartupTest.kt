package com.example

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import org.junit.Rule
import org.junit.Test
import org.junit.After
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import androidx.lifecycle.ViewModelProvider
import com.example.viewmodel.DietaViewModel
import android.os.Looper
import org.robolectric.Shadows

/** Exercises the launcher Activity, including its startup code and ViewModel creation. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33, 36], qualifiers = "w360dp-h800dp")
class MainActivityStartupTest {
    private val originalHandler = Thread.getDefaultUncaughtExceptionHandler()
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @After fun restoreHandler() { Thread.setDefaultUncaughtExceptionHandler(originalHandler) }

    @Test fun launcherDisplaysHomeAndOpensDatabase() {
        val viewModel = ViewModelProvider(compose.activity)[DietaViewModel::class.java]
        compose.waitUntil(10000) {
            Shadows.shadowOf(Looper.getMainLooper()).idle()
            viewModel.recipes.value.isNotEmpty() && viewModel.userProfile.value != null
        }
        compose.onNodeWithText("FITMango").assertIsDisplayed()
    }
}
