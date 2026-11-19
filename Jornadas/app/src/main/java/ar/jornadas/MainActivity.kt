package ar.jornadas

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import ar.jornadas.ui.*

class MainActivity : ComponentActivity() {
    private val model: ProgramaModel by viewModels { ProgramaModel.Factory }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null && intent.hasExtra("servicio_disponible")) {
            model.mesa.disponible = intent.getBooleanExtra("servicio_disponible", true)
            intent.removeExtra("servicio_disponible")
        }
        // Pedido 44: antes se recargaba en cada onStart (rotar o volver del fondo = nueva petición).
        // Ahora hay una sola carga automática por proceso, después de aplicar el extra de prueba.
        model.cargaAutomatica()
        setContent { Portada(model) }
    }
}
