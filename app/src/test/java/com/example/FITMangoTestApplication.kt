package com.example

import androidx.work.Configuration
import androidx.work.testing.SynchronousExecutor
import androidx.work.testing.WorkManagerTestInitHelper

/** UI tests enqueue real work but do not consume live photo/GPU quotas. */
class FITMangoTestApplication : FITMangoApplication() {
    override fun onCreate() {
        super.onCreate()
        WorkManagerTestInitHelper.initializeTestWorkManager(this,
            Configuration.Builder().setExecutor(SynchronousExecutor()).setTaskExecutor(SynchronousExecutor()).build())
        // Network constraints stay unmet until a test explicitly uses TestDriver.
    }
}
