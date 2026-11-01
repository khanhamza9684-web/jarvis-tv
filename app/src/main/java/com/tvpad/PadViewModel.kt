package com.tvpad

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.math.abs

class PadViewModel(app: Application) : AndroidViewModel(app) {

    val gamepad = HidGamepad(app)

    private val _state = MutableStateFlow(GamepadState())
    val state: StateFlow<GamepadState> = _state.asStateFlow()

    init {
        // Stream state to the host. StateFlow conflates, so rapid touch events collapse
        // into at most one report every ~8 ms (~125 Hz).
        viewModelScope.launch {
            _state.collect {
                gamepad.send(it)
                delay(8)
            }
        }
        // Push a fresh report as soon as a host connects so it sees a neutral pad.
        viewModelScope.launch {
            gamepad.info.map { it.status }.distinctUntilChanged().collect {
                if (it == LinkStatus.CONNECTED) gamepad.send(_state.value)
            }
        }
    }

    fun button(bit: Int, down: Boolean) = _state.update { it.withButton(bit, down) }

    fun dpadSet(mask: Int) = _state.update { it.copy(dpad = mask) }

    fun leftStick(x: Float, y: Float) = _state.update { it.copy(lx = dead(x), ly = dead(y)) }

    fun rightStick(x: Float, y: Float) = _state.update { it.copy(rx = dead(x), ry = dead(y)) }

    /** Analog trigger + its digital button together, like a real pad. */
    fun leftTrigger(down: Boolean) =
        _state.update { it.withButton(Btn.L2, down).copy(lt = if (down) 1f else 0f) }

    fun rightTrigger(down: Boolean) =
        _state.update { it.withButton(Btn.R2, down).copy(rt = if (down) 1f else 0f) }

    private fun dead(v: Float) = if (abs(v) < DEADZONE) 0f else v

    override fun onCleared() {
        gamepad.stop()
    }

    private companion object {
        const val DEADZONE = 0.08f
    }
}
