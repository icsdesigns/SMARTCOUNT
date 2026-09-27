package com.silab.smartcount.ui

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.Inbox
import androidx.compose.material.icons.outlined.People
import androidx.compose.material.icons.outlined.Savings
import androidx.compose.material.icons.outlined.School
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.silab.smartcount.SmartCountApp
import com.silab.smartcount.notif.BankNotificationListener
import com.silab.smartcount.notif.LearnMode
import com.silab.smartcount.ui.theme.SectionTitle
import com.silab.smartcount.ui.theme.SmartTheme
import com.silab.smartcount.update.UpdateViewModel

// ===========================================================================
// Tour de inicio
// ===========================================================================

/**
 * Qué versión ha visto ya el tour. Se guarda el versionCode y no el nombre:
 * crece con cada commit, así que el tour vuelve con cada actualización que
 * llega al móvil —que es cuando hay algo nuevo que contar— y no solo cuando
 * cambia el número que se enseña.
 */
object TourPrefs {
    private const val FILE = "tour"
    private const val KEY = "seen_version_code"

    fun shouldShow(context: Context, versionCode: Long): Boolean =
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE).getLong(KEY, -1L) != versionCode

    fun markSeen(context: Context, versionCode: Long) {
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit().putLong(KEY, versionCode).apply()
    }
}

private enum class StepKind { INFO, PERMISSIONS, LEARN }

private data class TourStep(
    val icon: ImageVector,
    val title: String,
    val body: String,
    val kind: StepKind = StepKind.INFO
)

/**
 * Un recorrido por las cinco pestañas y, al final, lo que hay que dejar
 * configurado para que la app funcione: los permisos y el modo aprendizaje.
 * Termina ahí porque sin permisos la bandeja no recibe nada y nada en la
 * pantalla principal lo delata.
 */
private val TOUR = listOf(
    TourStep(
        Icons.Outlined.People, "Grupos",
        "Tus tricounts en una rejilla, con lo que te deben o debes. Dentro, los carteles " +
            "de balances y roles, los movimientos y el plan para saldar cuentas."
    ),
    TourStep(
        Icons.Outlined.Savings, "Ahorro",
        "Grupos leídos como ingresos y gastos. Decide quién hace los ingresos y quién " +
            "gasta, y la app te dice cuánto queda."
    ),
    TourStep(
        Icons.Outlined.BarChart, "Estadísticas",
        "Elige grupo y periodo en los desplegables y mira en qué se va el dinero: por " +
            "categoría, por persona o por mes, del grupo entero o solo tu parte."
    ),
    TourStep(
        Icons.Outlined.Inbox, "Bandeja",
        "Los Bizum, transferencias y pagos que detecta la app en las notificaciones de " +
            "tu banco. Asígnalos a uno o varios grupos, clasifícalos o límpiala entera."
    ),
    TourStep(
        Icons.Outlined.Tune, "Ajustes",
        "Permisos, qué apps se vigilan, de qué te avisamos y las actualizaciones. " +
            "Desde «Acerca de» puedes volver a ver este tour."
    ),
    TourStep(
        Icons.Outlined.Shield, "Permisos que hay que conceder",
        "Sin ellos la bandeja no recibe nada ni la app puede avisarte o actualizarse.",
        StepKind.PERMISSIONS
    ),
    TourStep(
        Icons.Outlined.School, "Modo aprendizaje",
        "Hasta dónde mira la app. Si tu banco no está en la lista, ponlo en Completo: " +
            "las apps que notifiquen aparecerán en Ajustes para activarlas.",
        StepKind.LEARN
    )
)

@Composable
fun TourDialog(updateVm: UpdateViewModel, onClose: () -> Unit) {
    val c = SmartTheme.colors
    val context = LocalContext.current
    val registry = (context.applicationContext as SmartCountApp).bankRegistry
    var step by remember { mutableIntStateOf(0) }
    var learn by remember { mutableStateOf(registry.learnMode) }

    // Los permisos se conceden fuera de la app: se releen al volver.
    var permTick by remember { mutableIntStateOf(0) }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                permTick++
                updateVm.recheckInstallPermission()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    val notifLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        permTick++
    }
    val update by updateVm.state.collectAsStateWithLifecycle()
    val canInstall = update.canInstall

    val current = TOUR[step]
    val last = step == TOUR.lastIndex

    Dialog(onDismissRequest = onClose, properties = DialogProperties(dismissOnClickOutside = false)) {
        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(24.dp))
                .background(c.background)
                .verticalScroll(rememberScrollState())
                .padding(20.dp)
        ) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "Versión ${updateVm.installedVersionName}",
                    style = SectionTitle,
                    color = c.brand,
                    modifier = Modifier
                        .clip(RoundedCornerShape(100))
                        .background(c.savingsTint)
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                )
                Text(
                    "✕",
                    color = c.secondaryText,
                    modifier = Modifier.clickable(onClick = onClose).padding(8.dp)
                )
            }

            Spacer(Modifier.height(12.dp))
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                Box(
                    Modifier
                        .size(56.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(c.savingsTint),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(current.icon, contentDescription = null, tint = c.brand, modifier = Modifier.size(28.dp))
                }
                Spacer(Modifier.height(12.dp))
                Text(
                    current.title,
                    style = MaterialTheme.typography.titleLarge,
                    color = c.primaryText,
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    current.body,
                    style = MaterialTheme.typography.bodyMedium,
                    color = c.secondaryText,
                    textAlign = TextAlign.Center
                )
            }

            when (current.kind) {
                StepKind.PERMISSIONS -> {
                    // Se releen cada vez que se vuelve a la app o responde el diálogo.
                    val listener = remember(permTick) { BankNotificationListener.hasAccess(context) }
                    val post = remember(permTick) {
                        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
                            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
                            PackageManager.PERMISSION_GRANTED
                    }
                    Spacer(Modifier.height(16.dp))
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        PermissionRow(
                            "Acceso a notificaciones",
                            "Para leer los avisos de tu banco y llevarlos a la bandeja.",
                            listener
                        ) { context.startActivity(Intent(BankNotificationListener.settingsIntentAction)) }
                        PermissionRow(
                            "Avisos de SmartCount",
                            "Para avisarte de cada movimiento detectado.",
                            post
                        ) {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                            }
                        }
                        PermissionRow(
                            "Instalar apps desconocidas",
                            "Para instalar las actualizaciones con un toque.",
                            canInstall
                        ) { updateVm.openPermissionSettings() }
                    }
                }
                StepKind.LEARN -> {
                    Spacer(Modifier.height(16.dp))
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        LearnMode.entries.forEach { mode ->
                            LearnOption(mode, mode == learn) {
                                learn = mode
                                registry.learnMode = mode
                            }
                        }
                    }
                }
                StepKind.INFO -> Unit
            }

            Spacer(Modifier.height(20.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                TOUR.indices.forEach { i ->
                    Box(
                        Modifier
                            .padding(horizontal = 3.dp)
                            .height(7.dp)
                            .width(if (i == step) 20.dp else 7.dp)
                            .clip(RoundedCornerShape(100))
                            .background(if (i == step) c.brand else c.divider)
                    )
                }
            }
            Spacer(Modifier.height(20.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(Modifier.weight(1f)) {
                    if (step > 0) {
                        SecondaryButton("Atrás", Modifier.fillMaxWidth()) { step-- }
                    }
                }
                Box(Modifier.weight(1f)) {
                    PrimaryButton(if (last) "Empezar" else "Siguiente") {
                        if (last) onClose() else step++
                    }
                }
            }
        }
    }
}

/** Un permiso con su estado y un botón para ir a concederlo. */
@Composable
private fun PermissionRow(title: String, body: String, granted: Boolean, onGrant: () -> Unit) {
    val c = SmartTheme.colors
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(c.chipBackground)
            .clickable(enabled = !granted, onClick = onGrant)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall, color = c.primaryText)
            Text(body, style = MaterialTheme.typography.bodySmall, color = c.secondaryText)
        }
        Spacer(Modifier.width(10.dp))
        Text(
            if (granted) "✓ Concedido" else "Conceder",
            color = if (granted) c.positive else c.brand,
            fontWeight = FontWeight.Medium,
            style = MaterialTheme.typography.bodySmall
        )
    }
}

/** Una de las tres opciones del modo aprendizaje, marcada si es la actual. */
@Composable
private fun LearnOption(mode: LearnMode, selected: Boolean, onClick: () -> Unit) {
    val c = SmartTheme.colors
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(if (selected) c.savingsTint else c.chipBackground)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                mode.label,
                style = MaterialTheme.typography.titleSmall,
                color = if (selected) c.brand else c.primaryText
            )
            Text(mode.description, style = MaterialTheme.typography.bodySmall, color = c.secondaryText)
        }
        if (selected) Text("✓", color = c.brand, fontWeight = FontWeight.Medium)
    }
}
