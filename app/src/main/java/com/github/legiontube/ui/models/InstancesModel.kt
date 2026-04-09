package com.github.legiontube.ui.models

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.legiontube.api.RetrofitInstance
import com.github.legiontube.api.obj.PipedInstance
import com.github.legiontube.db.DatabaseHolder.Database
import com.github.legiontube.db.obj.CustomInstance
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import java.net.MalformedURLException

class InstancesModel : ViewModel() {
    val customInstances = Database.customInstanceDao().getAllFlow()
        .flowOn(Dispatchers.IO)

    fun addCustomInstance(
        apiUrlInput: String,
        instanceNameInput: String?,
        frontendUrlInput: String?
    ) {
        if (apiUrlInput.isEmpty()) throw IllegalArgumentException()

        val apiUrl = apiUrlInput.toHttpUrlOrNull() ?: throw MalformedURLException()
        val frontendUrl = if (!frontendUrlInput.isNullOrBlank()) {
            frontendUrlInput.toHttpUrlOrNull() ?: throw MalformedURLException()
        } else {
            null
        }

        viewModelScope.launch(Dispatchers.IO) {
            val instanceName = instanceNameInput ?: apiUrl.host

            Database.customInstanceDao()
                .insert(
                    CustomInstance(
                        instanceName,
                        apiUrl.toString(),
                        frontendUrl?.toString().orEmpty()
                    )
                )
        }
    }

    fun deleteCustomInstance(customInstance: CustomInstance) =
        viewModelScope.launch(Dispatchers.IO) {
            Database.customInstanceDao().deleteCustomInstance(customInstance)
        }

    fun fetchAndSyncPublicInstances(onComplete: (Boolean) -> Unit = {}) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                // 1. Add preferred instances first
                val preferred = listOf(
                    CustomInstance("private.coffee", "https://api.piped.private.coffee", "https://private.coffee"),
                    CustomInstance("kavin.rocks (Official)", "https://pipedapi.kavin.rocks", "https://piped.video"),
                    CustomInstance("leptons.xyz", "https://pipedapi.leptons.xyz", "https://piped.leptons.xyz"),
                    CustomInstance("nosebs.ru", "https://pipedapi.nosebs.ru", "https://piped.nosebs.ru"),
                    CustomInstance("adminforge.de", "https://pipedapi.adminforge.de", "https://piped.adminforge.de"),
                    CustomInstance("piped.yt", "https://api.piped.yt", "https://piped.yt"),
                    CustomInstance("drgns.space", "https://pipedapi.drgns.space", "https://piped.drgns.space")
                )
                Database.customInstanceDao().insertAll(preferred)

                // 2. Fetch all from public registry
                val publicInstances = RetrofitInstance.externalApi.getPublicInstances()
                val customInstances = publicInstances
                    .filter { !it.isCurrentlyDown && it.upToDate }
                    .map {
                        CustomInstance(
                            name = it.name,
                            apiUrl = it.apiUrl,
                            frontendUrl = ""
                        )
                    }
                
                Database.customInstanceDao().insertAll(customInstances)
                onComplete(true)
            } catch (e: Exception) {
                onComplete(false)
            }
        }
    }
}