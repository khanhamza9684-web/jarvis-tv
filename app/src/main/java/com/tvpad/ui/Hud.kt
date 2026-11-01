package com.tvpad.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tvpad.GamepadState
import com.tvpad.LinkInfo
import com.tvpad.LinkStatus
import java.util.Locale

private fun statusColor(s: LinkStatus) = when (s) {
    LinkStatus.CONNECTED -> Color(0xFF3DDC84)
    LinkStatus.CONNECTING, LinkStatus.STARTING -> Color(0xFFFFC107)
    LinkStatus.READY -> Color(0xFF4FC3F7)
    LinkStatus.IDLE -> Color(0xFF9E9E9E)
    LinkStatus.UNSUPPORTED, LinkStatus.NO_PERMISSION -> Color(0xFFFF5252)
}

private fun statusLabel(i: LinkInfo) = when (i.status) {
    LinkStatus.CONNECTED -> "Connected · ${i.hostName ?: "host"}"
    LinkStatus.CONNECTING -> "Connecting · ${i.hostName ?: "host"}"
    LinkStatus.STARTING -> "Starting…"
    LinkStatus.READY -> "Ready — waiting for TV"
    LinkStatus.IDLE -> "Idle"
    LinkStatus.UNSUPPORTED -> "Unsupported"
    LinkStatus.NO_PERMISSION -> "Permission needed"
}

private fun f(v: Float) = String.format(Locale.US, "%+.2f", v)

/** Heads-up display: link status, report rate, send time, live inputs. */
@Composable
fun Hud(state: GamepadState, info: LinkInfo, modifier: Modifier = Modifier) {
    val mono = FontFamily.Monospace
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0xCC000000))
            .padding(horizontal = 12.dp, vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(Modifier.size(9.dp).clip(CircleShape).background(statusColor(info.status)))
            Text(statusLabel(info), color = Color.White, fontSize = 12.sp)
            Text("${info.reportsPerSec} Hz", color = Color(0xFFB0BEC5), fontSize = 11.sp, fontFamily = mono)
            Text("${String.format(Locale.US, "%.1f", info.lastSendMs)} ms", color = Color(0xFFB0BEC5), fontSize = 11.sp, fontFamily = mono)
            Text("#${info.totalReports}", color = Color(0xFF78909C), fontSize = 11.sp, fontFamily = mono)
        }
        Text(
            "L ${f(state.lx)},${f(state.ly)}   R ${f(state.rx)},${f(state.ry)}   LT ${String.format(Locale.US, "%.0f", state.lt * 100)}%  RT ${String.format(Locale.US, "%.0f", state.rt * 100)}%",
            color = Color(0xFFCFD8DC), fontSize = 11.sp, fontFamily = mono,
        )
        val pressed = state.pressedNames()
        Text(
            if (pressed.isEmpty()) "—" else pressed.joinToString(" "),
            color = Color(0xFFFFD54F), fontSize = 12.sp, fontFamily = mono,
        )
        info.message?.let { Text(it, color = Color(0xFFFFAB91), fontSize = 11.sp) }
    }
}
