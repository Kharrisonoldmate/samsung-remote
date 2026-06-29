package com.redmi.samsungremote

/**
 * Реалізація ІЧ-протоколу кондиціонерів Samsung (SAMSUNG_AC).
 * Порт класу IRSamsungAc з бібліотеки IRremoteESP8266 (src/ir_Samsung.cpp).
 */
class SamsungAcProtocol {

    companion object {
        const val MIN_TEMP = 16
        const val MAX_TEMP = 30

        const val MODE_AUTO = 0
        const val MODE_COOL = 1
        const val MODE_DRY = 2
        const val MODE_FAN = 3
        const val MODE_HEAT = 4

        const val FAN_AUTO = 0
        const val FAN_LOW = 2
        const val FAN_MED = 4
        const val FAN_HIGH = 5
        const val FAN_AUTO2 = 6
        const val FAN_TURBO = 7

        private const val SWING_V = 0b010
        private const val SWING_H = 0b011
        private const val SWING_BOTH = 0b100
        private const val SWING_OFF = 0b111

        private const val FANSPECIAL_OFF = 0b000
        private const val POWERFUL_ON = 0b011

        private const val FREQ_HZ = 38000
        private const val HDR_MARK = 690
        private const val HDR_SPACE = 17844
        private const val SECTION_MARK = 3086
        private const val SECTION_SPACE = 8864
        private const val SECTION_GAP = 2886
        private const val BIT_MARK = 586
        private const val ONE_SPACE = 1432
        private const val ZERO_SPACE = 436

        private const val SECTION_LEN = 7

        private val RESET = intArrayOf(
            0x02, 0x92, 0x0F, 0x00, 0x00, 0x00, 0xF0,
            0x01, 0x02, 0xAE, 0x71, 0x00, 0x15, 0xF0
        )
        private val EXT_MIDDLE = intArrayOf(0x01, 0xD2, 0x0F, 0x00, 0x00, 0x00, 0x00)

        private val POWER_ON_FRAME = intArrayOf(
            0x02, 0x92, 0x0F, 0x00, 0x00, 0x00, 0xF0,
            0x01, 0xD2, 0x0F, 0x00, 0x00, 0x00, 0x00,
            0x01, 0xE2, 0xFE, 0x71, 0x80, 0x11, 0xF0
        )
        private val POWER_OFF_FRAME = intArrayOf(
            0x02, 0xB2, 0x0F, 0x00, 0x00, 0x00, 0xC0,
            0x01, 0xD2, 0x0F, 0x00, 0x00, 0x00, 0x00,
            0x01, 0x02, 0xFF, 0x71, 0x80, 0x11, 0xC0
        )
    }

    private val base = ByteArray(14)

    private var lastSentPower = true
    private var forceExtended = true

    var onTimerMins = 0
        private set
    var offTimerMins = 0
        private set
    var sleep = false
        private set

    init {
        reset()
    }

    private fun b(i: Int): Int = base[i].toInt() and 0xFF
    private fun setB(i: Int, v: Int) { base[i] = (v and 0xFF).toByte() }

    private fun setBit(arr: ByteArray, idx: Int, bit: Int, on: Boolean) {
        val v = arr[idx].toInt() and 0xFF
        arr[idx] = (if (on) v or (1 shl bit) else v and (1 shl bit).inv()).toByte()
    }

    fun reset() {
        for (i in 0..13) base[i] = RESET[i].toByte()
        lastSentPower = true
        forceExtended = true
        onTimerMins = 0
        offTimerMins = 0
        sleep = false
        setPower(true)
    }

    fun getPower(): Boolean {
        val p1 = (b(6) shr 4) and 0b11
        val p2 = (b(13) shr 4) and 0b11
        return p1 == 0b11 && p2 == 0b11
    }

    fun setPower(on: Boolean) {
        val v = if (on) 0b11 else 0b00
        setB(6, (b(6) and 0xCF) or (v shl 4))
        setB(13, (b(13) and 0xCF) or (v shl 4))
    }

    fun togglePower() = setPower(!getPower())

    fun getTemp(): Int = ((b(11) shr 4) and 0x0F) + MIN_TEMP

    fun setTemp(temp: Int) {
        val t = temp.coerceIn(MIN_TEMP, MAX_TEMP)
        setB(11, (b(11) and 0x0F) or (((t - MIN_TEMP) and 0x0F) shl 4))
    }

    fun getMode(): Int = (b(12) shr 4) and 0b111

    fun setMode(mode: Int) {
        val m = if (mode > MODE_HEAT) MODE_AUTO else mode
        setB(12, (b(12) and 0b10001111) or (m shl 4))
        if (m == MODE_AUTO) {
            setFanRaw(FAN_AUTO2)
        } else if (getFan() == FAN_AUTO2) {
            setFanRaw(FAN_AUTO)
        }
    }

    fun getFan(): Int = (b(12) shr 1) and 0b111

    private fun setFanRaw(speed: Int) {
        setB(12, (b(12) and 0b11110001) or ((speed and 0b111) shl 1))
    }

    fun setFan(speed: Int) {
        when (speed) {
            FAN_AUTO, FAN_LOW, FAN_MED, FAN_HIGH, FAN_TURBO ->
                if (getMode() == MODE_AUTO) return
            FAN_AUTO2 ->
                if (getMode() != MODE_AUTO) return
            else -> return
        }
        setFanRaw(speed)
    }

    fun getSwing(): Boolean {
        val s = (b(9) shr 4) and 0b111
        return s == SWING_V || s == SWING_BOTH
    }

    fun setSwing(on: Boolean) {
        val cur = (b(9) shr 4) and 0b111
        val ns = when (cur) {
            SWING_BOTH, SWING_H -> if (on) SWING_BOTH else SWING_H
            else -> if (on) SWING_V else SWING_OFF
        }
        setB(9, (b(9) and 0b10001111) or (ns shl 4))
    }

    fun toggleSwing() = setSwing(!getSwing())

    fun getQuiet(): Boolean = ((b(5) shr 5) and 1) == 1

    fun setQuiet(on: Boolean) {
        setBit(base, 5, 5, on)
        if (on) {
            setFanRaw(FAN_AUTO)
            setPowerful(false)
        }
    }

    fun toggleQuiet() = setQuiet(!getQuiet())

    fun getPowerful(): Boolean {
        val fs = (b(10) shr 1) and 0b111
        return fs == POWERFUL_ON && getFan() == FAN_TURBO
    }

    fun setPowerful(on: Boolean) {
        val fs = if (on) POWERFUL_ON else FANSPECIAL_OFF
        setB(10, (b(10) and 0b11110001) or (fs shl 1))
        if (on) {
            setFanRaw(FAN_TURBO)
            setQuiet(false)
        }
    }

    fun togglePowerful() = setPowerful(!getPowerful())

    fun getDisplay(): Boolean = ((b(10) shr 4) and 1) == 1

    fun setDisplay(on: Boolean) = setBit(base, 10, 4, on)

    fun toggleDisplay() = setDisplay(!getDisplay())

    fun setOnTimer(mins: Int) {
        onTimerMins = (mins.coerceIn(0, 24 * 60) / 10) * 10
        if (onTimerMins > 0) sleep = false
    }

    fun setOffTimer(mins: Int) {
        offTimerMins = (mins.coerceIn(0, 24 * 60) / 10) * 10
        if (offTimerMins > 0) sleep = false
    }

    fun setSleep(mins: Int) {
        offTimerMins = (mins.coerceIn(0, 24 * 60) / 10) * 10
        if (offTimerMins > 0) onTimerMins = 0
        sleep = offTimerMins > 0
    }

    fun clearTimers() {
        onTimerMins = 0
        offTimerMins = 0
        sleep = false
    }

    private fun countBits(value: Int): Int = Integer.bitCount(value and 0xFF)

    private fun calcSectionChecksum(s: ByteArray, off: Int): Int {
        var sum = 0
        sum += countBits(s[off].toInt() and 0xFF)
        sum += countBits(s[off + 1].toInt() and 0x0F)
        sum += countBits((s[off + 2].toInt() shr 4) and 0x0F)
        for (i in 3..6) sum += countBits(s[off + i].toInt() and 0xFF)
        return sum xor 0xFF
    }

    private fun applyChecksum(s: ByteArray, sections: Int) {
        for (sec in 0 until sections) {
            val off = sec * SECTION_LEN
            val sum = calcSectionChecksum(s, off)
            val upper = (sum shr 4) and 0x0F
            val lower = sum and 0x0F
            s[off + 2] = ((s[off + 2].toInt() and 0xF0) or upper).toByte()
            s[off + 1] = ((s[off + 1].toInt() and 0x0F) or (lower shl 4)).toByte()
        }
    }

    private fun buildExtendedState(): ByteArray {
        val ext = ByteArray(21)
        for (i in 0..6) ext[i] = base[i]
        for (i in 0..6) ext[7 + i] = EXT_MIDDLE[i].toByte()
        for (i in 0..6) ext[14 + i] = base[7 + i]

        val onEnable = onTimerMins > 0
        val onDay = onTimerMins >= 24 * 60
        var onMins = 0; var onH1 = 0; var onH2 = 0
        if (!onDay) {
            onMins = (onTimerMins % 60) / 10
            val hours = onTimerMins / 60
            onH1 = hours and 1
            onH2 = hours shr 1
        }
        val offEnable = offTimerMins > 0
        val offDay = offTimerMins >= 24 * 60
        var offMins = 0; var offH1 = 0; var offH2 = 0
        if (!offDay) {
            offMins = (offTimerMins % 60) / 10
            val hours = offTimerMins / 60
            offH1 = hours and 1
            offH2 = hours shr 1
        }

        ext[9] = ((ext[9].toInt() and 0x0F) or (offMins shl 4) or (offH1 shl 7)).toByte()
        ext[10] = ((offH2 and 0x0F) or (onMins shl 4) or (onH1 shl 7)).toByte()
        ext[11] = (onH2 and 0x0F).toByte()
        var by12 = 0
        if (offDay) by12 = by12 or (1 shl 0)
        if (onEnable) by12 = by12 or (1 shl 1)
        if (offEnable) by12 = by12 or (1 shl 2)
        val sleepActive = sleep && offEnable
        if (sleepActive) by12 = by12 or (1 shl 3)
        if (onDay) by12 = by12 or (1 shl 4)
        ext[12] = by12.toByte()

        setBit(ext, 5, 4, sleepActive)

        applyChecksum(ext, 3)
        return ext
    }

    private fun buildNormalState(): ByteArray {
        val s = base.copyOf(14)
        applyChecksum(s, 2)
        return s
    }

    private fun waveform(state: ByteArray): IntArray {
        val p = ArrayList<Int>(400)
        p.add(HDR_MARK)
        p.add(HDR_SPACE)
        var off = 0
        while (off < state.size) {
            p.add(SECTION_MARK)
            p.add(SECTION_SPACE)
            for (i in 0 until SECTION_LEN) {
                val byteVal = state[off + i].toInt() and 0xFF
                for (bit in 0..7) {
                    p.add(BIT_MARK)
                    p.add(if ((byteVal shr bit) and 1 == 1) ONE_SPACE else ZERO_SPACE)
                }
            }
            p.add(BIT_MARK)
            p.add(SECTION_GAP)
            off += SECTION_LEN
        }
        if (p.isNotEmpty() && p.size % 2 == 0) p.removeAt(p.size - 1)
        return p.toIntArray()
    }

    private fun bytesOf(a: IntArray): ByteArray {
        val b = ByteArray(a.size)
        for (i in a.indices) b[i] = a[i].toByte()
        return b
    }

    fun powerOnPattern(): IntArray {
        setPower(true)
        lastSentPower = true
        forceExtended = false
        return waveform(bytesOf(POWER_ON_FRAME))
    }

    fun powerOffPattern(): IntArray {
        setPower(false)
        lastSentPower = false
        forceExtended = false
        return waveform(bytesOf(POWER_OFF_FRAME))
    }

    private fun hexToBytes(hex: String): ByteArray {
        val clean = hex.filter { !it.isWhitespace() }
        val n = clean.length / 2
        val out = ByteArray(n)
        for (i in 0 until n) out[i] = clean.substring(i * 2, i * 2 + 2).toInt(16).toByte()
        return out
    }

    /** Hex-команда → сигнал «справжньою» рамкою Samsung (заголовки секцій). */
    fun patternFromHex(hex: String): IntArray {
        val bytes = hexToBytes(hex)
        val out = ArrayList<Int>()
        var i = 0
        while (i < bytes.size) {
            val end = if (i + 14 <= bytes.size) i + 14 else bytes.size
            val wf = waveform(bytes.copyOfRange(i, end))
            if (out.isNotEmpty()) out.add(13000)
            for (v in wf) out.add(v)
            i = end
        }
        if (out.isNotEmpty() && out.size % 2 == 0) out.removeAt(out.size - 1)
        return out.toIntArray()
    }

    /** Hex-команда → сигнал «простою» рамкою (690+1790, без секцій). */
    fun patternFromHexSimple(hex: String): IntArray {
        val bytes = hexToBytes(hex)
        val out = ArrayList<Int>()
        var i = 0
        while (i < bytes.size) {
            val end = if (i + 14 <= bytes.size) i + 14 else bytes.size
            if (out.isNotEmpty()) out.add(3000)
            out.add(690); out.add(1790)
            for (b in i until end) {
                val byteVal = bytes[b].toInt() and 0xFF
                for (bit in 0..7) {
                    out.add(585)
                    out.add(if ((byteVal shr bit) and 1 == 1) 1430 else 440)
                }
            }
            i = end
        }
        if (out.isNotEmpty() && out.size % 2 == 0) out.removeAt(out.size - 1)
        return out.toIntArray()
    }

    val frequencyHz: Int get() = FREQ_HZ

    fun nextPattern(): IntArray {
        val powerChanged = getPower() != lastSentPower
        val needExtended = powerChanged || forceExtended ||
            onTimerMins > 0 || offTimerMins > 0 || sleep
        val state = if (needExtended) buildExtendedState() else buildNormalState()
        lastSentPower = getPower()
        forceExtended = false
        return waveform(state)
    }

    fun debugHex(): String {
        val s = buildNormalState()
        return s.joinToString(" ") { String.format("%02X", it.toInt() and 0xFF) }
    }
}
