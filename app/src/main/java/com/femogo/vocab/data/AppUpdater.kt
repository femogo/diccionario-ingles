package com.femogo.vocab.data

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/**
 * Busca una versión nueva de la propia aplicación, la descarga y abre el
 * instalador.
 *
 * Andar el último paso solo no es posible: Android reserva la instalación
 * silenciosa a las aplicaciones del sistema firmadas con la clave de
 * plataforma, al administrador de dispositivo de una instalación gestionada, o
 * a un móvil con root. No es un permiso que se pueda pedir, y existe justo para
 * que una aplicación no instale cosas a espaldas de quien la usa. Lo que sí se
 * puede es dejarlo todo hecho y que el usuario confirme con un toque.
 */
class AppUpdater(private val context: Context) {

    sealed interface Resultado {
        data object AlDia : Resultado
        data class Descargando(val version: String) : Resultado
        data class ListaParaInstalar(val version: String) : Resultado
        data class PermisoNecesario(val version: String) : Resultado
        data class Fallo(val motivo: String) : Resultado
    }

    /** Versión instalada ahora mismo. */
    private val versionActual: Long
        get() = runCatching {
            val info = context.packageManager.getPackageInfo(context.packageName, 0)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) info.longVersionCode
            else @Suppress("DEPRECATION") info.versionCode.toLong()
        }.getOrDefault(0L)

    /** Consulta la última publicación y dice si hay algo más nuevo. */
    suspend fun comprobar(): Pair<Long, String>? = withContext(Dispatchers.IO) {
        val cuerpo = leer(URL_ULTIMA) ?: return@withContext null
        val json = runCatching { JSONObject(cuerpo) }.getOrNull() ?: return@withContext null

        // La etiqueta es "v" seguido del número de compilación, que es el mismo
        // que el versionCode del APK publicado.
        val etiqueta = json.optString("tag_name").removePrefix("v")
        val version = etiqueta.toLongOrNull() ?: return@withContext null

        val descarga = json.optJSONArray("assets")?.let { assets ->
            (0 until assets.length())
                .map { assets.getJSONObject(it) }
                .firstOrNull { it.optString("name").endsWith(".apk") }
                ?.optString("browser_download_url")
        } ?: return@withContext null

        if (version <= versionActual) null else version to descarga
    }

    /**
     * Descarga el APK a la zona privada de la aplicación. Devuelve el archivo
     * listo para pasárselo al instalador del sistema.
     */
    suspend fun descargar(url: String): Result<File> = withContext(Dispatchers.IO) {
        runCatching {
            val destino = File(context.cacheDir, "actualizacion").apply { mkdirs() }
            val apk = File(destino, "vocabulario.apk")
            if (apk.exists()) apk.delete()

            val conexion = (URL(url).openConnection() as HttpURLConnection).apply {
                connectTimeout = 15_000
                readTimeout = 60_000
                instanceFollowRedirects = true
            }
            try {
                if (conexion.responseCode !in 200..299) {
                    throw IOException("La descarga responde ${conexion.responseCode}")
                }
                conexion.inputStream.use { entrada ->
                    apk.outputStream().use { salida -> entrada.copyTo(salida) }
                }
            } finally {
                conexion.disconnect()
            }
            if (apk.length() < 1_000_000) throw IOException("El archivo descargado está incompleto")
            apk
        }
    }

    /** Si no está concedido, el instalador ni siquiera llega a abrirse. */
    fun puedeInstalar(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.O ||
            context.packageManager.canRequestPackageInstalls()

    /** Lleva a la pantalla del sistema donde se concede el permiso. */
    fun abrirAjustesDePermiso() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        context.startActivity(
            Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES)
                .setData(Uri.parse("package:${context.packageName}"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }

    /** Abre el instalador del sistema con el archivo ya descargado. */
    fun instalar(apk: File) {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.archivos", apk)
        context.startActivity(
            Intent(Intent.ACTION_VIEW)
                .setDataAndType(uri, "application/vnd.android.package-archive")
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }

    private fun leer(url: String): String? = runCatching {
        val conexion = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 15_000
            readTimeout = 20_000
            setRequestProperty("Accept", "application/vnd.github+json")
        }
        try {
            if (conexion.responseCode !in 200..299) null
            else conexion.inputStream.bufferedReader().readText()
        } finally {
            conexion.disconnect()
        }
    }.getOrNull()

    private companion object {
        const val URL_ULTIMA =
            "https://api.github.com/repos/femogo/diccionario-ingles/releases/latest"
    }
}
