package com.example.sharedkhatm

/**
 * Geçmişe eklenen tek kayıt (Kaydet'e basıldığında).
 * dateYmd: yyyyMMdd (sıralama ve gruplama için).
 */
data class ZikirHistoryRecord(
    val dateYmd: String,
    val zikirId: String,
    val zikirName: String,
    val count: Int
)
