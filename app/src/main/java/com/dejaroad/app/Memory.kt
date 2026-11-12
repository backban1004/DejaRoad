package com.dejaroad.app

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/** 지난번 어려운 곳에서 급히 줄였던 기록. [profile]은 그때 지점 앞뒤 속도(거리 기준, [SpeedProfile] 칸 수, 없으면 빈 목록) */
data class PastHard(
  val key: String,
  val name: String,
  val atMillis: Long,
  val fromKph: Int,
  val toKph: Int,
  val lat: Double?,
  val lon: Double?,
  val profile: List<Float?> = emptyList(),
)

/** 어려운 곳을 지난 기록 (급제동이 있었는지) */
data class Pass(val key: String, val atMillis: Long, val hard: Boolean)

/** 운전자의 길 기억: 어디서 어려웠고, 그 뒤로 어떻게 지났는지 */
data class RoadMemory(val hards: List<PastHard> = emptyList(), val passes: List<Pass> = emptyList()) {

  val hardKeys: Set<String>
    get() = hards.mapTo(HashSet()) { it.key }

  /** 그 곳의 가장 최근 급제동 */
  fun lastHard(key: String): PastHard? = hards.filter { it.key == key }.maxByOrNull { it.atMillis }

  fun hardCount(key: String): Int = hards.count { it.key == key }

  fun passCount(key: String): Int = passes.count { it.key == key }

  fun plus(hard: PastHard?, pass: Pass): RoadMemory = copy(hards = hards + listOfNotNull(hard), passes = passes + pass)
}

/** 길 기억을 앱 내부 파일(JSON) 하나에 둔다 */
class MemoryStore(context: Context) {
  private val file = File(context.filesDir, "road_memory.json")

  fun load(): RoadMemory =
    runCatching {
      val o = JSONObject(file.readText())
      RoadMemory(o.getJSONArray("hards").map { it.toHard() }, o.getJSONArray("passes").map { it.toPass() })
    }.getOrDefault(RoadMemory())

  fun save(m: RoadMemory) {
    val o = JSONObject()
    o.put("hards", JSONArray(m.hards.map { it.toJson() }))
    o.put("passes", JSONArray(m.passes.map { JSONObject().put("key", it.key).put("at", it.atMillis).put("hard", it.hard) }))
    file.writeText(o.toString())
  }

  private fun <T> JSONArray.map(f: (JSONObject) -> T): List<T> = (0 until length()).map { f(getJSONObject(it)) }

  private fun PastHard.toJson() =
    JSONObject().put("key", key).put("name", name).put("at", atMillis).put("from", fromKph).put("to", toKph).put("lat", lat ?: JSONObject.NULL).put("lon", lon ?: JSONObject.NULL)
      .put("profile", JSONArray(profile.map { it?.toDouble() ?: JSONObject.NULL }))

  private fun JSONObject.toHard() =
    PastHard(
      getString("key"),
      getString("name"),
      getLong("at"),
      getInt("from"),
      getInt("to"),
      optDouble("lat").takeIf { !it.isNaN() },
      optDouble("lon").takeIf { !it.isNaN() },
      optJSONArray("profile")?.let { a -> List(a.length()) { i -> if (a.isNull(i)) null else a.getDouble(i).toFloat() } } ?: emptyList(),
    )

  private fun JSONObject.toPass() = Pass(getString("key"), getLong("at"), getBoolean("hard"))
}
