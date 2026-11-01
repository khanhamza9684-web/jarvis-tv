package com.tvpad

/** Button bit positions inside the 16-bit button field of the HID report. */
object Btn {
    const val A = 0
    const val B = 1
    const val X = 2
    const val Y = 3
    const val L1 = 4
    const val R1 = 5
    const val L2 = 6
    const val R2 = 7
    const val SELECT = 8
    const val START = 9
    const val L3 = 10
    const val R3 = 11
    const val MODE = 12

    val names = mapOf(
        A to "A", B to "B", X to "X", Y to "Y",
        L1 to "L1", R1 to "R1", L2 to "L2", R2 to "R2",
        SELECT to "SEL", START to "START", L3 to "L3", R3 to "R3", MODE to "HOME",
    )
}

/** D-pad bit flags (kept separate from buttons; converted to a hat value on send). */
object Dpad {
    const val UP = 1
    const val RIGHT = 2
    const val DOWN = 4
    const val LEFT = 8
}

/** Immutable snapshot of the whole gamepad. Sticks are -1f..1f, triggers 0f..1f. */
data class GamepadState(
    val buttons: Int = 0,
    val dpad: Int = 0,
    val lx: Float = 0f,
    val ly: Float = 0f,
    val rx: Float = 0f,
    val ry: Float = 0f,
    val lt: Float = 0f,
    val rt: Float = 0f,
) {
    fun isPressed(bit: Int) = (buttons shr bit) and 1 == 1

    fun withButton(bit: Int, down: Boolean): GamepadState =
        copy(buttons = if (down) buttons or (1 shl bit) else buttons and (1 shl bit).inv())

    fun withDpad(flag: Int, down: Boolean): GamepadState =
        copy(dpad = if (down) dpad or flag else dpad and flag.inv())

    fun pressedNames(): List<String> =
        Btn.names.filterKeys { isPressed(it) }.values.toList() + dpadNames()

    private fun dpadNames(): List<String> = buildList {
        if (dpad and Dpad.UP != 0) add("↑")
        if (dpad and Dpad.RIGHT != 0) add("→")
        if (dpad and Dpad.DOWN != 0) add("↓")
        if (dpad and Dpad.LEFT != 0) add("←")
    }

    /**
     * 9-byte HID input report (without the report ID):
     *  [0..1] 16 buttons, [2] hat (low nibble), [3] X, [4] Y, [5] Z (right X),
     *  [6] Rz (right Y), [7] Rx (left trigger), [8] Ry (right trigger).
     */
    fun toReport(): ByteArray {
        val r = ByteArray(REPORT_SIZE)
        r[0] = (buttons and 0xFF).toByte()
        r[1] = ((buttons shr 8) and 0xFF).toByte()
        r[2] = hat().toByte()
        r[3] = axis(lx)
        r[4] = axis(ly)
        r[5] = axis(rx)
        r[6] = axis(ry)
        r[7] = trigger(lt)
        r[8] = trigger(rt)
        return r
    }

    /** Hat: 0 = neutral (null state), 1 = N, 2 = NE, 3 = E ... 8 = NW. */
    private fun hat(): Int {
        val up = dpad and Dpad.UP != 0
        val right = dpad and Dpad.RIGHT != 0
        val down = dpad and Dpad.DOWN != 0
        val left = dpad and Dpad.LEFT != 0
        return when {
            up && right -> 2
            right && down -> 4
            down && left -> 6
            left && up -> 8
            up -> 1
            right -> 3
            down -> 5
            left -> 7
            else -> 0
        }
    }

    private fun axis(v: Float): Byte =
        (((v.coerceIn(-1f, 1f) + 1f) / 2f * 255f).toInt()).toByte()

    private fun trigger(v: Float): Byte = ((v.coerceIn(0f, 1f) * 255f).toInt()).toByte()

    companion object {
        const val REPORT_SIZE = 9
    }
}
