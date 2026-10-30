package com.example.ui.components

import androidx.compose.material3.LocalContentColor
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.unit.dp
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import com.example.R

internal fun fitMangoIconResource(name: String): Int? = when (name.substringAfterLast('.')) {
    "Home", "HomeFilled" -> R.drawable.fit_icon_home
    "CalendarMonth", "CalendarToday", "DateRange" -> R.drawable.fit_icon_calendar
    "RestaurantMenu", "MenuBook", "Restaurant" -> R.drawable.fit_icon_recipes
    "ShoppingCart", "ShoppingBag" -> R.drawable.fit_icon_shopping
    "Person", "AccountCircle" -> R.drawable.fit_icon_profile
    "SmartToy", "AutoAwesome" -> R.drawable.fit_icon_ai
    "PictureAsPdf", "UploadFile" -> R.drawable.fit_icon_pdf
    "Description", "Article" -> R.drawable.fit_icon_document
    "WaterDrop", "LocalDrink" -> R.drawable.fit_icon_water
    "MonitorWeight", "Scale" -> R.drawable.fit_icon_weight
    "Straighten" -> R.drawable.fit_icon_measure
    "Favorite" -> R.drawable.fit_icon_favorite
    "FavoriteBorder" -> R.drawable.fit_icon_favorite_outline
    "Add", "AddCircle", "AddCircleOutline" -> R.drawable.fit_icon_add
    "Edit", "EditNote" -> R.drawable.fit_icon_edit
    "Delete", "DeleteOutline", "DeleteForever" -> R.drawable.fit_icon_delete
    "SwapHoriz", "SwapVert", "Sync" -> R.drawable.fit_icon_swap
    "Check", "CheckCircle", "CheckCircleOutline", "Done" -> R.drawable.fit_icon_check
    "Close", "Cancel" -> R.drawable.fit_icon_close
    "Notifications", "NotificationsNone" -> R.drawable.fit_icon_bell
    "Schedule", "AccessTime" -> R.drawable.fit_icon_clock
    "Language", "Public" -> R.drawable.fit_icon_globe
    "CameraAlt", "PhotoCamera", "AddAPhoto" -> R.drawable.fit_icon_camera
    "LocalFireDepartment" -> R.drawable.fit_icon_fire
    "FitnessCenter" -> R.drawable.fit_icon_mass
    "TrendingUp", "ShowChart", "BarChart" -> R.drawable.fit_icon_progress
    "Flag", "TrackChanges" -> R.drawable.fit_icon_goal
    "Warning", "ErrorOutline" -> R.drawable.fit_icon_warning
    "Link" -> R.drawable.fit_icon_link
    "DirectionsWalk", "DirectionsRun" -> R.drawable.fit_clay_walk
    "Bedtime", "NightsStay" -> R.drawable.fit_clay_sleep
    "EmojiEvents", "MilitaryTech" -> R.drawable.fit_clay_medal
    "QrCodeScanner", "QrCode", "DocumentScanner" -> R.drawable.fit_icon_camera
    else -> null
}

/** Packaged offline renders; decode small thumbnails instead of keeping full source images in RAM. */
private val clayBitmapCache = object : LruCache<Int, Bitmap>(8 * 1024 * 1024) {
    override fun sizeOf(key: Int, value: Bitmap): Int = value.allocationByteCount
}

internal fun fitMangoClayResource(resource: Int): Int? = when (resource) {
    R.drawable.fit_icon_add -> R.drawable.fit_clay_add
    R.drawable.fit_icon_ai -> R.drawable.fit_clay_ai
    R.drawable.fit_icon_balance -> R.drawable.fit_clay_balance
    R.drawable.fit_icon_bell -> R.drawable.fit_clay_bell
    R.drawable.fit_icon_breakfast -> R.drawable.fit_clay_breakfast
    R.drawable.fit_icon_calendar -> R.drawable.fit_clay_calendar
    R.drawable.fit_icon_camera -> R.drawable.fit_clay_camera
    R.drawable.fit_icon_carbs -> R.drawable.fit_clay_carbs
    R.drawable.fit_icon_check -> R.drawable.fit_clay_check
    R.drawable.fit_icon_clock -> R.drawable.fit_clay_clock
    R.drawable.fit_icon_close -> R.drawable.fit_clay_close
    R.drawable.fit_icon_creatine -> R.drawable.fit_clay_creatine
    R.drawable.fit_icon_dairy -> R.drawable.fit_clay_dairy
    R.drawable.fit_icon_delete -> R.drawable.fit_clay_delete
    R.drawable.fit_icon_dinner -> R.drawable.fit_clay_dinner
    R.drawable.fit_icon_document -> R.drawable.fit_clay_document
    R.drawable.fit_icon_drink -> R.drawable.fit_clay_drink
    R.drawable.fit_icon_edit -> R.drawable.fit_clay_edit
    R.drawable.fit_icon_fasting -> R.drawable.fit_clay_fasting
    R.drawable.fit_icon_fat -> R.drawable.fit_clay_fat
    R.drawable.fit_icon_favorite -> R.drawable.fit_clay_favorite
    R.drawable.fit_icon_favorite_outline -> R.drawable.fit_clay_favorite_outline
    R.drawable.fit_icon_fire -> R.drawable.fit_clay_fire
    R.drawable.fit_icon_globe -> R.drawable.fit_clay_globe
    R.drawable.fit_icon_goal -> R.drawable.fit_clay_goal
    R.drawable.fit_icon_health -> R.drawable.fit_clay_health
    R.drawable.fit_icon_home -> R.drawable.fit_clay_home
    R.drawable.fit_icon_link -> R.drawable.fit_clay_link
    R.drawable.fit_icon_mango -> R.drawable.fit_clay_mango
    R.drawable.fit_icon_mass -> R.drawable.fit_clay_mass
    R.drawable.fit_icon_meal -> R.drawable.fit_clay_meal
    R.drawable.fit_icon_measure -> R.drawable.fit_clay_measure
    R.drawable.fit_icon_pdf -> R.drawable.fit_clay_pdf
    R.drawable.fit_icon_profile -> R.drawable.fit_clay_profile
    R.drawable.fit_icon_progress -> R.drawable.fit_clay_progress
    R.drawable.fit_icon_protein -> R.drawable.fit_clay_protein
    R.drawable.fit_icon_recipes -> R.drawable.fit_clay_recipes
    R.drawable.fit_icon_reduction -> R.drawable.fit_clay_reduction
    R.drawable.fit_icon_shopping -> R.drawable.fit_clay_shopping
    R.drawable.fit_icon_snack -> R.drawable.fit_clay_snack
    R.drawable.fit_icon_swap -> R.drawable.fit_clay_swap
    R.drawable.fit_icon_vegetable -> R.drawable.fit_clay_vegetable
    R.drawable.fit_icon_warning -> R.drawable.fit_clay_warning
    R.drawable.fit_icon_water -> R.drawable.fit_clay_water
    R.drawable.fit_icon_weight -> R.drawable.fit_clay_weight
    else -> null
}

@Composable
private fun fitMangoPainter(resource: Int): Painter {
    val clay = fitMangoClayResource(resource) ?: resource.takeIf {
        it == R.drawable.fit_clay_walk || it == R.drawable.fit_clay_sleep || it == R.drawable.fit_clay_medal
    }
    if (clay == null) return painterResource(resource)
    val resources = LocalContext.current.resources
    val bitmap = remember(resources, clay) {
        synchronized(clayBitmapCache) {
            clayBitmapCache.get(clay) ?: run {
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true; inScaled = false }
                BitmapFactory.decodeResource(resources, clay, bounds)
                var sample = 1
                while (bounds.outWidth / sample > 192 || bounds.outHeight / sample > 192) sample *= 2
                val options = BitmapFactory.Options().apply { inSampleSize = sample; inScaled = false }
                val decoded = requireNotNull(BitmapFactory.decodeResource(resources, clay, options)) {
                    "Cannot decode packaged FITMango icon"
                }
                clayBitmapCache.put(clay, decoded)
                decoded
            }
        }
    }
    return remember(bitmap) { BitmapPainter(bitmap.asImageBitmap()) }
}

/** Keep existing action semantics and fall back to Material for icons outside the brand set. */
@Composable
fun FitMangoIcon(imageVector: ImageVector, contentDescription: String?, modifier: Modifier = Modifier,
    tint: Color = LocalContentColor.current, colorful: Boolean = false) {
    val resource = fitMangoIconResource(imageVector.name)
    if (resource != null && colorful) {
        androidx.compose.material3.Icon(painter = fitMangoPainter(resource), contentDescription = contentDescription,
            modifier = modifier.then(Modifier.size(28.dp)), tint = Color.Unspecified)
    } else {
        val simple = when (imageVector.name.substringAfterLast('.')) {
            "Edit", "EditNote" -> Icons.Outlined.Edit
            "Search" -> Icons.Outlined.Search
            "Add", "AddCircle", "AddCircleOutline" -> Icons.Outlined.Add
            "Delete", "DeleteOutline", "DeleteForever" -> Icons.Outlined.DeleteOutline
            "Close", "Cancel" -> Icons.Outlined.Close
            "Check", "Done" -> Icons.Outlined.Check
            "CheckCircle", "CheckCircleOutline" -> Icons.Outlined.CheckCircle
            "MoreVert" -> Icons.Outlined.MoreVert
            "DriveFileMove" -> Icons.Outlined.DriveFileMove
            "MenuBook" -> Icons.Outlined.MenuBook
            "SwapHoriz" -> Icons.Outlined.SwapHoriz
            else -> imageVector
        }
        androidx.compose.material3.Icon(imageVector = simple, contentDescription = contentDescription,
            modifier = modifier, tint = tint)
    }
}

@Composable
fun FitMangoAssetIcon(resource: Int, contentDescription: String? = null, modifier: Modifier = Modifier) {
    androidx.compose.material3.Icon(fitMangoPainter(resource), contentDescription,
        modifier.then(Modifier.size(28.dp)), tint = Color.Unspecified)
}

internal fun fitMangoCategoryResource(label: String): Int? = when (label.trim().lowercase()) {
    "śniadanie", "śniadania" -> R.drawable.fit_icon_breakfast
    "obiad", "obiady" -> R.drawable.fit_icon_meal
    "kolacja", "kolacje" -> R.drawable.fit_icon_dinner
    "przekąska", "przekąski", "owoce", "orzechy i nasiona" -> R.drawable.fit_icon_snack
    "warzywa" -> R.drawable.fit_icon_vegetable
    "nabiał" -> R.drawable.fit_icon_dairy
    "produkty białkowe", "mięso i ryby" -> R.drawable.fit_icon_protein
    "produkty zbożowe", "zboża" -> R.drawable.fit_icon_carbs
    "tłuszcze" -> R.drawable.fit_icon_fat
    "napoje" -> R.drawable.fit_icon_drink
    "ulubione" -> R.drawable.fit_icon_favorite
    else -> null
}
