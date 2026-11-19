package ar.jornadas.ui

import androidx.lifecycle.*
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import ar.jornadas.SesionDeCarga
import ar.jornadas.Servicios
import ar.jornadas.data.Agenda
import ar.jornadas.data.Cartelera
import ar.jornadas.data.ResultadoSync
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

/** Todo lo que la pantalla necesita, en un solo objeto inmutable (una sola fuente de verdad para la UI). */
data class ProgramaUiState(
    val cartelera: Cartelera? = null,      // null mientras Room todavía no respondió
    val dia: Int = 0,
    val tema: String = "Todos",
    val cargando: Boolean = false,
    val ultimoResultado: ResultadoSync? = null,
)

/**
 * Las dependencias llegan por constructor (Factory abajo): el ViewModel no crea Room ni Retrofit,
 * no conoce la Activity ni el banco de pruebas, y se puede probar con un fake de [Agenda]
 * (ver ProgramaModelTest).
 */
class ProgramaModel(
    private val agenda: Agenda,
    private val sesion: SesionDeCarga,
    private val estado: SavedStateHandle,
) : ViewModel() {

    // Estado mutable privado; afuera sólo se expone lectura (antes dia/aviso/cargando eran públicos
    // y la UI los modificaba directamente).
    private data class Peticion(val cargando: Boolean = false, val resultado: ResultadoSync? = null)
    private val peticionEstado = MutableStateFlow(Peticion())

    // Pedido 43: los filtros viven en SavedStateHandle: sobreviven a la rotación, a salir del
    // catálogo para ver una ficha y a la muerte del proceso; una tarea nueva arranca sin filtros.
    private val dia = estado.getStateFlow(CLAVE_DIA, 0)
    private val tema = estado.getStateFlow(CLAVE_TEMA, "Todos")

    val uiState: StateFlow<ProgramaUiState> =
        combine(agenda.cartelera, dia, tema, peticionEstado) { cartelera, d, t, p ->
            ProgramaUiState(cartelera, d, t, p.cargando, p.resultado)
        }.stateIn(viewModelScope, SharingStarted.Eagerly, ProgramaUiState(dia = dia.value, tema = tema.value))

    private var peticion: Job? = null

    fun elegirDia(valor: Int) { estado[CLAVE_DIA] = valor }
    fun elegirTema(valor: String) { estado[CLAVE_TEMA] = valor }

    /** Pedido 44: se llama desde onCreate; sólo dispara la primera vez en el proceso. */
    fun cargaAutomatica() {
        if (sesion.reclamarCargaAutomatica()) actualizar()
    }

    /** Actualización explícita. Con una petición en curso, los toques extra no agregan intentos. */
    fun actualizar() {
        if (peticion?.isActive == true) return
        peticionEstado.update { it.copy(cargando = true) }
        peticion = viewModelScope.launch {
            val resultado = try { agenda.sincronizar() } finally {
                peticionEstado.update { it.copy(cargando = false) }
            }
            peticionEstado.update { it.copy(resultado = resultado) }
        }
    }

    fun marcar(id: String) { viewModelScope.launch { agenda.cambiarFavorita(id) } }

    companion object {
        private const val CLAVE_DIA = "filtro_dia"
        private const val CLAVE_TEMA = "filtro_tema"

        /** Arma el ViewModel con las instancias únicas del proceso (applicationContext, sin leaks). */
        val Factory = viewModelFactory {
            initializer {
                val servicios = Servicios.de(this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY]!!)
                ProgramaModel(servicios.agenda, servicios.sesion, createSavedStateHandle())
            }
        }
    }
}
