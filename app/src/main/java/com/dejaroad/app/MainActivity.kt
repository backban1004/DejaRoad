package com.dejaroad.app

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.dejaroad.app.ui.DejaRoadTheme
import com.dejaroad.app.ui.DrivingPanel
import com.dejaroad.app.ui.IdlePanel
import com.dejaroad.app.ui.Palette
import com.dejaroad.app.ui.PreviewPanel
import com.dejaroad.app.ui.ReviewPanel

class MainActivity : ComponentActivity() {

  companion object {
    /** 실행 중에 받아야 하는 권한 (Pleos 차량 권한은 설치 시 부여) */
    private val RUNTIME_PERMISSIONS = arrayOf("pleos.car.permission.FUSED_LOCATION", Manifest.permission.ACCESS_FINE_LOCATION, "android.car.permission.CAR_SPEED")

    // 시연 스크립트용 명령 (adb shell am start --activity-single-top -n com.dejaroad.app/.MainActivity ...)
    /** 광교 갤러리아까지 내비 경로를 만든다 */
    const val EXTRA_DEMO_ROUTE = "demo_route"
    /** 내비 대신 시연용 회전 안내 목록을 넣는다 (내비가 경로를 못 줄 때) */
    const val EXTRA_DEMO_STEPS = "demo_steps"
    /** 지금 경로의 분기점·합류에 '지난 화요일' 급제동 기록을 넣는다 */
    const val EXTRA_SEED_PAST = "seed_past"
    /** 길 기억을 모두 지운다 */
    const val EXTRA_CLEAR = "clear"
    /** 경로 위 위치를 이 거리(m)로 옮긴다 */
    const val EXTRA_JUMP = "jump"
    /** 예습을 읽는다 */
    const val EXTRA_PLAY = "play"
    /** 주행을 끝내고 복습을 보인다 */
    const val EXTRA_END = "end"
  }

  private val session: RoadSession
    get() = (application as DejaRoadApp).session

  private val permissionRequest = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { RoadService.start(this) }

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    val wanted = RUNTIME_PERMISSIONS + if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) arrayOf(Manifest.permission.POST_NOTIFICATIONS) else emptyArray()
    val missing = wanted.filter { checkSelfPermission(it) != PackageManager.PERMISSION_GRANTED }
    if (missing.isEmpty()) RoadService.start(this) else permissionRequest.launch(missing.toTypedArray())
    session.start()
    if (savedInstanceState == null) handleExtras(intent)

    setContent {
      DejaRoadTheme {
        Surface(Modifier.fillMaxSize(), color = Palette.background) {
          when (session.mode) {
            Mode.IDLE -> IdlePanel(session)
            Mode.PREVIEW -> PreviewPanel(session)
            Mode.DRIVING -> DrivingPanel(session)
            Mode.REVIEW -> ReviewPanel(session)
          }
        }
      }
    }
  }

  override fun onNewIntent(intent: Intent) {
    super.onNewIntent(intent)
    handleExtras(intent)
  }

  private fun handleExtras(intent: Intent) {
    if (intent.getBooleanExtra(EXTRA_CLEAR, false)) session.clearMemory()
    if (intent.getBooleanExtra(EXTRA_DEMO_ROUTE, false)) session.demoRoute()
    if (intent.getBooleanExtra(EXTRA_DEMO_STEPS, false)) session.loadDemoSteps()
    if (intent.getBooleanExtra(EXTRA_SEED_PAST, false)) session.seedPast()
    if (intent.hasExtra(EXTRA_JUMP)) session.jumpTo(intent.getFloatExtra(EXTRA_JUMP, 0f).toDouble())
    if (intent.getBooleanExtra(EXTRA_PLAY, false)) session.playPreview()
    if (intent.getBooleanExtra(EXTRA_END, false)) session.endTrip()
  }
}
