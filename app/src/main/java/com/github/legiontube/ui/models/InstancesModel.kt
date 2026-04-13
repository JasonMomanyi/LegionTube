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
                    CustomInstance("drgns.space", "https://pipedapi.drgns.space", "https://piped.drgns.space"),
                    CustomInstance("kavin.rocks libre (Official)", "https://pipedapi-libre.kavin.rocks", "https://piped-libre.kavin.rocks"),
                    CustomInstance("privacy.com.de", "https://piped-api.privacy.com.de", "https://piped.privacy.com.de"),
                    CustomInstance("owo.si", "https://pipedapi.owo.si", "https://piped.owo.si"),
                    CustomInstance("ducks.party", "https://pipedapi.ducks.party", "https://piped.ducks.party"),
                    CustomInstance("codespace.cz", "https://piped-api.codespace.cz", "https://piped.codespace.cz"),
                    CustomInstance("reallyaweso.me", "https://pipedapi.reallyaweso.me", "https://piped.reallyaweso.me"),
                    CustomInstance("darkness.services", "https://pipedapi.darkness.services", "https://piped.darkness.services"),
                    CustomInstance("orangenet.cc", "https://pipedapi.orangenet.cc", "https://piped.orangenet.cc")
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