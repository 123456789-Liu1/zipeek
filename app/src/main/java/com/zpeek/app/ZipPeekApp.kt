package com.zpeek.app

import android.app.Application
import com.zpeek.app.core.ArchiveSource

class ZipPeekApp : Application() {
    override fun onCreate() {
        super.onCreate()
        ArchiveSource.cleanCache(this)
    }
}
