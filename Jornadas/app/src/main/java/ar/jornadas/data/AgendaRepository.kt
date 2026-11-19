package ar.jornadas.data

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import retrofit2.HttpException
import java.io.IOException

/** Lo que hay guardado localmente: las charlas y si alguna vez se descargó una edición válida. */
data class Cartelera(val charlas: List<Charla>, val hayEdicion: Boolean)

/** Contrato que usa el ViewModel: permite reemplazar Room + Retrofit por un fake en pruebas. */
interface Agenda {
    val cartelera: Flow<Cartelera>
    suspend fun sincronizar(): ResultadoSync
    suspend fun cambiarFavorita(id: String)
}

class AgendaRepository(private val api: ProgramaApi, private val dao: CarteleraDao) : Agenda {
    /** Room es la única fuente de verdad: la UI nunca muestra directamente la respuesta de Retrofit. */
    override val cartelera: Flow<Cartelera> = combine(dao.observar(), dao.hayEdicion(), ::Cartelera)

    override suspend fun sincronizar(): ResultadoSync {
        // Los tres tipos de fallo de Retrofit: antes solo se atrapaba IOException y un HTTP 500 o un
        // JSON inválido terminaban en una excepción no capturada (crash de la app).
        val remotas = try {
            api.consultar()
        } catch (e: CancellationException) {
            throw e // no romper la cancelación estructurada
        } catch (e: IOException) {
            return ResultadoSync.SinConexion
        } catch (e: HttpException) {
            return ResultadoSync.ErrorServidor(e.code())
        } catch (e: RuntimeException) { // JsonSyntaxException, cuerpo nulo, etc.
            return ResultadoSync.DatosInvalidos
        }
        val fichas = remotas.map { Instantanea.aCharla(it) ?: return ResultadoSync.DatosInvalidos }
        return try {
            ResultadoSync.Ok(dao.aplicarInstantanea(fichas))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            ResultadoSync.ErrorGuardado // la transacción revirtió: sigue la edición anterior completa
        }
    }

    override suspend fun cambiarFavorita(id: String) = dao.alternar(id)
}
