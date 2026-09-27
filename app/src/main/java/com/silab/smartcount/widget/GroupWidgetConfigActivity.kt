package com.silab.smartcount.widget

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.datastore.preferences.core.Preferences
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.glance.state.PreferencesGlanceStateDefinition
import androidx.lifecycle.lifecycleScope
import com.silab.smartcount.data.cache.CachedGroup
import com.silab.smartcount.data.cache.GroupCache
import com.silab.smartcount.ui.SmartDivider
import com.silab.smartcount.ui.SmartRow
import com.silab.smartcount.ui.formatMoney
import com.silab.smartcount.ui.theme.ScreenPadding
import com.silab.smartcount.ui.theme.SmartCountTheme
import com.silab.smartcount.ui.theme.SmartTheme
import kotlinx.coroutines.launch

/**
 * Elegir el grupo del widget de grupo: se abre al ponerlo y cada vez que se
 * quiera cambiar. Ofrece todos los grupos, también los de ahorro y los
 * cerrados: el widget es de quien lo pone y lo que quiera ver es asunto suyo.
 */
class GroupWidgetConfigActivity : ComponentActivity() {

    private var appWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        appWidgetId = intent?.getIntExtra(
            AppWidgetManager.EXTRA_APPWIDGET_ID,
            AppWidgetManager.INVALID_APPWIDGET_ID
        ) ?: AppWidgetManager.INVALID_APPWIDGET_ID
        // Salir sin elegir no deja un widget vacío en la pantalla de inicio.
        setResult(Activity.RESULT_CANCELED, resultIntent())
        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            finish()
            return
        }

        val snapshot = GroupCache(this).read()
        val groups = snapshot.groups.sortedWith(
            compareBy<CachedGroup> { it.archived }.thenByDescending { it.recency }
        )
        enableEdgeToEdge()
        setContent { SmartCountTheme { Chooser(groups) } }
    }

    private fun resultIntent() =
        Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)

    private fun choose(group: CachedGroup) {
        lifecycleScope.launch {
            val glanceId = GlanceAppWidgetManager(this@GroupWidgetConfigActivity)
                .getGlanceIdBy(appWidgetId)
            updateAppWidgetState(
                this@GroupWidgetConfigActivity,
                PreferencesGlanceStateDefinition,
                glanceId
            ) { prefs: Preferences ->
                prefs.toMutablePreferences().apply { this[GroupWidget.GROUP_ID] = group.id }
            }
            GroupWidget().update(this@GroupWidgetConfigActivity, glanceId)
            setResult(Activity.RESULT_OK, resultIntent())
            finish()
        }
    }

    @androidx.compose.runtime.Composable
    private fun Chooser(groups: List<CachedGroup>) {
        val c = SmartTheme.colors
        Column(
            Modifier
                .fillMaxSize()
                .background(c.background)
                .statusBarsPadding()
                .navigationBarsPadding()
        ) {
            Text(
                "Elige un grupo",
                style = MaterialTheme.typography.headlineLarge,
                color = c.primaryText,
                modifier = Modifier.padding(ScreenPadding)
            )
            if (groups.isEmpty()) {
                Text(
                    "Abre SmartCount para cargar tus grupos y vuelve a poner el widget.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = c.secondaryText,
                    modifier = Modifier.padding(horizontal = ScreenPadding)
                )
                return@Column
            }
            LazyColumn(Modifier.fillMaxSize()) {
                items(groups, key = { it.id }) { g ->
                    SmartRow(
                        title = g.title,
                        subtitle = if (g.archived) "Cerrado · ${headlineLabel(g)}" else headlineLabel(g),
                        value = formatMoney(g.headline, g.currency, signed = true),
                        valueColor = if (g.headline < 0) c.negative else c.positive,
                        leading = {
                            Box(
                                Modifier
                                    .width(38.dp).height(38.dp)
                                    .clip(RoundedCornerShape(100))
                                    .background(c.chipBackground),
                                contentAlignment = Alignment.Center
                            ) { Text(g.emoji ?: "•") }
                        },
                        onClick = { choose(g) }
                    )
                    SmartDivider()
                }
            }
        }
    }
}
