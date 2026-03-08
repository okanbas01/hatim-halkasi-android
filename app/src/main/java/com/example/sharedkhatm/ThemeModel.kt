package com.example.sharedkhatm

data class ThemeModel(
    val imageRes: Int,
    val isTextWhite: Boolean = true, // Yazı rengi beyaz mı olsun?
    val isLocked: Boolean = false   // Reklam izleyerek açılabilir (Cuma/Kandil premium tema)
)