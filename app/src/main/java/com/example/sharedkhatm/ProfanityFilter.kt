package com.example.sharedkhatm

import java.util.Locale

object ProfanityFilter {

    // Yaygın küfür ve argo kökleri
    // Bu liste, kelime köklerini içerdiği için türevlerini de yakalar.
    private val blackList = listOf(
        "amk", "aq", "sik", "siktir", "sokayım", "sokayım",
        "oç", "piç", "yavşak", "kavat", "ibne", "göt", "kalta",
        "fahişe", "kahpe", "oros", "yarra", "yaraa", "amcık",
        "yarrak", "taşşak", "pezevenk", "gavat", "sürtük",
        "sikiş", "sikim", "sikem", "skm", "mq", "aq",
        "ananı", "bacını", "avradını", "sülaleni",
        "mal", "gerizekalı", "salak", "aptal", "ahmak", "dangalak", // Hafif hakaretler
        "hıyar", "öküz", "denyo", "zibidi", "züppe"
    )

    fun containsBadWords(text: String): Boolean {
        if (text.isBlank()) return false

        // Türkçe karakter duyarlılığı için TR locale kullanıyoruz
        val lowerCaseText = text.lowercase(Locale("tr", "TR"))

        for (badWord in blackList) {
            // Kelime metnin içinde geçiyor mu?
            // "contains" kullanmak katıdır ama güvenlidir.
            // Örneğin "sıkıntı" kelimesindeki "sık" köküyle "sik" karışmaz (i ve ı farkı).
            // Ancak "asik" (aşık - ingilizce karakter sorunu) gibi durumlara dikkat etmek gerekir.

            // Daha gelişmiş bir kontrol için kelimenin sınırlarına bakabiliriz (Regex ile)
            // Ancak şimdilik basit contain mantığı çoğu durumu çözer.

            if (lowerCaseText.contains(badWord)) {
                return true
            }
        }
        return false
    }
}