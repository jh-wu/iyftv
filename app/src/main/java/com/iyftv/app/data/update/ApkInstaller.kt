package com.iyftv.app.data.update

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import java.io.File

object ApkInstaller {

    enum class Result { Started, NeedsPermission, Failed }

    fun updateFile(context: Context, release: Release) = File(File(context.cacheDir, "updates"), "iyftv-build-${release.build}.apk")

    /** Deletes downloaded APKs other than [keep], so old updates don't pile up in the cache. */
    fun cleanUp(context: Context, keep: File? = null) {
        File(context.cacheDir, "updates").listFiles()?.filter { it != keep }?.forEach { it.delete() }
    }

    /** True when [apk] is a complete APK of this app, not a truncated file or an error page. */
    fun isValid(context: Context, apk: File): Boolean {
        @Suppress("DEPRECATION")
        val info = context.packageManager.getPackageArchiveInfo(apk.path, 0) ?: return false
        return info.packageName == context.packageName
    }

    /**
     * Hands [apk] to the system installer. On Android 8+ the app first needs the
     * "install unknown apps" permission; when it is missing this opens that setting
     * and returns [Result.NeedsPermission], and the caller offers to try again.
     */
    fun install(context: Context, apk: File): Result {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && !context.packageManager.canRequestPackageInstalls()) {
            val settings = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}"))
            try {
                context.startActivity(settings)
                return Result.NeedsPermission
            } catch (_: ActivityNotFoundException) {
                // Some TV builds lack this screen; the installer then asks for the permission itself.
            }
        }
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.updates", apk)
        val intent = Intent(Intent.ACTION_VIEW)
            .setDataAndType(uri, "application/vnd.android.package-archive")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        return try {
            context.startActivity(intent)
            Result.Started
        } catch (_: ActivityNotFoundException) {
            Result.Failed
        }
    }
}
