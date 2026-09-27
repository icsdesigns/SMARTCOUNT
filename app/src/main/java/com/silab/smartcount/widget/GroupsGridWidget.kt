package com.silab.smartcount.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.LocalContext
import androidx.glance.LocalSize
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.lazy.GridCells
import androidx.glance.appwidget.lazy.LazyVerticalGrid
import androidx.glance.appwidget.lazy.items
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import com.silab.smartcount.MainActivity
import com.silab.smartcount.data.cache.CachedGroup
import com.silab.smartcount.data.cache.GroupCache
import com.silab.smartcount.ui.formatMoney

/**
 * Rejilla de grupos: todos los grupos activos de un vistazo, con su cifra, y
 * un botón para apuntar un movimiento sin pasar por ninguno.
 */
class GroupsGridWidget : GlanceAppWidget() {

    // Exacto: el número de columnas sale del ancho real que tenga el widget.
    override val sizeMode: SizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val groups = GroupCache(context).read().groups
            .filterNot { it.archived }
            .sortedByDescending { it.recency }
        provideContent { GlanceTheme { Content(groups) } }
    }

    @Composable
    private fun Content(groups: List<CachedGroup>) {
        val context = LocalContext.current
        val columns = (LocalSize.current.width.value / CELL_MIN_WIDTH).toInt().coerceIn(1, 4)

        Column(GlanceModifier.fillMaxSize().widgetSurface().padding(12.dp)) {
            Row(
                GlanceModifier.fillMaxWidth().padding(start = 4.dp, bottom = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "Grupos",
                    style = TextStyle(color = WidgetColors.text, fontSize = 15.sp, fontWeight = FontWeight.Bold),
                    modifier = GlanceModifier.defaultWeight()
                        .clickable(actionStartActivity(appIntent(context, MainActivity.ACTION_OPEN_GROUPS)))
                )
                WidgetChip(
                    "+ Movimiento",
                    appIntent(context, MainActivity.ACTION_NEW_EXPENSE),
                    background = WidgetColors.brand,
                    color = WidgetColors.onBrand
                )
            }

            if (groups.isEmpty()) {
                Text(
                    "Añade un grupo en SmartCount para verlo aquí",
                    style = TextStyle(color = WidgetColors.text2, fontSize = 13.sp),
                    modifier = GlanceModifier.padding(horizontal = 4.dp)
                )
                return@Column
            }

            LazyVerticalGrid(
                gridCells = GridCells.Fixed(columns),
                modifier = GlanceModifier.fillMaxSize()
            ) {
                items(groups, itemId = { it.id.toLong() }) { g ->
                    // El hueco entre celdas se hace con el relleno de cada una:
                    // la rejilla de Glance no admite espaciado propio.
                    Box(GlanceModifier.padding(3.dp)) { Cell(g) }
                }
            }
        }
    }

    @Composable
    private fun Cell(g: CachedGroup) {
        val context = LocalContext.current
        Column(
            GlanceModifier
                .fillMaxWidth()
                .background(WidgetColors.chip)
                .cornerRadius(16.dp)
                .padding(horizontal = 10.dp, vertical = 9.dp)
                .clickable(actionStartActivity(openGroupIntent(context, g.id)))
        ) {
            Text(
                groupTitle(g),
                style = TextStyle(color = WidgetColors.text2, fontSize = 12.sp, fontWeight = FontWeight.Medium),
                maxLines = 1
            )
            Spacer(GlanceModifier.height(2.dp))
            Text(
                formatMoney(g.headline, g.currency, signed = true),
                style = TextStyle(
                    color = WidgetColors.money(g.headline),
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                ),
                maxLines = 1
            )
            Text(
                headlineLabel(g),
                style = TextStyle(color = WidgetColors.text2, fontSize = 11.sp),
                maxLines = 1
            )
        }
    }

    private companion object {
        /** Lo mínimo que necesita una celda para que la cifra no se corte. */
        const val CELL_MIN_WIDTH = 120f
    }
}

class GroupsGridWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = GroupsGridWidget()
}
