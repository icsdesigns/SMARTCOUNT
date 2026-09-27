package com.silab.smartcount.widget

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceModifier
import androidx.glance.action.clickable
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.background
import androidx.glance.color.ColorProvider
import androidx.glance.layout.padding
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.silab.smartcount.MainActivity
import com.silab.smartcount.data.cache.CachedGroup

/**
 * El lenguaje visual que comparten los tres widgets: el mismo de la app, con
 * el azul de marca para lo que no es dinero y verde/rojo para lo que sí.
 */
internal object WidgetColors {
    val bg = ColorProvider(day = Color(0xFFFFFFFF), night = Color(0xFF000000))
    val text = ColorProvider(day = Color(0xFF07070A), night = Color(0xFFFFFFFF))
    val text2 = ColorProvider(day = Color(0xFF74747A), night = Color(0xFF8A8A8E))
    val chip = ColorProvider(day = Color(0xFFF2F2F4), night = Color(0xFF161618))
    val pos = ColorProvider(day = Color(0xFF00B862), night = Color(0xFF00C46A))
    val brand = ColorProvider(day = Color(0xFF3355E6), night = Color(0xFF4A6CFF))
    val onBrand = ColorProvider(day = Color(0xFFFFFFFF), night = Color(0xFFFFFFFF))
    val neg = ColorProvider(day = Color(0xFFFF3B3B), night = Color(0xFFFF4D4D))

    fun money(v: Double): ColorProvider = if (v < 0) neg else pos
}

/** Fondo común: esquinas de 24 dp como las tarjetas de la app. */
internal fun GlanceModifier.widgetSurface(): GlanceModifier =
    background(WidgetColors.bg).cornerRadius(24.dp)

/** «te deben», «debes» o «ahorrado», según el grupo y el signo. */
internal fun headlineLabel(g: CachedGroup): String = when {
    g.savings -> "ahorrado"
    g.headline >= 0 -> "te deben"
    else -> "debes"
}

internal fun groupTitle(g: CachedGroup): String = "${g.emoji ?: ""} ${g.title}".trim()

/**
 * Intención explícita hacia la app.
 *
 * Cada destino lleva su propia URI: dos PendingIntent que solo se diferencian
 * en los extras el sistema los trata como el mismo, y tocar un grupo acabaría
 * abriendo otro.
 */
internal fun appIntent(context: Context, action: String, key: String = action): Intent =
    Intent(context, MainActivity::class.java)
        .setAction(action)
        .setData(Uri.parse("smartcount://widget/$key"))
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)

internal fun openGroupIntent(context: Context, groupId: Int): Intent =
    appIntent(context, MainActivity.ACTION_OPEN_GROUP, "group/$groupId")
        .putExtra(MainActivity.EXTRA_GROUP_ID, groupId)

/** Botón en píldora, como los chips de la app. */
@Composable
internal fun WidgetChip(
    label: String,
    intent: Intent,
    background: ColorProvider = WidgetColors.chip,
    color: ColorProvider = WidgetColors.text
) {
    Text(
        label,
        style = TextStyle(color = color, fontSize = 13.sp, fontWeight = FontWeight.Medium),
        maxLines = 1,
        modifier = GlanceModifier
            .background(background)
            .cornerRadius(100.dp)
            .padding(horizontal = 12.dp, vertical = 7.dp)
            .clickable(actionStartActivity(intent))
    )
}
