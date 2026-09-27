package com.silab.smartcount.data.repo

import android.content.Context

/**
 * Los grupos creados desde SmartCount, que **no están en tu cuenta de Tricount**.
 *
 * SmartCount no entra con tu cuenta: la API interna de Tricount no tiene
 * inicio de sesión con usuario y contraseña, sino una *instalación* propia
 * (ver TricountClient). Los grupos a los que te unes pegando su enlace ya
 * existían en Tricount, así que siguen allí; pero uno creado aquí solo lo
 * conoce esta instalación, y la app oficial no lo enseña hasta que abras su
 * enlace en ella.
 *
 * No hay forma de preguntar a la API si ya lo abriste en Tricount, así que la
 * marca vive en este móvil y se quita a mano.
 */
class UnlinkedGroups(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences("unlinked_groups", Context.MODE_PRIVATE)

    fun ids(): Set<Int> =
        prefs.getStringSet(KEY, emptySet()).orEmpty().mapNotNull { it.toIntOrNull() }.toSet()

    fun mark(id: Int, unlinked: Boolean) {
        val updated = if (unlinked) ids() + id else ids() - id
        prefs.edit().putStringSet(KEY, updated.map(Int::toString).toSet()).apply()
    }

    private companion object {
        const val KEY = "ids"
    }
}
