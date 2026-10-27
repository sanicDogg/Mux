package com.example.mux

import android.app.Application
import com.example.mux.data.forwarder.DataForwarder
import com.example.mux.data.forwarder.KtorDataForwarder
import com.example.mux.data.repository.NotificationLogRepository
import com.example.mux.data.repository.SettingsRepository
import com.example.mux.data.repository.SettingsRepositoryImpl
import com.example.mux.process.manager.BinaryManager
import com.example.mux.process.manager.NativeProcessManager
import com.example.mux.process.nginx.NginxConfigManager
import com.example.mux.process.nginx.NginxProcessController
import com.example.mux.process.repository.DefaultProcessLogRepository
import com.example.mux.process.repository.ProcessLogRepository
import com.example.mux.process.tuna.TunaProcessController
import com.example.mux.service.AppNotificationListenerService

class MuxApplication : Application() {

    val processLogRepository: ProcessLogRepository by lazy {
        DefaultProcessLogRepository()
    }

    val nativeProcessManager: NativeProcessManager by lazy {
        NativeProcessManager(processLogRepository, this)
    }

    val binaryManager: BinaryManager by lazy {
        BinaryManager(this)
    }

    val nginxConfigManager: NginxConfigManager by lazy {
        NginxConfigManager(filesDir)
    }

    val nginxProcessController: NginxProcessController by lazy {
        NginxProcessController(nativeProcessManager, binaryManager, nginxConfigManager)
    }

    val tunaProcessController: TunaProcessController by lazy {
        TunaProcessController(nativeProcessManager, binaryManager, processLogRepository)
    }

    val settingsRepository: SettingsRepository by lazy {
        SettingsRepositoryImpl(this)
    }

    val notificationLogRepository: NotificationLogRepository by lazy {
        AppNotificationListenerService.logRepository
    }

    val dataForwarder: DataForwarder by lazy {
        KtorDataForwarder(settingsRepository)
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
    }

    companion object {
        lateinit var instance: MuxApplication
            private set
    }
}
