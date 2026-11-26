package com.tanonb.scandoc.data

import android.content.Context
import com.tanonb.scandoc.imaging.Compression
import com.tanonb.scandoc.imaging.PageFilter
import com.tanonb.scandoc.imaging.PageSize
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

data class AppSettings(
    val autoCrop: Boolean = true,
    val defaultFilter: PageFilter = PageFilter.AUTO,
    val compression: Compression = Compression.MEDIUM,
    val pageSize: PageSize = PageSize.AUTO,
    val showBanner: Boolean = true,
    val searchablePdf: Boolean = true,
    val flatten: Boolean = true,
)

class SettingsStore(context: Context) {
    private val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
    private val _state = MutableStateFlow(read())
    val state: StateFlow<AppSettings> = _state
    val current: AppSettings get() = _state.value

    private fun read() = AppSettings(
        autoCrop = prefs.getBoolean("autoCrop", true),
        defaultFilter = PageFilter.of(prefs.getString("defaultFilter", PageFilter.AUTO.name)),
        compression = Compression.of(prefs.getString("compression", null)),
        pageSize = PageSize.of(prefs.getString("pageSize", null)),
        showBanner = prefs.getBoolean("showBanner", true),
        searchablePdf = prefs.getBoolean("searchablePdf", true),
        flatten = prefs.getBoolean("flatten", true),
    ).also { com.tanonb.scandoc.imaging.Bitmaps.flattenEnabled = it.flatten }

    fun update(transform: (AppSettings) -> AppSettings) {
        val s = transform(_state.value)
        prefs.edit()
            .putBoolean("autoCrop", s.autoCrop)
            .putString("defaultFilter", s.defaultFilter.name)
            .putString("compression", s.compression.name)
            .putString("pageSize", s.pageSize.name)
            .putBoolean("showBanner", s.showBanner)
            .putBoolean("searchablePdf", s.searchablePdf)
            .putBoolean("flatten", s.flatten)
            .apply()
        _state.value = s
        com.tanonb.scandoc.imaging.Bitmaps.flattenEnabled = s.flatten
    }
}
