package com.redmi.samsungremote

import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {

    private val ac = SamsungAcProtocol()
    private lateinit var ir: IrController

    private lateinit var tvStatus: TextView
    private lateinit var tvTemp: TextView
    private lateinit var tvMode: TextView
    private lateinit var tvFan: TextView
    private lateinit var tvFlags: TextView
    private lateinit var tvTimers: TextView

    private val modeCycle = intArrayOf(
        SamsungAcProtocol.MODE_COOL,
        SamsungAcProtocol.MODE_DRY,
        SamsungAcProtocol.MODE_FAN,
        SamsungAcProtocol.MODE_HEAT,
        SamsungAcProtocol.MODE_AUTO
    )
    private val fanCycle = intArrayOf(
        SamsungAcProtocol.FAN_AUTO,
        SamsungAcProtocol.FAN_LOW,
        SamsungAcProtocol.FAN_MED,
        SamsungAcProtocol.FAN_HIGH,
        SamsungAcProtocol.FAN_TURBO
    )
    private val timerCycleMins = intArrayOf(0, 60, 120, 180, 360, 540, 720)

    private var irWarned = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        ir = IrController(this)

        tvStatus = findViewById(R.id.tvStatus)
        tvTemp = findViewById(R.id.tvTemp)
        tvMode = findViewById(R.id.tvMode)
        tvFan = findViewById(R.id.tvFan)
        tvFlags = findViewById(R.id.tvFlags)
        tvTimers = findViewById(R.id.tvTimers)

        findViewById<Button>(R.id.btnPower).setOnClickListener { ac.togglePower(); send() }
        findViewById<Button>(R.id.btnTempUp).setOnClickListener { ac.setTemp(ac.getTemp() + 1); send() }
        findViewById<Button>(R.id.btnTempDown).setOnClickListener { ac.setTemp(ac.getTemp() - 1); send() }
        findViewById<Button>(R.id.btnMode).setOnClickListener { cycleMode(); send() }
        findViewById<Button>(R.id.btnFan).setOnClickListener { cycleFan(); send() }
        findViewById<Button>(R.id.btnSwing).setOnClickListener { ac.toggleSwing(); send() }
        findViewById<Button>(R.id.btnTurbo).setOnClickListener { ac.togglePowerful(); send() }
        findViewById<Button>(R.id.btnQuiet).setOnClickListener { ac.toggleQuiet(); send() }
        findViewById<Button>(R.id.btnLight).setOnClickListener { ac.toggleDisplay(); send() }
        findViewById<Button>(R.id.btnTimerOn).setOnClickListener { cycleTimerOn(); send() }
        findViewById<Button>(R.id.btnTimerOff).setOnClickListener { cycleTimerOff(); send() }
        findViewById<Button>(R.id.btnSleep).setOnClickListener { toggleSleep(); send() }
        findViewById<Button>(R.id.btnReset).setOnClickListener { ac.reset(); send() }

        Toast.makeText(this, ir.status() + " · " + ir.carrierRangesText(), Toast.LENGTH_LONG).show()
        updateDisplay()
    }

    private fun cycleMode() {
        val cur = ac.getMode()
        val idx = modeCycle.indexOf(cur).let { if (it < 0) 0 else it }
        ac.setMode(modeCycle[(idx + 1) % modeCycle.size])
    }

    private fun cycleFan() {
        if (ac.getMode() == SamsungAcProtocol.MODE_AUTO) {
            Toast.makeText(this, "Швидкість вентилятора недоступна в режимі АВТО", Toast.LENGTH_SHORT).show()
            return
        }
        val cur = ac.getFan()
        val idx = fanCycle.indexOf(cur).let { if (it < 0) 0 else it }
        ac.setFan(fanCycle[(idx + 1) % fanCycle.size])
    }

    private fun cycleTimerOn() {
        val cur = ac.onTimerMins
        val idx = timerCycleMins.indexOf(cur).let { if (it < 0) 0 else it }
        ac.setOnTimer(timerCycleMins[(idx + 1) % timerCycleMins.size])
    }

    private fun cycleTimerOff() {
        val cur = if (ac.sleep) 0 else ac.offTimerMins
        val idx = timerCycleMins.indexOf(cur).let { if (it < 0) 0 else it }
        ac.setOffTimer(timerCycleMins[(idx + 1) % timerCycleMins.size])
    }

    private fun toggleSleep() {
        if (ac.sleep) ac.setSleep(0) else ac.setSleep(8 * 60)
    }

    private fun send() {
        val pattern = ac.nextPattern()
        updateDisplay()
        Thread {
            var ok = false
            for (i in 0 until 3) {
                if (ir.transmit(ac.frequencyHz, pattern)) ok = true
                try { Thread.sleep(40) } catch (_: InterruptedException) {}
            }
            if (!ok) {
                val msg = "ІЧ не передано: ${ir.lastError ?: "невідома причина"}"
                runOnUiThread { Toast.makeText(this, msg, Toast.LENGTH_LONG).show() }
            }
        }.start()
    }

    private fun modeName(m: Int) = when (m) {
        SamsungAcProtocol.MODE_COOL -> "ОХОЛОДЖ."
        SamsungAcProtocol.MODE_DRY -> "ОСУШЕННЯ"
        SamsungAcProtocol.MODE_FAN -> "ВЕНТ."
        SamsungAcProtocol.MODE_HEAT -> "ОБІГРІВ"
        else -> "АВТО"
    }

    private fun fanName(f: Int) = when (f) {
        SamsungAcProtocol.FAN_LOW -> "Низька"
        SamsungAcProtocol.FAN_MED -> "Середня"
        SamsungAcProtocol.FAN_HIGH -> "Висока"
        SamsungAcProtocol.FAN_TURBO -> "Турбо"
        else -> "Авто"
    }

    private fun hoursLabel(mins: Int): String {
        if (mins <= 0) return "0"
        val h = mins / 60
        val m = mins % 60
        return if (m == 0) "${h}год" else "${h}год${m}хв"
    }

    private fun updateDisplay() {
        val on = ac.getPower()
        tvStatus.text = if (on) "● УВІМКНЕНО" else "○ ВИМКНЕНО"

        tvTemp.text = "${ac.getTemp()}°"
        tvMode.text = modeName(ac.getMode())
        tvFan.text = "Вент: ${fanName(ac.getFan())}"

        val flags = ArrayList<String>()
        if (ac.getSwing()) flags.add("⇅ Обдув")
        if (ac.getPowerful()) flags.add("⚡ Турбо")
        if (ac.getQuiet()) flags.add("🔇 Тихо")
        if (ac.getDisplay()) flags.add("💡 Світло")
        if (ac.sleep) flags.add("☾ Сон")
        tvFlags.text = flags.joinToString("   ")

        val timers = ArrayList<String>()
        if (ac.onTimerMins > 0) timers.add("ON: ${hoursLabel(ac.onTimerMins)}")
        if (ac.offTimerMins > 0 && !ac.sleep) timers.add("OFF: ${hoursLabel(ac.offTimerMins)}")
        if (ac.sleep) timers.add("Сон: ${hoursLabel(ac.offTimerMins)}")
        tvTimers.text = timers.joinToString("    ")

        val alpha = if (on) 1f else 0.45f
        tvTemp.alpha = alpha
        tvMode.alpha = alpha
        tvFan.alpha = alpha
    }
}
