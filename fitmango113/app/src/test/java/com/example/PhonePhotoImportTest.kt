package com.example

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import com.example.viewmodel.RecipePhotoSearch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class PhonePhotoImportTest {
    @Test fun selectedPhotoIsCopiedAndDoesNotDependOnOriginalFile(): Unit = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val source = File.createTempFile("source", ".jpg", context.cacheDir)
        val bitmap = Bitmap.createBitmap(20, 10, Bitmap.Config.ARGB_8888)
        source.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 90, it) }
        val saved = File(RecipePhotoSearch.savePhonePhoto(context, Uri.fromFile(source)))
        try {
            source.delete()
            assertTrue(saved.exists())
            assertEquals(20, BitmapFactory.decodeFile(saved.absolutePath).width)
        } finally { source.delete(); saved.delete(); bitmap.recycle() }
    }
    @Test fun invalidFileIsRejected(): Unit = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val source = File.createTempFile("invalid", ".txt", context.cacheDir).apply { writeText("not a photograph") }
        try { assertTrue(runCatching { RecipePhotoSearch.savePhonePhoto(context, Uri.fromFile(source)) }.isFailure) }
        finally { source.delete() }
    }
}
