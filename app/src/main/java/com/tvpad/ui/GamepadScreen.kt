package com.tvpad.ui

import android.annotation.SuppressLint
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tvpad.Btn
import com.tvpad.Dpad
import com.tvpad.PadViewModel

private val Panel = Color(0xFF0E1116)
private val Gray = Color(0xFF8A94A6)

@SuppressLint("MissingPermission")
@Composable
fun GamepadScreen(vm: PadViewModel, onMakeDiscoverable: () -> Unit) {
    val state by vm.state.collectAsState()
    val info by vm.gamepad.info.collectAsState()
    var showHud by remember { mutableStateOf(true) }
    var showDevices by remember { mutableStateOf(false) }

    Box(Modifier.fillMaxSize().background(Panel).padding(12.dp)) {

        if (showHud) Hud(state, info, Modifier.align(Alignment.TopCenter))

        // Shoulders / triggers
        Row(Modifier.align(Alignment.TopStart), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            HoldButton("L2", Modifier.size(64.dp, 40.dp), RoundedCornerShape(10.dp), Gray,
                { vm.leftTrigger(true) }, { vm.leftTrigger(false) }, 14.sp)
            HoldButton("L1", Modifier.size(64.dp, 40.dp), RoundedCornerShape(10.dp), Gray,
                { vm.button(Btn.L1, true) }, { vm.button(Btn.L1, false) }, 14.sp)
        }
        Row(Modifier.align(Alignment.TopEnd), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            HoldButton("R1", Modifier.size(64.dp, 40.dp), RoundedCornerShape(10.dp), Gray,
                { vm.button(Btn.R1, true) }, { vm.button(Btn.R1, false) }, 14.sp)
            HoldButton("R2", Modifier.size(64.dp, 40.dp), RoundedCornerShape(10.dp), Gray,
                { vm.rightTrigger(true) }, { vm.rightTrigger(false) }, 14.sp)
        }

        // Left cluster: D-pad above-left, left stick lower-right of it
        DPad(
            Modifier.align(Alignment.TopStart).padding(start = 20.dp, top = 74.dp),
            onChange = { vm.dpadSet(it) },
        )
        Stick(
            diameter = 130.dp, color = Color(0xFF4FC3F7),
            modifier = Modifier.align(Alignment.BottomStart).padding(start = 130.dp, bottom = 8.dp),
            onMove = { x, y -> vm.leftStick(x, y) },
        )

        // Right cluster: face buttons above-right, right stick lower-left of them
        FaceButtons(
            Modifier.align(Alignment.TopEnd).padding(end = 20.dp, top = 70.dp),
            onButton = { bit, down -> vm.button(bit, down) },
        )
        Stick(
            diameter = 130.dp, color = Color(0xFFFFB74D),
            modifier = Modifier.align(Alignment.BottomEnd).padding(end = 130.dp, bottom = 8.dp),
            onMove = { x, y -> vm.rightStick(x, y) },
        )

        // Center: Select / Home / Start
        Row(
            Modifier.align(Alignment.BottomCenter).padding(bottom = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            val pill = RoundedCornerShape(50)
            HoldButton("SELECT", Modifier.size(70.dp, 30.dp), pill, Gray,
                { vm.button(Btn.SELECT, true) }, { vm.button(Btn.SELECT, false) }, 10.sp)
            HoldButton("HOME", Modifier.size(70.dp, 30.dp), pill, Color(0xFF3DDC84),
                { vm.button(Btn.MODE, true) }, { vm.button(Btn.MODE, false) }, 10.sp)
            HoldButton("START", Modifier.size(70.dp, 30.dp), pill, Gray,
                { vm.button(Btn.START, true) }, { vm.button(Btn.START, false) }, 10.sp)
        }

        // Center: connection controls
        Row(Modifier.align(Alignment.Center), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { showDevices = true }) { Text("Connect") }
            TextButton(onClick = { showHud = !showHud }) { Text(if (showHud) "Hide HUD" else "Show HUD") }
        }
    }

    if (showDevices) {
        val devices = remember(showDevices) { vm.gamepad.bondedDevices() }
        AlertDialog(
            onDismissRequest = { showDevices = false },
            title = { Text("Connect to TV") },
            text = {
                Column {
                    Text(
                        "1. Tap \"Make discoverable\".\n" +
                            "2. On Google TV: Settings → Remote & Accessories → Add accessory, pick this phone.\n" +
                            "3. Once paired, choose the TV below.",
                        fontSize = 13.sp,
                    )
                    Column(Modifier.padding(top = 10.dp)) {
                        if (devices.isEmpty()) Text("No paired devices yet.", color = Gray, fontSize = 13.sp)
                        devices.forEach { d ->
                            Text(
                                d.name ?: d.address,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { vm.gamepad.connect(d); showDevices = false }
                                    .padding(vertical = 10.dp),
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = onMakeDiscoverable) { Text("Make discoverable") }
            },
            dismissButton = {
                Row {
                    TextButton(onClick = { vm.gamepad.disconnect(); showDevices = false }) { Text("Disconnect") }
                    TextButton(onClick = { showDevices = false }) { Text("Close") }
                }
            },
        )
    }
}

/** A button that reports down on first touch and up on release; multi-touch safe. */
@Composable
fun HoldButton(
    label: String,
    modifier: Modifier,
    shape: Shape,
    color: Color,
    onDown: () -> Unit,
    onUp: () -> Unit,
    textSize: TextUnit = 18.sp,
) {
    var pressed by remember { mutableStateOf(false) }
    val down by rememberUpdatedState(onDown)
    val up by rememberUpdatedState(onUp)
    val haptic = LocalHapticFeedback.current

    Box(
        modifier = modifier
            .clip(shape)
            .background(if (pressed) color else color.copy(alpha = 0.28f))
            .border(2.dp, color, shape)
            .pointerInput(Unit) {
                awaitEachGesture {
                    awaitFirstDown()
                    pressed = true
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    down()
                    do {
                        val event = awaitPointerEvent()
                    } while (event.changes.any { it.pressed })
                    pressed = false
                    up()
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        Text(label, color = Color.White, fontSize = textSize, fontWeight = FontWeight.Bold)
    }
}

/** Analog stick. Reports x,y in -1..1 (y positive = down, matching HID). */
@Composable
fun Stick(diameter: Dp, color: Color, modifier: Modifier = Modifier, onMove: (Float, Float) -> Unit) {
    var knob by remember { mutableStateOf(Offset.Zero) }
    val move by rememberUpdatedState(onMove)

    Box(
        modifier = modifier
            .size(diameter)
            .clip(CircleShape)
            .background(Color(0x22FFFFFF))
            .border(2.dp, color.copy(alpha = 0.6f), CircleShape)
            .pointerInput(Unit) {
                awaitEachGesture {
                    val first = awaitFirstDown()
                    val r = this.size.width / 2f
                    val c = Offset(r, r)

                    fun emit(p: Offset) {
                        var v = (p - c) / r
                        val len = v.getDistance()
                        if (len > 1f) v /= len
                        knob = v
                        move(v.x, v.y)
                    }

                    emit(first.position)
                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull { it.id == first.id } ?: break
                        if (!change.pressed) break
                        emit(change.position)
                        change.consume()
                    }
                    knob = Offset.Zero
                    move(0f, 0f)
                }
            },
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val r = this.size.width / 2f
            drawCircle(
                color = color.copy(alpha = 0.85f),
                radius = r * 0.4f,
                center = Offset(r + knob.x * r * 0.6f, r + knob.y * r * 0.6f),
            )
        }
    }
}

/** Single-touch D-pad: sliding the thumb around selects directions, corners give diagonals. */
@Composable
fun DPad(modifier: Modifier = Modifier, onChange: (Int) -> Unit) {
    var mask by remember { mutableStateOf(0) }
    val change by rememberUpdatedState(onChange)
    val haptic = LocalHapticFeedback.current

    Box(
        modifier = modifier
            .size(140.dp)
            .pointerInput(Unit) {
                awaitEachGesture {
                    val first = awaitFirstDown()
                    val r = this.size.width / 2f

                    fun update(p: Offset) {
                        val v = (p - Offset(r, r)) / r
                        var m = 0
                        if (v.y < -0.3f) m = m or Dpad.UP
                        if (v.y > 0.3f) m = m or Dpad.DOWN
                        if (v.x < -0.3f) m = m or Dpad.LEFT
                        if (v.x > 0.3f) m = m or Dpad.RIGHT
                        if (m != mask) {
                            if (m != 0) haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            mask = m
                            change(m)
                        }
                    }

                    update(first.position)
                    while (true) {
                        val event = awaitPointerEvent()
                        val ch = event.changes.firstOrNull { it.id == first.id } ?: break
                        if (!ch.pressed) break
                        update(ch.position)
                        ch.consume()
                    }
                    mask = 0
                    change(0)
                }
            },
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val w = this.size.width
            val arm = w / 3f
            val base = Color(0xFF2A2F3A)
            val hot = Color(0xFF4FC3F7)
            val rad = CornerRadius(14f, 14f)

            drawRoundRect(base, Offset(arm, 0f), Size(arm, w), rad)
            drawRoundRect(base, Offset(0f, arm), Size(w, arm), rad)

            fun arrow(active: Boolean, a: Offset, b: Offset, c: Offset) {
                val p = Path().apply { moveTo(a.x, a.y); lineTo(b.x, b.y); lineTo(c.x, c.y); close() }
                drawPath(p, if (active) hot else Gray)
            }
            val s = arm * 0.28f
            val mid = w / 2f
            arrow(mask and Dpad.UP != 0, Offset(mid, arm * 0.25f), Offset(mid - s, arm * 0.25f + s * 1.4f), Offset(mid + s, arm * 0.25f + s * 1.4f))
            arrow(mask and Dpad.DOWN != 0, Offset(mid, w - arm * 0.25f), Offset(mid - s, w - arm * 0.25f - s * 1.4f), Offset(mid + s, w - arm * 0.25f - s * 1.4f))
            arrow(mask and Dpad.LEFT != 0, Offset(arm * 0.25f, mid), Offset(arm * 0.25f + s * 1.4f, mid - s), Offset(arm * 0.25f + s * 1.4f, mid + s))
            arrow(mask and Dpad.RIGHT != 0, Offset(w - arm * 0.25f, mid), Offset(w - arm * 0.25f - s * 1.4f, mid - s), Offset(w - arm * 0.25f - s * 1.4f, mid + s))
        }
    }
}

@Composable
fun FaceButtons(modifier: Modifier = Modifier, onButton: (Int, Boolean) -> Unit) {
    val s = 58.dp
    Box(modifier.size(150.dp)) {
        @Composable
        fun face(label: String, bit: Int, color: Color, align: Alignment) {
            HoldButton(
                label, Modifier.align(align).size(s), CircleShape, color,
                { onButton(bit, true) }, { onButton(bit, false) },
            )
        }
        face("Y", Btn.Y, Color(0xFFFFD54F), Alignment.TopCenter)
        face("A", Btn.A, Color(0xFF66BB6A), Alignment.BottomCenter)
        face("X", Btn.X, Color(0xFF42A5F5), Alignment.CenterStart)
        face("B", Btn.B, Color(0xFFEF5350), Alignment.CenterEnd)
    }
}
