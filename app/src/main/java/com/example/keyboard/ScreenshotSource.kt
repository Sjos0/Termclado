package com.example.keyboard

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.util.Size
import androidx.core.content.ContextCompat

/**
 * Captura de tela mais recente, para a pílula da barra do teclado.
 *
 * Privacidade: só lê nome e data da imagem, e só com a permissão de fotos já concedida pelo usuário.
 * Nada é enviado para fora do aparelho. A imagem só vai para o app que está digitando, e só quando
 * o usuário toca na pílula.
 */
object ScreenshotSource {

    data class Shot(val uri: Uri, val name: String, val addedAtSec: Long)

    /** Permissão necessária para ler imagens: depende da versão do Android. */
    fun permissionName(): String =
        if (Build.VERSION.SDK_INT >= 33) Manifest.permission.READ_MEDIA_IMAGES
        else Manifest.permission.READ_EXTERNAL_STORAGE

    fun hasPermission(context: Context): Boolean =
        ContextCompat.checkSelfPermission(context, permissionName()) == PackageManager.PERMISSION_GRANTED

    /** Captura mais recente feita nos últimos [maxAgeSec] segundos, ou null. */
    fun latestRecent(context: Context, maxAgeSec: Long = 120L): Shot? {
        if (!hasPermission(context)) return null
        val now = System.currentTimeMillis() / 1000L
        val projection = arrayOf(
            MediaStore.Images.Media._ID,
            MediaStore.Images.Media.DISPLAY_NAME,
            MediaStore.Images.Media.DATE_ADDED
        )
        val selection = "${MediaStore.Images.Media.DATE_ADDED} >= ? AND " +
            "(${MediaStore.Images.Media.DISPLAY_NAME} LIKE ? OR ${MediaStore.Images.Media.DISPLAY_NAME} LIKE ?)"
        val args = arrayOf((now - maxAgeSec).toString(), "%creenshot%", "%aptura%")
        return try {
            context.contentResolver.query(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                projection,
                selection,
                args,
                "${MediaStore.Images.Media.DATE_ADDED} DESC"
            )?.use { c ->
                if (!c.moveToFirst()) return null
                val id = c.getLong(c.getColumnIndexOrThrow(MediaStore.Images.Media._ID))
                val name = c.getString(c.getColumnIndexOrThrow(MediaStore.Images.Media.DISPLAY_NAME)) ?: ""
                val added = c.getLong(c.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_ADDED))
                Shot(
                    uri = Uri.withAppendedPath(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id.toString()),
                    name = name,
                    addedAtSec = added
                )
            }
        } catch (_: SecurityException) {
            null
        } catch (_: Exception) {
            null
        }
    }

    /** Miniatura da captura (Android 10 ou superior). Em versões antigas, a pílula mostra só o texto. */
    fun thumbnail(context: Context, shot: Shot): Bitmap? {
        if (Build.VERSION.SDK_INT < 29) return null
        return try {
            context.contentResolver.loadThumbnail(shot.uri, Size(96, 96), null)
        } catch (_: Exception) {
            null
        }
    }
}
