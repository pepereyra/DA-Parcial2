package ar.jornadas

import android.content.Context
import androidx.room.Room
import ar.jornadas.data.*
import ar.jornadas.lab.ServicioLocal
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.atomic.AtomicBoolean

/** Marca de "ya hubo carga automática" que vive lo mismo que el proceso. */
class SesionDeCarga {
    private val hecha = AtomicBoolean(false)
    /** true sólo la primera vez que se llama en el proceso. */
    fun reclamarCargaAutomatica(): Boolean = hecha.compareAndSet(false, true)
}

/**
 * Una sola instancia por proceso. Antes cada ViewModel creaba su propia base Room, su cliente
 * Retrofit y su ServicioLocal (con su propio contador): si la Activity se cerraba y se volvía a
 * abrir en el mismo proceso, se duplicaban conexiones a la base y el contador volvía a cero.
 */
class Servicios private constructor(context: Context) {
    val mesa = ServicioLocal(context)
    private val archivo = Room.databaseBuilder(context, Archivo::class.java, "cartelera.db")
        .addMigrations(MIGRACION_1_2)
        .build()
    private val api = Retrofit.Builder().baseUrl("https://programa.invalid/")
        .client(OkHttpClient.Builder().addInterceptor(mesa).build())
        .addConverterFactory(GsonConverterFactory.create()).build().create(ProgramaApi::class.java)
    val agenda = AgendaRepository(api, archivo.cartelera())

    /** Pedido 44: la carga automática ocurre una vez por proceso, no por Activity ni por pantalla. */
    val sesion = SesionDeCarga()

    companion object {
        @Volatile private var instancia: Servicios? = null
        fun de(context: Context): Servicios = instancia ?: synchronized(this) {
            instancia ?: Servicios(context.applicationContext).also { instancia = it }
        }
    }
}
