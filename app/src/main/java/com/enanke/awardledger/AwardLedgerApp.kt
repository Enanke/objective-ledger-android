package com.enanke.awardledger

import android.app.Application
import com.enanke.awardledger.core.AppContainer

class AwardLedgerApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        container.connectivity.start()
    }
}
