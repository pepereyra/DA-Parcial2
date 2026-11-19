package ar.jornadas.ui

import androidx.lifecycle.SavedStateHandle
import ar.jornadas.SesionDeCarga
import ar.jornadas.data.Agenda
import ar.jornadas.data.Cartelera
import ar.jornadas.data.ResultadoSync
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/** Evidencia del pedido 44 (control de descargas) y del 43 (filtros guardados), con un fake de Agenda. */
@OptIn(ExperimentalCoroutinesApi::class)
class ProgramaModelTest {
    private class AgendaFalsa : Agenda {
        var llamadas = 0
        var respuesta = CompletableDeferred<ResultadoSync>()
        override val cartelera = MutableStateFlow(Cartelera(emptyList(), hayEdicion = true))
        override suspend fun sincronizar(): ResultadoSync { llamadas++; return respuesta.await() }
        override suspend fun cambiarFavorita(id: String) {}
    }

    @Before fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())
    @After fun tearDown() = Dispatchers.resetMain()

    @Test fun unaSolaCargaAutomaticaPorProceso() {
        val agenda = AgendaFalsa(); val sesion = SesionDeCarga()
        ProgramaModel(agenda, sesion, SavedStateHandle()).cargaAutomatica()
        // Rotación o Activity recreada en el mismo proceso: otro ViewModel, misma sesión.
        ProgramaModel(agenda, sesion, SavedStateHandle()).cargaAutomatica()
        assertEquals(1, agenda.llamadas)
    }

    @Test fun dosToquesDuranteUnaPeticionAgreganUnIntento() {
        val agenda = AgendaFalsa()
        val model = ProgramaModel(agenda, SesionDeCarga(), SavedStateHandle())
        model.actualizar(); model.actualizar()
        assertEquals(1, agenda.llamadas)
        assertTrue(model.uiState.value.cargando)

        agenda.respuesta.complete(ResultadoSync.SinConexion) // termina con falla
        assertFalse(model.uiState.value.cargando)
        assertEquals(ResultadoSync.SinConexion, model.uiState.value.ultimoResultado)

        agenda.respuesta = CompletableDeferred()
        model.actualizar() // tras la falla se puede volver a pedir
        assertEquals(2, agenda.llamadas)
    }

    @Test fun losFiltrosSobrevivenEnElSavedStateHandle() {
        val estado = SavedStateHandle()
        ProgramaModel(AgendaFalsa(), SesionDeCarga(), estado).apply { elegirDia(2); elegirTema("Datos") }
        // Proceso recreado: Android devuelve el mismo SavedStateHandle al nuevo ViewModel.
        val recreado = ProgramaModel(AgendaFalsa(), SesionDeCarga(), estado)
        assertEquals(2, recreado.uiState.value.dia)
        assertEquals("Datos", recreado.uiState.value.tema)
        // Tarea nueva: handle vacío, sin filtros.
        val nueva = ProgramaModel(AgendaFalsa(), SesionDeCarga(), SavedStateHandle())
        assertEquals(0, nueva.uiState.value.dia)
        assertEquals("Todos", nueva.uiState.value.tema)
    }
}
