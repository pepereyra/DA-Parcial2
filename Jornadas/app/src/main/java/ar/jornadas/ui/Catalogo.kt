package ar.jornadas.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ar.jornadas.data.Charla
import ar.jornadas.data.ResultadoSync

@Composable
fun Catalogo(model: ProgramaModel, estadoLista: LazyListState, abrir: (String) -> Unit) {
    // Pedido 44: sin LaunchedEffect de recarga; un composable no dispara I/O y cambiar de pestaña
    // o volver de una ficha no agrega peticiones.
    val estado by model.uiState.collectAsStateWithLifecycle()
    val temas = listOf("Todos", "Android", "Datos", "Calidad")
    // Los filtros siguen siendo editables durante la espera.
    Selector(listOf("Ambos días", "Día 1", "Día 2"), estado.dia) { model.elegirDia(it) }
    Selector(temas, temas.indexOf(estado.tema).coerceAtLeast(0)) { model.elegirTema(temas[it]) }
    TextButton(onClick = model::actualizar) { Text("Actualizar") }
    if (estado.cargando) LinearProgressIndicator(Modifier.fillMaxWidth())

    val actual = estado.cartelera ?: return // Room todavía no respondió
    // Pedido 41: una falla ya no reemplaza la lista. Con edición previa se avisa y se sigue mostrando.
    when (val r = estado.ultimoResultado) {
        null -> {}
        is ResultadoSync.Ok -> if (r.retiradasDeMiAgenda.isNotEmpty())
            Text("Se retiró del programa: ${r.retiradasDeMiAgenda.joinToString()}. Ya no está en Mi agenda.")
        else -> if (actual.hayEdicion) {
            Text("${causa(r)}: se están usando datos guardados.")
        } else {
            Text("No se pudo descargar el programa (${causa(r).lowercase()}).")
            OutlinedButton(onClick = model::actualizar, enabled = !estado.cargando) { Text("Reintentar") }
        }
    }
    if (!actual.hayEdicion) return
    if (actual.charlas.isEmpty()) {
        Text("Sin charlas programadas.")
        return
    }
    val visibles = actual.charlas.filter {
        (estado.dia == 0 || it.dia == estado.dia) && (estado.tema == "Todos" || it.tema == estado.tema)
    }
    if (visibles.isEmpty()) Text("No hay charlas con estos filtros.")
    Grilla(visibles, abrir, estadoLista)
}

private fun causa(r: ResultadoSync): String = when (r) {
    ResultadoSync.SinConexion -> "Sin conexión"
    is ResultadoSync.ErrorServidor -> "El servidor respondió con error ${r.codigo}"
    ResultadoSync.DatosInvalidos -> "La respuesta del servidor no es válida"
    ResultadoSync.ErrorGuardado -> "No se pudo guardar la edición nueva"
    is ResultadoSync.Ok -> "Actualizado"
}

@Composable
private fun Selector(opciones: List<String>, indice: Int, elegir: (Int) -> Unit) {
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        opciones.forEachIndexed { i, texto ->
            FilterChip(selected = i == indice, onClick = { elegir(i) }, label = { Text(texto) })
        }
    }
}

@Composable
fun Grilla(fichas: List<Charla>, abrir: (String) -> Unit, estado: LazyListState = rememberLazyListState()) {
    LazyColumn(state = estado, verticalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(bottom = 12.dp)) {
        items(fichas, key = { it.id }) { ficha ->
            ElevatedCard(onClick = { abrir(ficha.id) }, modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Día ${ficha.dia} · ${ficha.hora} · ${ficha.sala}", style = MaterialTheme.typography.labelMedium)
                    Text(ficha.titulo, style = MaterialTheme.typography.titleLarge)
                    Text(if (ficha.favorita) "${ficha.tema} · En mi agenda" else ficha.tema)
                }
            }
        }
    }
}
