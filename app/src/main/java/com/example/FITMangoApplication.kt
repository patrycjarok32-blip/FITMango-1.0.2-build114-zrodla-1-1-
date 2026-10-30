package com.example

import android.app.Application
import androidx.work.Configuration

/** WorkManager can initialize on demand even when Startup providers are not run. */
open class FITMangoApplication : Application(), Configuration.Provider {
    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder().build()
}
