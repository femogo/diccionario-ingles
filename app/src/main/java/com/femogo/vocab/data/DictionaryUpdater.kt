package com.femogo.vocab.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/**
 * Trae el diccionario desde el repositorio y lo instala si ha cambiado.
 *
 * El progreso no se toca: vive en otra tabla y se vuelve a enlazar por posición
 * en la lista de frecuencia, así que ampliar el vocabulario no cuesta nada de
 * lo aprendido.
 *
 * La descarga es un GET a la copia en crudo del archivo. Eso exige que el
 * repositorio sea público: GitHub sirve `raw` de repositorios privados solo con
 * credenciales, y meter un token dentro del APK equivale a publicarlo, porque
 * cualquiera puede extraerlo del archivo instalado.
 */
class DictionaryUpdater(
    private val context: Context,
    private val repo: VocabRepository,
    private val url: String = URL_POR_DEFECTO
) {

    sealed interface Resultado {
        data object AlDia : Resultado
        data class Instalado(val palabras: Int, val descartadas: Int) : Resultado
        data class Fallo(val motivo: String) : Resultado
    }

    suspend fun actualizar(huellaActual: String?): Pair<Resultado, String?> =
        withContext(Dispatchers.IO) {
            val texto = try {
                descargar()
            } catch (e: IOException) {
                return@withContext Resultado.Fallo(mensajeDe(e)) to null
            }

            val huella = huellaDe(texto)
            if (huella == huellaActual) return@withContext Resultado.AlDia to huella

            val informe = texto.byteInputStream().use { repo.importInto(it) }
            if (informe.imported == 0) {
                return@withContext Resultado.Fallo("El archivo descargado no tiene palabras válidas") to null
            }
            Resultado.Instalado(informe.imported, informe.skipped) to huella
        }

    private fun descargar(): String {
        val conexion = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 15_000
            readTimeout = 30_000
            requestMethod = "GET"
            setRequestProperty("Accept", "text/plain")
        }
        try {
            when (val codigo = conexion.responseCode) {
                in 200..299 -> return conexion.inputStream.bufferedReader().readText()
                404 -> throw IOException(
                    "GitHub responde 404. Si el repositorio es privado, la descarga " +
                        "directa no funciona: hazlo público para poder actualizar así."
                )
                else -> throw IOException("GitHub responde $codigo")
            }
        } finally {
            conexion.disconnect()
        }
    }

    private fun mensajeDe(e: IOException): String =
        e.message ?: "No se ha podido conectar. Comprueba la conexión."

    /** Huella del contenido, para no reinstalar lo que ya está puesto. */
    private fun huellaDe(texto: String): String {
        val digest = java.security.MessageDigest.getInstance("SHA-256")
        return digest.digest(texto.toByteArray()).joinToString("") { "%02x".format(it) }
    }

    companion object {
        const val URL_POR_DEFECTO =
            "https://raw.githubusercontent.com/femogo/diccionario-ingles/main/app/src/main/assets/words.txt"
    }
}
