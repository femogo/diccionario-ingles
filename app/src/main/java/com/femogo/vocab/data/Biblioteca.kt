package com.femogo.vocab.data

import android.content.Context
import com.femogo.vocab.engine.Card
import com.femogo.vocab.engine.Word
import com.femogo.vocab.engine.WordParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.IOException
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL

/** Un módulo tal como lo ve la aplicación. */
data class Modulo(
    val id: String,
    val nombre: String,
    val descripcion: String,
    val version: Int,
    val palabras: Int
)

/**
 * Los módulos de juego y su contenido.
 *
 * Un módulo es un juego con su propio vocabulario y su propio progreso. Son
 * datos, no código: la aplicación lee un índice publicado, y lo que aparezca
 * allí lo instala. Añadir un idioma nuevo es publicar un archivo y nombrarlo en
 * el índice; nadie tiene que reinstalar nada.
 *
 * El progreso se guarda por módulo y por posición en la lista de frecuencia, así
 * que actualizar el contenido de un módulo no cuesta lo aprendido.
 */
class Biblioteca(private val context: Context) {

    private val db = AppDatabase.get(context)
    private val catalogos = HashMap<String, List<Word>>()

    /** La primera vez, instala lo que viene dentro de la aplicación. */
    suspend fun prepararSiHaceFalta() = withContext(Dispatchers.IO) {
        if (db.moduloDao().todos().isNotEmpty()) return@withContext
        val indice = context.assets.open(INDICE).use { leerIndice(it.bufferedReader().readText()) }
        indice.forEach { anuncio ->
            val texto = context.assets.open(anuncio.archivo).use { it.bufferedReader().readText() }
            instalar(anuncio, texto)
        }
    }

    suspend fun modulos(): List<Modulo> = withContext(Dispatchers.IO) {
        db.moduloDao().todos().map {
            Modulo(it.id, it.nombre, it.descripcion, it.version, it.palabras)
        }
    }

    suspend fun catalogo(modulo: String): List<Word> = catalogos[modulo]
        ?: withContext(Dispatchers.IO) {
            db.wordDao().delModulo(modulo).mapNotNull { it.toDomain() }
                .also { catalogos[modulo] = it }
        }

    suspend fun cards(modulo: String): Map<Int, Card> = withContext(Dispatchers.IO) {
        db.cardDao().delModulo(modulo).associate { it.rank to it.toDomain() }
    }

    suspend fun guardar(modulo: String, card: Card) = withContext(Dispatchers.IO) {
        db.cardDao().upsert(CardEntity.from(card, modulo))
    }

    suspend fun borrarProgreso(modulo: String) = withContext(Dispatchers.IO) {
        db.cardDao().limpiar(modulo)
    }

    suspend fun borrarTodoElProgreso() = withContext(Dispatchers.IO) {
        db.cardDao().limpiarTodo()
    }

    // --- actualización ------------------------------------------------------

    data class Novedad(val nombre: String, val palabras: Int, val esNuevo: Boolean)

    sealed interface Resultado {
        data object AlDia : Resultado
        data class Instalados(val novedades: List<Novedad>) : Resultado
        data class Fallo(val motivo: String) : Resultado
    }

    /**
     * Descarga el índice publicado e instala lo que haya cambiado, incluidos
     * módulos que no existían cuando se instaló la aplicación.
     */
    suspend fun actualizar(): Resultado = withContext(Dispatchers.IO) {
        val indice = try {
            leerIndice(descargar(URL_BASE + INDICE))
        } catch (e: IOException) {
            return@withContext Resultado.Fallo(e.message ?: "No se ha podido conectar")
        } catch (e: Exception) {
            return@withContext Resultado.Fallo("El índice de módulos no se entiende")
        }

        val instalados = db.moduloDao().todos().associateBy { it.id }
        val novedades = ArrayList<Novedad>()

        for (anuncio in indice) {
            val actual = instalados[anuncio.id]
            if (actual != null && actual.version >= anuncio.version) continue
            val texto = try {
                descargar(URL_BASE + anuncio.archivo)
            } catch (e: IOException) {
                return@withContext Resultado.Fallo(
                    "No se ha podido descargar ${anuncio.nombre}: ${e.message}"
                )
            }
            val cuantas = instalar(anuncio, texto)
            if (cuantas > 0) novedades.add(Novedad(anuncio.nombre, cuantas, actual == null))
        }

        if (novedades.isEmpty()) Resultado.AlDia else Resultado.Instalados(novedades)
    }

    /** Sustituye el contenido de un módulo. El progreso no se toca. */
    private suspend fun instalar(anuncio: Anuncio, texto: String): Int {
        val resultado = WordParser.parse(texto.lineSequence())
        if (resultado.words.isEmpty()) return 0

        db.wordDao().limpiar(anuncio.id)
        resultado.words.chunked(500).forEach { lote ->
            db.wordDao().insertar(lote.map { WordEntity.from(it, anuncio.id) })
        }
        db.moduloDao().guardar(
            ModuloEntity(
                id = anuncio.id,
                nombre = anuncio.nombre,
                descripcion = anuncio.descripcion,
                version = anuncio.version,
                palabras = resultado.words.size
            )
        )
        catalogos.remove(anuncio.id)
        return resultado.words.size
    }

    private data class Anuncio(
        val id: String,
        val nombre: String,
        val descripcion: String,
        val archivo: String,
        val version: Int
    )

    private fun leerIndice(json: String): List<Anuncio> {
        val raiz = JSONObject(json)
        val lista = raiz.getJSONArray("modulos")
        return (0 until lista.length()).map { i ->
            val m = lista.getJSONObject(i)
            Anuncio(
                id = m.getString("id"),
                nombre = m.getString("nombre"),
                descripcion = m.optString("descripcion"),
                archivo = m.getString("archivo"),
                version = m.optInt("version", 1)
            )
        }
    }

    private fun descargar(url: String): String {
        val conexion = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 15_000
            readTimeout = 30_000
            instanceFollowRedirects = true
        }
        try {
            when (val codigo = conexion.responseCode) {
                in 200..299 -> return conexion.inputStream.bufferedReader().readText()
                404 -> throw IOException(
                    "GitHub responde 404. Si el repositorio es privado la descarga " +
                        "directa no funciona: hazlo público."
                )
                else -> throw IOException("GitHub responde $codigo")
            }
        } finally {
            conexion.disconnect()
        }
    }

    /** Importación manual desde un archivo, para probar módulos sin publicarlos. */
    suspend fun importar(modulo: String, stream: InputStream): Int = withContext(Dispatchers.IO) {
        val texto = stream.bufferedReader().readText()
        val actual = db.moduloDao().porId(modulo) ?: return@withContext 0
        instalar(
            Anuncio(actual.id, actual.nombre, actual.descripcion, "", actual.version + 1),
            texto
        )
    }

    private companion object {
        const val INDICE = "modulos.json"
        const val URL_BASE = "https://raw.githubusercontent.com/femogo/diccionario-ingles/main/"
    }
}
