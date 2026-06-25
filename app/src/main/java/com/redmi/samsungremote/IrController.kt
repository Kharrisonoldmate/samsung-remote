package com.redmi.samsungremote

import android.content.Context
import android.hardware.ConsumerIrManager

/**
 * Обгортка над апаратним ІЧ-передавачем телефона (ConsumerIrManager).
 */
class IrController(context: Context) {

    private val irManager: ConsumerIrManager? =
        context.getSystemService(Context.CONSUMER_IR_SERVICE) as? ConsumerIrManager

    /** Чи є на телефоні ІЧ-передавач і чи він доступний. */
    fun hasEmitter(): Boolean = irManager?.hasIrEmitter() == true

    /**
     * Передати ІЧ-патерн.
     * @param frequencyHz несуча частота (38000 для Samsung)
     * @param pattern масив тривалостей on/off у мікросекундах (починається з ON)
     * @return true, якщо передача відбулась
     */
    fun transmit(frequencyHz: Int, pattern: IntArray): Boolean {
        val mgr = irManager ?: return false
        if (!mgr.hasIrEmitter()) return false
        return try {
            mgr.transmit(frequencyHz, pattern)
            true
        } catch (e: Exception) {
            false
        }
    }
}
