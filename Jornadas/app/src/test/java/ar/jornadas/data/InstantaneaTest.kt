package ar.jornadas.data

import org.junit.Assert.*
import org.junit.Test

/** Evidencia del pedido 42 y de la validación de fichas, con los datos de v1/v2/v3 del enunciado. */
class InstantaneaTest {
    private fun charla(id: String, titulo: String = "Charla $id", sala: String = "Auditorio", favorita: Boolean = false) =
        Charla(id, titulo, "Android", 1, "09:00", sala, "Ponente", "Resumen", favorita)

    @Test fun v2ConservaLaMarcaDeLa11ConTituloYSalaNuevos() {
        val v2 = listOf(charla("11", "Compose en producción avanzada", "Sala Puerto"), charla("12"))
        val resultado = Instantanea.fusionar(v2, favoritasActuales = setOf("11"))
        val la11 = resultado.first { it.id == "11" }
        assertTrue(la11.favorita)
        assertEquals("Compose en producción avanzada", la11.titulo)
        assertEquals("Sala Puerto", la11.sala)
        assertFalse(resultado.first { it.id == "12" }.favorita)
    }

    @Test fun marcaHechaDuranteLaPeticionSeRespeta() {
        // La 24 se marcó mientras viajaba la respuesta: la marca se lee al escribir, no antes.
        val respuesta = listOf(charla("24", favorita = false), charla("90"))
        val resultado = Instantanea.fusionar(respuesta, favoritasActuales = setOf("24"))
        assertTrue(resultado.first { it.id == "24" }.favorita)
        assertFalse(resultado.first { it.id == "90" }.favorita)
    }

    @Test fun v3InformaLa11RetiradaDeMiAgenda() {
        val favoritas = listOf(charla("11", favorita = true), charla("24", favorita = true))
        val v3 = listOf(charla("24"), charla("90"))
        assertEquals(listOf("11"), Instantanea.favoritasRetiradas(favoritas, v3).map { it.id })
    }

    @Test fun charlaQueRegresaVuelveSinMarcar() {
        // Al retirarse se borró su fila (y su marca): ya no figura entre las favoritas actuales.
        val regreso = Instantanea.fusionar(listOf(charla("11")), favoritasActuales = emptySet())
        assertFalse(regreso.single().favorita)
    }

    @Test fun v4VaciaEsValidaYNoGeneraCharlas() {
        assertTrue(Instantanea.fusionar(emptyList(), setOf("11")).isEmpty())
    }

    @Test fun fichaIncompletaSeRechaza() {
        val completa = FichaRemota("11", "T", "Android", 1, "09:00", "Auditorio", "P", "R")
        assertNotNull(Instantanea.aCharla(completa))
        assertNull(Instantanea.aCharla(completa.copy(title = null)))
        assertNull(Instantanea.aCharla(completa.copy(id = " ")))
        assertNull(Instantanea.aCharla(completa.copy(day = null)))
    }
}
