package com.dejaroad.app

import android.app.Application

class DejaRoadApp : Application() {
  /** 화면과 포그라운드 서비스가 함께 쓰는 세션 하나 */
  val session by lazy { RoadSession(this) }
}
