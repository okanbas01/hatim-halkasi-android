package com.example.sharedkhatm

object SurahNames {
    private val turkishNames = listOf(
        "", // 0. indeks boş (Sureler 1'den başlar)
        "Fatiha", "Bakara", "Al-i İmran", "Nisa", "Maide", "En'am", "A'raf", "Enfal", "Tevbe", "Yunus",
        "Hud", "Yusuf", "Ra'd", "İbrahim", "Hicr", "Nahl", "İsra", "Kehf", "Meryem", "Taha",
        "Enbiya", "Hac", "Mü'minun", "Nur", "Furkan", "Şuara", "Neml", "Kasas", "Ankebut", "Rum",
        "Lokman", "Secde", "Ahzab", "Sebe", "Fatır", "Yasin-i Şerif", "Saffat", "Sad", "Zümer", "Mü'min",
        "Fussilet", "Şura", "Zuhruf", "Duhan", "Casiye", "Ahkaf", "Muhammed", "Fetih", "Hucurat", "Kaf",
        "Zariyat", "Tur", "Necm", "Kamer", "Rahman", "Vakıa", "Hadid", "Mücadele", "Haşr", "Mümtehine",
        "Saf", "Cuma", "Münafikun", "Tegabun", "Talak", "Tahrim", "Mülk", "Kalem", "Hakka", "Mearic",
        "Nuh", "Cin", "Müzzemmil", "Müddessir", "Kıyamet", "İnsan", "Mürselat", "Nebe", "Naziat", "Abese",
        "Tekvir", "İnfitar", "Mutaffifin", "İnşikak", "Bürüc", "Tarık", "A'la", "Gaşiye", "Fecr", "Beled",
        "Şems", "Leyl", "Duha", "İnşirah", "Tin", "Alak", "Kadir", "Beyyine", "Zilzal", "Adiyat",
        "Karia", "Tekasür", "Asr", "Hümeze", "Fil", "Kureyş", "Maun", "Kevser", "Kafirun", "Nasr",
        "Tebbet", "İhlas", "Felak", "Nas"
    )

    fun getName(number: Int): String {
        // Liste dışına çıkmamak için kontrol
        return if (number in 1..114) turkishNames[number] else "Sure $number"
    }
}