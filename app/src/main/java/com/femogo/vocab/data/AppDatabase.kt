package com.femogo.vocab.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [WordEntity::class, CardEntity::class],
    version = 2,
    exportSchema = true
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun wordDao(): WordDao
    abstract fun cardDao(): CardDao

    companion object {
        /**
         * El progreso dejó de medirse en fechas para medirse en preguntas
         * respondidas. Las cajas y los aciertos se conservan; lo único que se
         * pierde es el calendario, que ya no significa nada. Todas las palabras
         * quedan disponibles de inmediato y el espaciado se rehace jugando.
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

        @Volatile private var instance: AppDatabase? = null

        fun get(context: Context): AppDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(
                context.applicationContext,
                AppDatabase::class.java,
                "vocab.db"
            ).addMigrations(DE_FECHAS_A_TURNOS).build().also { instance = it }
        }
    }
}
