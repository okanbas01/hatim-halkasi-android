package com.example.sharedkhatm

import com.google.firebase.Timestamp

// Hatim sınıfının güncel hali:
data class Hatim(
    var id: String = "",
    var name: String = "",
    var description: String = "",
    var totalParts: Int = 30,
    var completedParts: Int = 0,
    var participants: ArrayList<String> = ArrayList(),
    var createdBy: String = "",
    // EKLENEN KISIM: Oluşturulma tarihi (Varsayılan null olabilir)
    var createdAt: Timestamp? = null
) {
    // Boş constructor Firebase için gereklidir
    constructor() : this("", "", "", 30, 0, ArrayList(), "", null)
}