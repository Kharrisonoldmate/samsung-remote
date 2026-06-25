package com.redmi.samsungremote

import android.content.Context
import android.hardware.ConsumerIrManager

class IrController(context: Context) {

    private val irManager: ConsumerIrManager? =
        context.getSystemService(Context.CONSUMER_IR_SERVICE) as? ConsumerIrManager

    val serviceAvailable: Boolean get() = irManager != null

    var lastError: String? = null
        private set

    fun hasEmitter(): Boolean = irManager?.hasIrEmitter() == true

    fun status(): String = when {
        irManager == null -> "ІЧ-сервіс недоступний (null)"
        irManager.hasIrEmitter() -> "ІЧ-передавач: OK"
        else -> "Система каже «передавача нема» — пробую все одно"
    }

    fun carrierRangesText(): String {
        val ranges = try { irManager?.carrierFrequencies } catch (e: Throwable) { null }
            ?: return "частоти: невідомо"
        return "частоти: " + ranges.joinToString(", ") { "${it.minFrequency}-${it.maxFrequency}" }
    }

    fun transmit(frequencyHz: Int, pattern: IntArray): Boolean {
        val mgr = irManager
        if (mgr == null) {
            lastError = "ІЧ-сервіс недоступний"
            return false
        }
        return try {
            mgr.transmit(frequencyHz, pattern)
            lastError = null
            true
        } catch (e: Throwable) {
            lastError = "${e.javaClass.simpleName}: ${e.message ?: "без тексту"}"
            false
        }
    }
}
