package com.example.data.outbox

import android.content.Context
import com.example.data.local.FeldbuchSettings
import com.example.data.local.OutboxEntity
import com.example.data.local.OutboxKind
import com.example.data.local.SettingsDataStore
import com.example.data.network.EuleApi
import com.example.data.network.EuleApiResult
import com.example.data.network.EuleCitationCodec
import com.example.data.network.FakeEuleApi
import com.example.data.network.OkHttpEuleApi
import kotlinx.coroutines.flow.first

object EuleApiFactory {
  fun create(settings: FeldbuchSettings): EuleApi? =
    when {
      settings.euleUrl.isNotBlank() ->
        OkHttpEuleApi(
          baseUrl = settings.euleUrl,
          apiKey = settings.euleKey,
          enforceHttps = true,
        )
      settings.euleDemoMode -> FakeEuleApi()
      else -> null
    }
}

class EuleOutboxSender(
  private val apiResolver: suspend () -> EuleApi?,
) : OutboxSender {

  constructor(api: EuleApi) : this({ api })

  override suspend fun send(item: OutboxEntity): OutboxSendResult {
    val api =
      apiResolver()
        ?: return OutboxSendResult.Failed(
          "Eule URL not configured (set HTTPS URL or enable Demo mode in Setup)"
        )

    return when (OutboxKind.fromWire(item.kind)) {
      OutboxKind.NOTE,
      OutboxKind.IDEA -> {
        when (
          val res =
            api.suggestMemory(
              text = item.body,
              from = "feldbuch",
              idempotencyKey = item.id,
            )
        ) {
          is EuleApiResult.Success -> OutboxSendResult.Sent
          is EuleApiResult.Failure ->
            OutboxSendResult.Failed("${res.shortReason}: ${res.detailMessage}")
        }
      }
      OutboxKind.QUESTION -> {
        when (
          val res =
            api.ask(
              question = item.body,
              idempotencyKey = item.id,
            )
        ) {
          is EuleApiResult.Success ->
            OutboxSendResult.SentWithAnswer(
              answerText = res.data.answer,
              citationsJson = EuleCitationCodec.encode(res.data.citations),
            )
          is EuleApiResult.Failure ->
            OutboxSendResult.Failed("${res.shortReason}: ${res.detailMessage}")
        }
      }
    }
  }

  companion object {
    fun fromContext(context: Context): EuleOutboxSender {
      val settingsStore = SettingsDataStore(context.applicationContext)
      return EuleOutboxSender {
        val settings = settingsStore.settingsFlow.first()
        EuleApiFactory.create(settings)
      }
    }
  }
}
