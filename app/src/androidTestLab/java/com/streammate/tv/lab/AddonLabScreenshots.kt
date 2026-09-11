package com.streammate.tv.lab

import com.streammate.tv.addons.*

import android.content.Context
import android.graphics.Bitmap
import android.app.Activity
import android.content.ContentValues
import android.os.Bundle
import android.provider.MediaStore
import android.view.WindowManager
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File

/** Synthetic test fixtures only. Fixed names overwrite only this Lab-owned evidence directory. */
internal object AddonLabScreenshots {
    fun capture(context: Context, name: String) {
        check(context.packageName == "com.streammate.tv.lab")
        check(android.os.Build.FINGERPRINT.contains("generic") || android.os.Build.HARDWARE.contains("ranchu"))
        require(name.matches(Regex("[a-z0-9-]+\\.png")))
        val directory = checkNotNull(context.getExternalFilesDir("synthetic-test-evidence")).canonicalFile
        val target = File(directory, name).canonicalFile
        check(target.parentFile == directory)
        // Never bypass protected account/QR windows, including for synthetic fixtures.
        check(context is Activity && context.window.attributes.flags and WindowManager.LayoutParams.FLAG_SECURE == 0)
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val bitmap = checkNotNull(instrumentation.uiAutomation.takeScreenshot())
        try {
            target.outputStream().use { check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) }
            // Explicit visual-review export only. Unique names avoid the MediaStore
            // collision limit; ordinary regression runs keep private fixed-name files.
            if (InstrumentationRegistry.getArguments().getString("exportSyntheticScreenshots") == "true") {
                val exportName = "${name.removeSuffix(".png")}-${java.util.UUID.randomUUID()}.png"
                val values = ContentValues().apply {
                    put(MediaStore.Images.Media.DISPLAY_NAME, exportName)
                    put(MediaStore.Images.Media.MIME_TYPE, "image/png")
                    put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/SohvaLabSynthetic")
                    put(MediaStore.Images.Media.IS_PENDING, 1)
                }
                val resolver = context.contentResolver
                val uri = checkNotNull(resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values))
                try {
                    checkNotNull(resolver.openOutputStream(uri)).use { check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) }
                    resolver.update(uri, ContentValues().apply { put(MediaStore.Images.Media.IS_PENDING, 0) }, null, null)
                    instrumentation.sendStatus(0, Bundle().apply { putString("syntheticScreenshot", exportName) })
                } catch (error: Exception) {
                    resolver.delete(uri, null, null) // Only the incomplete image just created here.
                    throw error
                }
            }
        }
        finally { bitmap.recycle() }
    }
}
