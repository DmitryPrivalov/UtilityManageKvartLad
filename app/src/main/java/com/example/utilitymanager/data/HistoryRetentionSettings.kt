package com.example.utilitymanager.data

import android.content.Context

/** Выбор пользователя действует для всех квартир на этом устройстве. */
object HistoryRetentionSettings {
    private const val PREFS = "utility_manager_history_settings"
    private const val DISABLE_AUTOMATIC_CLEANUP = "disable_automatic_cleanup"

    fun isDisabled(context: Context): Boolean =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getBoolean(DISABLE_AUTOMATIC_CLEANUP, false)

    fun setDisabled(context: Context, disabled: Boolean) {
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(DISABLE_AUTOMATIC_CLEANUP, disabled)
            .apply()
    }
}
