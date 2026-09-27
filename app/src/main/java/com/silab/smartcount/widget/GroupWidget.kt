package com.silab.smartcount.widget

import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.LocalContext
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.lazy.LazyColumn
import androidx.glance.appwidget.lazy.items
import androidx.glance.appwidget.provideContent
import androidx.glance.currentState
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import com.silab.smartcount.data.cache.CachedGroup
import com.silab.smartcount.data.cache.CachedMovement
import com.silab.smartcount.data.cache.GroupCache
import com.silab.smartcount.ui.formatMoney

/**
 * Un grupo concreto, elegido al poner el widget: la cifra que lo resume, los
 * saldos de cada uno y sus movimientos, todo con scroll.
 */
class GroupWidget : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val groups = GroupCache(context).read().groups
        // Si el grupo ya no está, el toque vuelve a la pantalla de elegir.
        val appWidgetId = GlanceAppWidgetManager(context).getAppWidgetId(id)
        val chooser = Intent(context, GroupWidgetConfigActivity::class.java)
            .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
            .setData(android.net.Uri.parse("smartcount://widget/configure/$appWidgetId"))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        provideContent {
            val groupId = currentState<Preferences>()[GROUP_ID]
            GlanceTheme { Content(groups.firstOrNull { it.id == groupId }, chooser) }
        }
    }

    @Composable
    private fun Content(g: CachedGroup?, chooser: Intent) {
        val context = LocalContext.current
        if (g == null) {
            Column(
                GlanceModifier.fillMaxSize().widgetSurface().padding(16.dp)
                    .clickable(actionStartActivity(chooser))
            ) {
                Text("SmartCount", style = TextStyle(color = WidgetColors.text, fontSize = 15.sp, fontWeight = FontWeight.Medium))
                Spacer(GlanceModifier.height(6.dp))
                Text("Toca para elegir un grupo", style = TextStyle(color = WidgetColors.brand, fontSize = 13.sp))
            }
            return
        }
        val open = actionStartActivity(openGroupIntent(context, g.id))

        Column(GlanceModifier.fillMaxSize().widgetSurface().padding(horizontal = 16.dp, vertical = 14.dp)) {
            Column(GlanceModifier.fillMaxWidth().clickable(open)) {
                Text(
                    groupTitle(g),
                    style = TextStyle(color = WidgetColors.text2, fontSize = 13.sp, fontWeight = FontWeight.Medium),
                    maxLines = 1
                )
                Spacer(GlanceModifier.height(2.dp))
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        formatMoney(g.headline, g.currency, signed = true),
                        style = TextStyle(color = WidgetColors.money(g.headline), fontSize = 24.sp, fontWeight = FontWeight.Bold),
                        maxLines = 1
                    )
                    Spacer(GlanceModifier.width(6.dp))
                    Text(
                        headlineLabel(g),
                        style = TextStyle(color = WidgetColors.text2, fontSize = 12.sp),
                        modifier = GlanceModifier.padding(bottom = 4.dp)
                    )
                }
            }
            Spacer(GlanceModifier.height(8.dp))

            LazyColumn(GlanceModifier.fillMaxSize()) {
                // En un grupo de ahorro no hay deudas entre miembros: lo que
                // cuenta es lo que entra y lo que sale.
                if (g.savings) {
                    item { Section("Resumen") }
                    item { Line("Ingresado", formatMoney(g.income, g.currency), WidgetColors.pos, open = open) }
                    item { Line("Gastado", formatMoney(-g.spent, g.currency), WidgetColors.neg, open = open) }
                } else if (g.balances.isNotEmpty()) {
                    item { Section("Saldos") }
                    items(g.balances, itemId = { it.uuid.hashCode().toLong() }) { b ->
                        val me = b.uuid == g.myMembershipUuid
                        Line(
                            if (me) "${b.name} (tú)" else b.name,
                            formatMoney(b.amount, g.currency, signed = true),
                            WidgetColors.money(b.amount),
                            open = open
                        )
                    }
                }

                item { Section("Movimientos") }
                if (g.movements.isEmpty()) {
                    item {
                        Text(
                            "Aún no hay movimientos",
                            style = TextStyle(color = WidgetColors.text2, fontSize = 13.sp),
                            modifier = GlanceModifier.padding(vertical = 4.dp)
                        )
                    }
                }
                items(g.movements) { m -> Movement(m, g, open) }
            }
        }
    }

    @Composable
    private fun Section(label: String) {
        Text(
            label,
            style = TextStyle(color = WidgetColors.text2, fontSize = 12.sp, fontWeight = FontWeight.Medium),
            modifier = GlanceModifier.padding(top = 8.dp, bottom = 4.dp)
        )
    }

    @Composable
    private fun Line(
        label: String,
        value: String,
        color: androidx.glance.unit.ColorProvider,
        open: androidx.glance.action.Action
    ) {
        Row(
            GlanceModifier.fillMaxWidth().padding(vertical = 4.dp).clickable(open),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                label,
                style = TextStyle(color = WidgetColors.text, fontSize = 14.sp),
                maxLines = 1,
                modifier = GlanceModifier.defaultWeight()
            )
            Text(value, style = TextStyle(color = color, fontSize = 14.sp, fontWeight = FontWeight.Medium), maxLines = 1)
        }
    }

    /**
     * Como en la app: en un grupo de ahorro el color dice si el dinero entra o
     * sale; en uno normal el importe va en el color del texto.
     */
    @Composable
    private fun Movement(m: CachedMovement, g: CachedGroup, open: androidx.glance.action.Action) {
        Row(
            GlanceModifier.fillMaxWidth().padding(vertical = 5.dp).clickable(open),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(m.emoji, style = TextStyle(fontSize = 16.sp), modifier = GlanceModifier.width(26.dp))
            Column(GlanceModifier.defaultWeight()) {
                Text(
                    m.title,
                    style = TextStyle(color = WidgetColors.text, fontSize = 14.sp, fontWeight = FontWeight.Medium),
                    maxLines = 1
                )
                Text(m.subtitle, style = TextStyle(color = WidgetColors.text2, fontSize = 11.sp), maxLines = 1)
            }
            Spacer(GlanceModifier.width(8.dp))
            Text(
                when (m.income) {
                    null -> formatMoney(m.amount, g.currency)
                    true -> formatMoney(m.amount, g.currency, signed = true)
                    false -> formatMoney(-m.amount, g.currency, signed = true)
                },
                style = TextStyle(
                    color = when (m.income) {
                        null -> WidgetColors.text
                        true -> WidgetColors.pos
                        false -> WidgetColors.neg
                    },
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium
                ),
                maxLines = 1
            )
        }
    }

    companion object {
        /** El grupo que enseña cada widget, guardado en su propio estado. */
        val GROUP_ID = intPreferencesKey("group_id")
    }
}

class GroupWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = GroupWidget()
}
