package com.silab.smartcount.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.LocalContext
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.lazy.LazyColumn
import androidx.glance.appwidget.lazy.items
import androidx.glance.appwidget.provideContent
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import com.silab.smartcount.MainActivity
import com.silab.smartcount.SmartCountApp
import com.silab.smartcount.data.db.InboxEntry
import com.silab.smartcount.notif.DetectionNotifier
import com.silab.smartcount.ui.formatMoney
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * La bandeja en la pantalla de inicio: lo detectado que falta por llevar a un
 * grupo. Tocar un movimiento abre directamente su hoja de asignación.
 */
class InboxWidget : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val app = context.applicationContext as SmartCountApp
        // Solo los movimientos, como la chapa de la pestaña: los avisos que no
        // lo son se revisan dentro de la app, no desde el inicio.
        val pending = runCatching { app.database.inboxDao().pending() }
            .getOrDefault(emptyList())
            .filter { it.isBankMovement }
        provideContent { GlanceTheme { Content(pending) } }
    }

    @Composable
    private fun Content(pending: List<InboxEntry>) {
        val context = LocalContext.current
        val inbox = actionStartActivity(appIntent(context, MainActivity.ACTION_OPEN_INBOX))

        Column(GlanceModifier.fillMaxSize().widgetSurface().padding(horizontal = 16.dp, vertical = 14.dp)) {
            Row(
                GlanceModifier.fillMaxWidth().padding(bottom = 8.dp).clickable(inbox),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "Bandeja",
                    style = TextStyle(color = WidgetColors.text, fontSize = 15.sp, fontWeight = FontWeight.Bold),
                    modifier = GlanceModifier.defaultWeight()
                )
                if (pending.isNotEmpty()) {
                    Text(
                        if (pending.size == 1) "1 pendiente" else "${pending.size} pendientes",
                        style = TextStyle(color = WidgetColors.brand, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                    )
                }
            }

            if (pending.isEmpty()) {
                Column(
                    GlanceModifier.fillMaxSize().clickable(inbox),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text("Nada pendiente", style = TextStyle(color = WidgetColors.text2, fontSize = 14.sp))
                }
                return@Column
            }

            val fmt = SimpleDateFormat("d MMM · HH:mm", Locale.getDefault())
            LazyColumn(GlanceModifier.fillMaxSize()) {
                items(pending, itemId = { it.id }) { e -> Entry(e, fmt) }
            }
        }
    }

    @Composable
    private fun Entry(e: InboxEntry, fmt: SimpleDateFormat) {
        val context = LocalContext.current
        val intent = appIntent(context, MainActivity.ACTION_OPEN_INBOX, "inbox/${e.id}")
            .putExtra(DetectionNotifier.EXTRA_ENTRY_ID, e.id)
        val amount = e.amount ?: 0.0
        val signed = if (e.kind.isMoneyIn) amount else -amount

        Row(
            GlanceModifier.fillMaxWidth().padding(vertical = 6.dp)
                .clickable(actionStartActivity(intent)),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(GlanceModifier.defaultWeight()) {
                Text(
                    e.merchant ?: e.counterparty ?: e.kind.label,
                    style = TextStyle(color = WidgetColors.text, fontSize = 14.sp, fontWeight = FontWeight.Medium),
                    maxLines = 1
                )
                Text(
                    "${e.bankLabel} · ${fmt.format(Date(e.detectedAt))}",
                    style = TextStyle(color = WidgetColors.text2, fontSize = 11.sp),
                    maxLines = 1
                )
            }
            Spacer(GlanceModifier.width(8.dp))
            Text(
                if (e.amount == null) "—" else formatMoney(signed, e.currency, signed = true),
                style = TextStyle(color = WidgetColors.money(signed), fontSize = 14.sp, fontWeight = FontWeight.Medium),
                maxLines = 1
            )
        }
    }
}

class InboxWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = InboxWidget()
}
