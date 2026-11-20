package com.stock.trainer.data.local

import android.content.Context
import android.content.SharedPreferences
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class VipManager @Inject constructor(
    @ApplicationContext context: Context,
) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("vip_prefs", Context.MODE_PRIVATE)

    private val hasManualVipConfirmation = prefs.getBoolean(KEY_VIP_CONFIRMED, false)
    private val _isVip = MutableStateFlow(
        if (hasManualVipConfirmation) prefs.getBoolean(KEY_VIP, false) else false
    )
    val isVipFlow: StateFlow<Boolean> = _isVip.asStateFlow()

    private val _trainingCount = MutableStateFlow(prefs.getInt(KEY_COUNT, 0))
    val trainingCountFlow: StateFlow<Int> = _trainingCount.asStateFlow()

    var isVip: Boolean
        get() = _isVip.value
        set(value) {
            _isVip.value = value
            prefs.edit()
                .putBoolean(KEY_VIP, value)
                .putBoolean(KEY_VIP_CONFIRMED, true)
                .apply()
        }

    val trainingCount: Int
        get() = _trainingCount.value

    val remainingFree: Int
        get() = maxOf(0, MAX_FREE_TRAININGS - trainingCount)

    fun canTrain(): Boolean = isVip || trainingCount < MAX_FREE_TRAININGS

    fun incrementTrainingCount() {
        val next = trainingCount + 1
        _trainingCount.value = next
        prefs.edit().putInt(KEY_COUNT, next).apply()
    }

    fun resetCount() {
        _trainingCount.value = 0
        prefs.edit().putInt(KEY_COUNT, 0).apply()
    }

    companion object {
        private const val KEY_VIP = "is_vip"
        private const val KEY_VIP_CONFIRMED = "is_vip_confirmed_v2"
        private const val KEY_COUNT = "training_count"
        const val MAX_FREE_TRAININGS = 66
    }
}
