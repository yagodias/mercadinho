package br.com.listamercado

import android.app.Application
import org.osmdroid.config.Configuration

class App : Application() {
    override fun onCreate() {
        super.onCreate()
        Notifier.createChannel(this)

        // O servidor de mapas do OpenStreetMap exige identificar o app
        Configuration.getInstance().load(this, getSharedPreferences("osmdroid", MODE_PRIVATE))
        Configuration.getInstance().userAgentValue = packageName
    }
}
