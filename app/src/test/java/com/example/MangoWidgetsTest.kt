package com.example

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import androidx.test.core.app.ApplicationProvider
import android.content.Context
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class MangoWidgetsTest {
    @Test fun allWidgetProvidersAreDeclaredInManifest() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val packageInfo = context.packageManager.getPackageInfo(
            context.packageName, android.content.pm.PackageManager.GET_RECEIVERS)
        val receivers = packageInfo.receivers.orEmpty().map { it.name }.toSet()
        assert(receivers.contains(MangoHomeWidget::class.java.name))
        assert(receivers.contains(MangoMealsWidget::class.java.name))
        assert(receivers.contains(MangoWorkoutWidget::class.java.name))
    }
    @Test fun widgetProviderClassesCanBeInstantiated() {
        assertNotNull(MangoHomeWidget())
        assertNotNull(MangoMealsWidget())
        assertNotNull(MangoWorkoutWidget())
    }
}
