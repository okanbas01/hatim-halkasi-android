package com.example.sharedkhatm

/**
 * Tek bir zikir kartı verisi.
 * DiffUtil için equals/hashCode id + name + target + currentCount + order ile.
 */
data class ZikirItem(
    val id: String,
    var name: String,
    var target: Int,
    var currentCount: Int,
    var order: Int
) {
    fun copyWithCount(newCount: Int) = copy(currentCount = newCount)
    fun copyWithNameTarget(newName: String, newTarget: Int) = copy(name = newName, target = newTarget)
}
