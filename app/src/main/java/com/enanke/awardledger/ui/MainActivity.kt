package com.enanke.awardledger.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.enanke.awardledger.AwardLedgerApp
import com.enanke.awardledger.ui.theme.AwardLedgerTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val container = (application as AwardLedgerApp).container
        setContent {
            AwardLedgerTheme {
                AwardLedgerRoot(container)
            }
        }
    }
}
