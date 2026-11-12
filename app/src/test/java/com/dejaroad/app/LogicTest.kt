package com.dejaroad.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HardSpotFinderTest {

  // 판교역 → 광교: 시내 회전 몇 번, 고속도로 진입, 분기점, 출구, 출구 직후 연속 회전
  private val route =
    listOf(
      Step(Turn.TURN, Side.RIGHT, 400.0, ""),
      Step(Turn.TURN, Side.LEFT, 900.0, ""),
      Step(Turn.ENTER_IC, Side.RIGHT, 300.0, "판교IC"),
      Step(Turn.STRAIGHT, Side.CENTER, 3_000.0, ""),
      Step(Turn.KEEP, Side.RIGHT, 4_600.0, "신갈JC"),
      Step(Turn.EXIT_IC, Side.RIGHT, 6_600.0, "동수원IC"),
      Step(Turn.TURN, Side.LEFT, 200.0, ""),
      Step(Turn.TURN, Side.RIGHT, 150.0, ""),
      Step(Turn.ARRIVE, Side.CENTER, 500.0, ""),
    )

  @Test
  fun picksHighwayJunctionsAndQuickTurnsInRouteOrder() {
    val spots = HardSpotFinder.find(route)
    assertEquals(listOf("판교IC 합류", "신갈JC", "동수원IC 출구", "연속 회전"), spots.map { it.name })
    assertEquals(listOf(1600.0, 9200.0, 15800.0, 16000.0), spots.map { it.atM })
    // 출구 바로 뒤 200m에 회전이 이어지므로 출구 점수가 올라간다
    assertEquals(3, spots.first { it.name == "동수원IC 출구" }.score)
  }

  @Test
  fun plainTurnsFarApartAreNotHard() {
    val spots = HardSpotFinder.find(listOf(Step(Turn.TURN, Side.RIGHT, 500.0, ""), Step(Turn.TURN, Side.LEFT, 800.0, ""), Step(Turn.ARRIVE, Side.CENTER, 400.0, "")))
    assertTrue(spots.isEmpty())
  }

  @Test
  fun pastHardSpotIsAlwaysKept() {
    // 평범한 진입(점수 1)이라도 지난번 급제동한 곳이면 들어간다
    val steps = route + listOf(Step(Turn.ENTER_UNDER, Side.CENTER, 300.0, "수원지하차도"))
    val key = HardSpotFinder.keyOf(steps.last())
    assertTrue(HardSpotFinder.find(steps).none { it.name == "수원지하차도" })
    assertTrue(HardSpotFinder.find(steps, setOf(key)).any { it.name == "수원지하차도" })
  }

  @Test
  fun atMostFourSpots() {
    val many = (1..8).map { Step(Turn.ROUNDABOUT, Side.RIGHT, 1_000.0, "교차로$it") }
    assertEquals(HardSpotFinder.MAX_SPOTS, HardSpotFinder.find(many).size)
  }
}

class HardBrakeDetectorTest {

  /** 0.2초마다 속도를 넣는다. brakeFrom..brakeTo 구간에 페달을 밟는다 */
  private fun run(speeds: List<Float>, brakeFrom: Int, brakeTo: Int): List<BrakeEvent> {
    val out = mutableListOf<BrakeEvent>()
    val d = HardBrakeDetector { out += it }
    var m = 0.0
    speeds.forEachIndexed { i, v ->
      m += v / 3.6 * 0.2
      d.feed(Tick(i * 200L, v, i in brakeFrom..brakeTo, m))
    }
    return out
  }

  @Test
  fun suddenBrakeFrom74To41IsHard() {
    val speeds = List(10) { 74f } + (0..15).map { 74f - it * 2.2f } + List(10) { 41f } + (0..10).map { 41f + it }
    val events = run(speeds, 10, 30)
    assertEquals(1, events.size)
    assertEquals(74, events[0].fromKph)
    assertEquals(41, events[0].toKph)
  }

  @Test
  fun gentleStopAtLightIsNotHard() {
    // 60 → 0을 12초에 걸쳐 (5 km/h/s)
    val speeds = List(5) { 60f } + (0..60).map { 60f - it } + List(5) { 0f }
    assertTrue(run(speeds, 5, 70).isEmpty())
  }

  @Test
  fun slowSpeedBrakeIsIgnored() {
    val speeds = List(5) { 25f } + (0..10).map { 25f - it * 2.5f }
    assertTrue(run(speeds, 5, 15).isEmpty())
  }
}

class ReviewTest {
  private val ic = HardSpot("ENTER_IC|RIGHT|판교ic", "판교IC", SpotKind.ENTER_HIGHWAY, Side.RIGHT, 1600.0, 3)
  private val jc = HardSpot("KEEP|RIGHT|신갈jc", "신갈JC", SpotKind.JUNCTION, Side.RIGHT, 9200.0, 3)
  private val ex = HardSpot("EXIT_IC|RIGHT|동수원ic", "동수원IC", SpotKind.EXIT_HIGHWAY, Side.RIGHT, 15800.0, 3)
  private val tue = 1_000L

  @Test
  fun hardAgainBetterAndFirst() {
    val memory = RoadMemory(hards = listOf(PastHard(jc.key, "신갈JC", tue, 74, 41, null, null), PastHard(ic.key, "판교IC", tue, 52, 20, null, null)))
    val brake = BrakeEvent(0, 0, 74, 41, 8_900.0)
    val rows = Review.rows(listOf(ic, jc, ex), listOf(brake), memory, 17_000.0)
    assertEquals(listOf(Outcome.BETTER, Outcome.HARD, Outcome.FIRST_OK), rows.map { it.outcome })
    assertEquals(2, rows[1].hardCount)
    assertEquals("신갈JC가 아직 어려웠어요", Review.headline(rows))
    assertEquals("2번째 급제동", Review.state(rows[1]))
  }

  @Test
  fun brakeFarFromAnySpotBelongsToNone() {
    assertNull(Review.spotOf(BrakeEvent(0, 0, 60, 30, 5_000.0), listOf(ic, jc)))
  }

  @Test
  fun unfinishedTripSkipsSpotsAhead() {
    assertEquals(listOf(ic), Review.rows(listOf(ic, jc), emptyList(), RoadMemory(), 2_000.0).map { it.spot })
  }
}

class ScriptTest {
  @Test
  fun particlesFollowReading() {
    assertEquals("신갈JC가", Script.iGa("신갈JC"))
    assertEquals("판교IC는", Script.eunNeun("판교IC"))
    assertEquals("회전교차로가", Script.iGa("회전교차로"))
    assertEquals("지하차도 옆길이", Script.iGa("지하차도 옆길"))
    assertEquals("지난 화요일과", Script.gwaWa("지난 화요일"))
    assertEquals("어제와", Script.gwaWa("어제"))
  }

  @Test
  fun distances() {
    assertEquals("1.2 km", Script.km(1_234.0))
    assertEquals("300m", Script.meters(296.0))
    assertEquals("2km", Script.meters(2_000.0))
    assertEquals("1킬로미터", Script.spoken(1_020.0))
    assertEquals("1.5킬로미터", Script.spoken(1_480.0))
    assertEquals("2킬로미터", Script.spoken(1_800.0))
    assertEquals("3킬로미터", Script.spoken(2_960.0))
    assertEquals("300미터", Script.spoken(280.0))
  }
}
