package com.dejaroad.app

import ai.pleos.playground.tts.TextToSpeech
import ai.pleos.playground.tts.constant.ErrorType
import ai.pleos.playground.tts.constant.Mode
import ai.pleos.playground.tts.listener.EventListener
import ai.pleos.playground.tts.listener.OnInitializeListener
import android.content.Context
import android.media.AudioAttributes
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.util.Locale

/**
 * Pleos TextToSpeech SDK로 짧은 음성 안내를 낸다.
 *
 * 차량 안 엔진(ON_DEVICE)만 써서 네트워크 연결이나 앱 등록 없이 동작한다.
 * 초기화가 끝나기 전에 들어온 요청은 마지막 한 건만 기억했다가 준비되면 말한다.
 */
class VoiceGuide(context: Context) {

  private val tts = TextToSpeech(context.applicationContext, Mode.ON_DEVICE)

  private val main = Handler(Looper.getMainLooper())

  @Volatile private var ready = false
  @Volatile private var speaking = false

  /** 화면에 '읽어 드리는 중'을 보여 주기 위한 상태 (메인 스레드) */
  var talking by mutableStateOf(false)
    private set
  @Volatile private var pending: Pair<String, (() -> Unit)?>? = null

  /** 지금 문장을 끝까지 말한 뒤 할 일 (중간에 끊기면 하지 않는다) */
  @Volatile private var afterSpeech: (() -> Unit)? = null

  fun start() {
    tts.initialize(
      object : OnInitializeListener {
        override fun onSuccess() {
          Log.i(TAG, "TTS 초기화 완료")
          tts.addEventListener(events)
          tts.setLocale(Locale.KOREA)
          // 내비 안내 음성과 같은 종류로 내보내 음악 소리를 잠시 줄이게 한다
          tts.setAudioAttributes(
            AudioAttributes.Builder()
              .setUsage(AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE)
              .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
              .build()
          )
          ready = true
          pending?.let { (text, then) ->
            pending = null
            main.post { speak(text, then) }
          }
        }

        override fun onFailure(errorType: ErrorType) {
          Log.e(TAG, "TTS 초기화 실패: $errorType")
        }
      }
    )
  }

  /** 말하던 것이 있으면 끊고 새 문장을 말한다. 끝까지 말하면 [then]을 메인 스레드에서 부른다 */
  fun speak(text: String, then: (() -> Unit)? = null) {
    if (!ready) {
      pending = text to then
      return
    }
    runCatching {
      if (speaking) tts.stop()
      afterSpeech = then
      tts.speak(text, true)
      Log.i(TAG, "TTS 요청: $text")
    }.onFailure { Log.e(TAG, "TTS 요청 실패", it) }
  }

  fun stop() {
    pending = null
    afterSpeech = null
    talking = false
    if (ready && speaking) runCatching { tts.stop() }
  }

  fun release() {
    ready = false
    runCatching { tts.removeEventListener(events) }
    runCatching { tts.release() }
  }

  private val events =
    object : EventListener {
      override fun onReady() = Unit

      override fun onStart() {
        speaking = true
        main.post { talking = true }
        Log.i(TAG, "TTS 재생 시작")
      }

      override fun onUpdatedRms(rms: Double) = Unit

      override fun onDone() {
        speaking = false
        main.post { talking = false }
        Log.i(TAG, "TTS 재생 끝")
        val then = afterSpeech ?: return
        afterSpeech = null
        main.post(then)
      }

      override fun onStop() {
        speaking = false
        main.post { talking = false }
      }

      override fun onError(errorType: ErrorType) {
        speaking = false
        main.post { talking = false }
        Log.w(TAG, "TTS 오류: $errorType")
      }
    }

  companion object {
    private const val TAG = "VoiceGuide"
  }
}
