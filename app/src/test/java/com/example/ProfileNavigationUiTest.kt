package com.example

import android.app.Application
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.core.app.ApplicationProvider
import com.example.ui.screens.ProfileScreen
import com.example.ui.theme.MojaDietaAiTheme
import com.example.viewmodel.DietaViewModel
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], qualifiers = "w360dp-h640dp")
class ProfileNavigationUiTest {
    @get:Rule val compose = createComposeRule()
    private lateinit var app: Application
    @Before fun setup() {
        app = ApplicationProvider.getApplicationContext()
        app.getSharedPreferences("FITMangoProfileNavigation", 0).edit().clear().commit()
        app.getSharedPreferences("FITMangoMotion", 0).edit().putBoolean("reduced", true).commit()
    }
    private fun open() {
        val vm = DietaViewModel(app)
        compose.setContent { MojaDietaAiTheme { ProfileScreen(vm) } }
    }
    @Test fun appearanceHasBothThemeAndMotionControlsWithoutAnExtraExpansionStep() {
        open()
        compose.onNodeWithTag("profile_tile_calculator").assertExists()
        compose.onNodeWithTag("profile_tile_appearance").assertDoesNotExist()
        compose.onNodeWithTag("profile_tab_settings").performClick()
        compose.onNodeWithTag("profile_tile_appearance").performClick()
        compose.onNodeWithTag("theme_light").assertExists()
        compose.onNodeWithTag("profile_content").performScrollToNode(hasTestTag("reduce_animations"))
        compose.onNodeWithTag("reduce_animations").assertIsDisplayed()
        compose.onNodeWithTag("section_motion").assertDoesNotExist()
        compose.onNodeWithTag("profile_detail_back").performClick()
        compose.onNodeWithTag("profile_tab_settings").assertIsSelected()
    }
    @Test fun detailBackRestoresTheSelectedTabAndItsScrollPosition() {
        open()
        compose.onNodeWithTag("profile_tab_settings").performClick()
        compose.onNodeWithTag("profile_content").performScrollToNode(hasTestTag("profile_tile_about"))
        compose.onNodeWithTag("profile_tile_about").performClick()
        compose.onNodeWithTag("installed_build_label").assertExists()
        compose.onNodeWithTag("profile_detail_back").performClick()
        compose.onNodeWithTag("profile_tab_settings").assertIsSelected()
        compose.onNodeWithTag("profile_tile_about").assertIsDisplayed()
        compose.onNodeWithTag("profile_tab_progress").performClick()
        compose.onNodeWithTag("profile_tile_body_photos").assertExists()
        compose.onNodeWithTag("profile_tab_settings").performClick()
        compose.onNodeWithTag("profile_tile_about").assertIsDisplayed()
    }
    @Test fun quickPhotoActionOpensTheNewSetFormAndReturnsToTheOriginalTab() {
        open()
        compose.onNodeWithTag("profile_quick_body_photos_new").performClick()
        compose.onNodeWithText("Nowy zestaw • dzisiaj").assertIsDisplayed()
        compose.onNodeWithTag("section_body_photos").assertDoesNotExist()
        compose.onNodeWithTag("profile_detail_back").performClick()
        compose.onNodeWithTag("profile_tab_profile").assertIsSelected()
    }
    @Test fun selectedTabAndDetailReturnSurviveStateRecreation() {
        val vm = DietaViewModel(app)
        val restoration = StateRestorationTester(compose)
        restoration.setContent { MojaDietaAiTheme { ProfileScreen(vm) } }
        compose.onNodeWithTag("profile_tab_settings").performClick()
        compose.onNodeWithTag("profile_tile_appearance").performClick()
        restoration.emulateSavedInstanceStateRestore()
        compose.onNodeWithTag("theme_light").assertExists()
        compose.onNodeWithTag("profile_detail_back").performClick()
        compose.onNodeWithTag("profile_tab_settings").assertIsSelected()
    }
    @Test fun aSavedTabIsUsedOnOpeningTheProfile() {
        app.getSharedPreferences("FITMangoProfileNavigation", 0).edit().putString("tab", "progress").commit()
        open()
        compose.onNodeWithTag("profile_tab_progress").assertIsSelected()
        compose.onNodeWithTag("profile_tile_measurements").assertExists()
        compose.onNodeWithTag("profile_tile_calculator").assertDoesNotExist()
    }
}
