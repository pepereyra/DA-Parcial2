package ar.jornadas.data

/**
 * Resultado explícito de una actualización. Reemplaza al String "aviso": cada caso es excluyente
 * y el `when` de la UI es exhaustivo, así no quedan combinaciones imposibles ni un error técnico
 * disfrazado de lista vacía.
 */
sealed interface ResultadoSync {
    /** [retiradasDeMiAgenda]: títulos de charlas marcadas que la organización retiró en esta edición. */
    data class Ok(val retiradasDeMiAgenda: List<String> = emptyList()) : ResultadoSync
    /** Retrofit lanzó IOException: no hubo conexión con el servicio. */
    data object SinConexion : ResultadoSync
    /** El servicio respondió con un código HTTP de error (Retrofit lanza HttpException). */
    data class ErrorServidor(val codigo: Int) : ResultadoSync
    /** La respuesta llegó pero no se puede usar (JSON inválido o fichas incompletas). */
    data object DatosInvalidos : ResultadoSync
    /** La descarga salió bien pero falló la escritura local: la transacción revirtió todo. */
    data object ErrorGuardado : ResultadoSync
}

/** Reglas puras de la instantánea (sin Android, testeables con JUnit). */
object Instantanea {
    /**
     * Gson ignora la nulabilidad de Kotlin: si falta un campo, deja null en una propiedad "no nula"
     * y explota más tarde en la UI. Se valida acá; una ficha incompleta invalida la instantánea entera
     * (es una foto completa: aplicarla a medias dejaría un programa inconsistente).
     */
    fun aCharla(f: FichaRemota): Charla? {
        val id = f.id?.takeIf { it.isNotBlank() } ?: return null
        return Charla(id, f.title ?: return null, f.track ?: return null, f.day ?: return null,
            f.time ?: return null, f.room ?: return null, f.speaker.orEmpty(), f.summary.orEmpty())
    }

    /** Pedido 42: las vigentes conservan la marca que tienen AHORA; las nuevas entran sin marcar. */
    fun fusionar(nuevas: List<Charla>, favoritasActuales: Set<String>): List<Charla> =
        nuevas.map { it.copy(favorita = it.id in favoritasActuales) }

    /** Charlas marcadas que no vienen en la instantánea nueva (se borran junto con su marca). */
    fun favoritasRetiradas(favoritas: List<Charla>, nuevas: List<Charla>): List<Charla> {
        val vigentes = nuevas.mapTo(HashSet()) { it.id }
        return favoritas.filter { it.id !in vigentes }
    }
}
