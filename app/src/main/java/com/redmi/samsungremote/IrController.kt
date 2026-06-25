package com.redmi.samsungremote

import android.content.Context
import android.hardware.ConsumerIrManager

class IrController(context: Context) {

    private val irManager: ConsumerIrManager? =
        context.getSystemService(Context.CONSUMER_IR_SERVICE) as? ConsumerIrManager

    val serviceAvailable: Boolean get() = irManager != null

    fun hasEmitter(): Boolean = irManager?.hasIrEmitter() == true

    fun status(): String = when {
        irManager == null -> "ІЧ-сервіс недоступний (null) — стандартна передача неможлива"
        irManager.hasIrEmitter() -> "ІЧ-передавач: OK"
        else -> "Система каже «передавача нема» — пробую передавати все одно"
    }

    fun transmit(frequencyHz: Int, pattern: IntArray): Boolean {
        val mgr = irManager ?: return false
        return try {
            mgr.transmit(frequencyHz, pattern)
            true
        } catch (e: Exception) {
            false
        }
    }
}
