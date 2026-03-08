package com.example.sharedkhatm.ads

import android.content.Context

/**
 * Hangi ekranda banner gösterileceği. Premium'da hiç gösterilmez.
 * DI uyumlu – Context constructor ile.
 */
interface BannerStateController {

    /**
     * Sadece HOME, SPIRITUAL_GRID, HATIM_LIST, SURE_LIST için true.
     * READING, QIBLA, ZIKR ve premium'da false.
     */
    fun shouldShowBanner(screen: Screen): Boolean
}
