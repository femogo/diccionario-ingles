package com.femogo.vocab.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [ModuloEntity::class, WordEntity::class, CardEntity::class],
    version = 3,
    exportSchema = true
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun moduloDao(): ModuloDao
    abstract fun wordDao(): WordDao
    abstract fun cardDao(): CardDao

    companion object {
        const val MODULO_INICIAL = "vocabulario"

        /**
         * El progreso dejó de medirse en fechas para medirse en preguntas
         * respondidas. Las cajas y los aciertos se conservan; lo único que se
         * pierde es el calendario, que ya no significa nada.
         */
        private val DE_FECHAS_A_TURNOS = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE cards_nueva (
                        rank INTEGER NOT NULL PRIMARY KEY,
                        box INTEGER NOT NULL,
                        dueTurn INTEGER NOT NULL,
                        seen INTEGER NOT NULL,
                        correct INTEGER NOT NULL,
                        streak INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    INSERT INTO cards_nueva (rank, box, dueTurn, seen, correct, streak)
                    SELECT rank, box, 0, seen, correct, streak FROM cards
                    """.trimIndent()
                )
                db.execSQL("DROP TABLE cards")
                db.execSQL("ALTER TABLE cards_nueva RENAME TO cards")
            }
        }

        /**
         * El juego pasa a tener varios módulos. Todo lo que había era el módulo
         * de vocabulario, así que se le asigna ese nombre y el progreso queda
         * intacto: las claves llevan ahora el módulo por delante, pero apuntan a
         * las mismas palabras.
         */
        private val A_MODULOS = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE modulos (
                        id TEXT NOT NULL PRIMARY KEY,
                        nombre TEXT NOT NULL,
                        descripcion TEXT NOT NULL,
                        version INTEGER NOT NULL,
                        palabras INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    CREATE TABLE words_nueva (
                        modulo TEXT NOT NULL,
                        rank INTEGER NOT NULL,
                        en TEXT NOT NULL,
                        lemma TEXT NOT NULL,
                        pos TEXT NOT NULL,
                        es TEXT NOT NULL,
                        esAlt TEXT NOT NULL,
                        cefr TEXT NOT NULL,
                        hint TEXT,
                        PRIMARY KEY (modulo, rank)
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    INSERT INTO words_nueva (modulo, rank, en, lemma, pos, es, esAlt, cefr, hint)
                    SELECT '$MODULO_INICIAL', rank, en, lemma, pos, es, esAlt, cefr, hint FROM words
                    """.trimIndent()
                )
                db.execSQL("DROP TABLE words")
                db.execSQL("ALTER TABLE words_nueva RENAME TO words")

                db.execSQL(
                    """
                    CREATE TABLE cards_nueva (
                        modulo TEXT NOT NULL,
                        rank INTEGER NOT NULL,
                        box INTEGER NOT NULL,
                        dueTurn INTEGER NOT NULL,
                        seen INTEGER NOT NULL,
                        correct INTEGER NOT NULL,
                        streak INTEGER NOT NULL,
                        PRIMARY KEY (modulo, rank)
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    INSERT INTO cards_nueva (modulo, rank, box, dueTurn, seen, correct, streak)
                    SELECT '$MODULO_INICIAL', rank, box, dueTurn, seen, correct, streak FROM cards
                    """.trimIndent()
                )
                db.execSQL("DROP TABLE cards")
                db.execSQL("ALTER TABLE cards_nueva RENAME TO cards")

                db.execSQL(
                    """
                    INSERT INTO modulos (id, nombre, descripcion, version, palabras)
                    SELECT '$MODULO_INICIAL', 'Vocabulario',
                           'Las palabras más frecuentes del inglés, por orden de uso',
                           0, COUNT(*)
                    FROM words WHERE modulo = '$MODULO_INICIAL'
                    """.trimIndent()
                )
            }
        }

        @Volatile private var instance: AppDatabase? = null

        fun get(context: Context): AppDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(
                context.applicationContext,
                AppDatabase::class.java,
                "vocab.db"
            ).addMigrations(DE_FECHAS_A_TURNOS, A_MODULOS)
                .build().also { instance = it }
        }
    }
}
