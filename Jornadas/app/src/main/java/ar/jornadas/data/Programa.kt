package ar.jornadas.data

import androidx.room.*
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import retrofit2.http.GET
import kotlinx.coroutines.flow.Flow

/** Campos nulables a propósito: Gson no respeta la nulabilidad de Kotlin (ver Instantanea.aCharla). */
data class FichaRemota(val id: String?, val title: String?, val track: String?, val day: Int?,
    val time: String?, val room: String?, val speaker: String?, val summary: String?)

interface ProgramaApi {
    @GET("programa") suspend fun consultar(): List<FichaRemota>
}

@Entity(tableName = "charlas")
data class Charla(@PrimaryKey val id: String, val titulo: String, val tema: String, val dia: Int,
    val hora: String, val sala: String, val ponente: String, val resumen: String,
    val favorita: Boolean = false)

/**
 * Registro de la última edición descargada con éxito (pedido 41).
 * Hace falta una tabla aparte porque una edición válida puede tener cero charlas (v4):
 * "no hay filas en charlas" no alcanza para distinguir "nunca descargué" de "descargué un programa vacío".
 */
@Entity(tableName = "edicion")
data class Edicion(@PrimaryKey val id: Int = 1, val descargadaEn: Long)

@Dao
interface CarteleraDao {
    @Query("SELECT * FROM charlas ORDER BY dia, hora, sala")
    fun observar(): Flow<List<Charla>>

    @Query("SELECT EXISTS(SELECT 1 FROM edicion)")
    fun hayEdicion(): Flow<Boolean>

    @Query("SELECT * FROM charlas WHERE favorita = 1")
    suspend fun favoritas(): List<Charla>

    @Query("DELETE FROM charlas")
    suspend fun borrarTodas()

    @Query("DELETE FROM charlas WHERE id NOT IN (:vigentes)")
    suspend fun borrarRetiradas(vigentes: List<String>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun guardar(fichas: List<Charla>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun registrarEdicion(edicion: Edicion)

    /**
     * Aplica una instantánea completa del programa (pedido 42) en una sola transacción:
     * - las marcas se leen DENTRO de la transacción, así una marca agregada o quitada mientras
     *   viajaba la petición se respeta (no se pisa con datos tomados antes de la respuesta);
     * - las charlas que no vienen en la instantánea se borran (y con ellas su marca), por eso si
     *   una charla retirada regresa, regresa sin marcar;
     * - las vigentes se actualizan por id estable (título/sala nuevos) conservando su marca;
     * - si algo falla, se revierte todo y queda la edición anterior completa.
     * Devuelve los títulos de las charlas de Mi agenda que fueron retiradas, para avisarle al usuario.
     */
    @Transaction
    suspend fun aplicarInstantanea(fichas: List<Charla>): List<String> {
        val favoritas = favoritas()
        if (fichas.isEmpty()) borrarTodas() else borrarRetiradas(fichas.map { it.id })
        guardar(Instantanea.fusionar(fichas, favoritas.mapTo(HashSet()) { it.id }))
        registrarEdicion(Edicion(descargadaEn = System.currentTimeMillis()))
        return Instantanea.favoritasRetiradas(favoritas, fichas).map { it.titulo }
    }

    @Query("UPDATE charlas SET favorita = NOT favorita WHERE id = :id")
    suspend fun alternar(id: String)
}

/**
 * La versión instalada ya tiene usuarios con charlas guardadas: la tabla nueva se agrega con una
 * migración explícita (nada de fallbackToDestructiveMigration) para conservar charlas y marcas.
 * Si el usuario ya tenía charlas descargadas, eso cuenta como una edición previa.
 */
val MIGRACION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("CREATE TABLE IF NOT EXISTS `edicion` (`id` INTEGER NOT NULL, `descargadaEn` INTEGER NOT NULL, PRIMARY KEY(`id`))")
        db.execSQL("INSERT INTO edicion (id, descargadaEn) SELECT 1, 0 WHERE EXISTS (SELECT 1 FROM charlas)")
    }
}

@Database(entities = [Charla::class, Edicion::class], version = 2, exportSchema = false)
abstract class Archivo : RoomDatabase() {
    abstract fun cartelera(): CarteleraDao
}
