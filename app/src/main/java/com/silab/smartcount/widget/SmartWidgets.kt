package com.silab.smartcount.widget

import android.content.Context
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.updateAll
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/** Repinta todos los widgets tras cambiar la caché o la bandeja. */
object SmartWidgets {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    fun refresh(context: Context) {
        scope.launch {
            runCatching { GroupsGridWidget().updateAll(context) }
            runCatching { GroupWidget().updateAll(context) }
            runCatching { InboxWidget().updateAll(context) }
        }
    }

    suspend fun hasAny(context: Context): Boolean {
        val manager = GlanceAppWidgetManager(context)
        return listOf(GroupsGridWidget::class.java, GroupWidget::class.java, InboxWidget::class.java)
            .any { manager.getGlanceIds(it).isNotEmpty() }
    }
}
