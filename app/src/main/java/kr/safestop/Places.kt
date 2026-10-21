package kr.safestop

import kotlin.math.abs
import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

data class Place(val type: String, val name: String, val route: String, val direction: String, val lat: Double, val lng: Double)

class Places(private val all: List<Place>) {

    fun nearest(lat: Double, lng: Double, types: Set<String> = TYPES): Pair<Place, Double>? =
        all.asSequence().filter { it.type in types }
            .map { it to distanceM(lat, lng, it.lat, it.lng) }
            .minByOrNull { it.second }

    // 반경만 본다. 휴게소 바로 옆 본선 정차는 놓칠 수 있어 진입로 구분은 추후 보강
    fun inStopZone(lat: Double, lng: Double, radiusM: Double = 400.0) =
        nearest(lat, lng)?.let { it.second <= radiusM } ?: false

    fun describe(lat: Double, lng: Double, heading: Double? = null, maxM: Double = 30_000.0): String? {
        val near = all.map { it to distanceM(lat, lng, it.lat, it.lng) }.filter { it.second <= maxM }.sortedBy { it.second }
        val (closest, closestM) = near.firstOrNull() ?: return null
        val road = closest.route.removeSuffix("선") + "고속도로"
        if (heading == null) return "$road ${label(closest)}에서 ${km(closestM)} 떨어진 곳"
        val sameRoad = near.filter { it.first.route == closest.route }
        fun ahead(p: Place) = angleDiff(bearing(lat, lng, p.lat, p.lng), heading) < 90
        val front = sameRoad.firstOrNull { ahead(it.first) }
        val back = sameRoad.firstOrNull { !ahead(it.first) }
        return listOfNotNull(
            front?.let { "${label(it.first)} ${km(it.second)} 전" },
            back?.let { "${label(it.first)}에서 ${km(it.second)} 지난 곳" },
        ).joinToString(", ", prefix = "$road ")
    }

    companion object {
        val TYPES = setOf("rest", "shelter", "toll")

        private fun label(p: Place) = p.name.substringBefore('(') + when (p.type) {
            "rest" -> "휴게소"; "shelter" -> "졸음쉼터"; else -> "톨게이트"
        }

        private fun km(m: Double) = "%.1fkm".format(m / 1000)

        fun bearing(lat1: Double, lng1: Double, lat2: Double, lng2: Double): Double {
            val p1 = Math.toRadians(lat1); val p2 = Math.toRadians(lat2); val dl = Math.toRadians(lng2 - lng1)
            val y = sin(dl) * cos(p2)
            val x = cos(p1) * sin(p2) - sin(p1) * cos(p2) * cos(dl)
            return (Math.toDegrees(atan2(y, x)) + 360) % 360
        }

        private fun angleDiff(a: Double, b: Double) = abs(((a - b + 540) % 360) - 180)

        fun parse(csv: String) = Places(csv.lineSequence().drop(1).filter { it.isNotBlank() }.mapNotNull { line ->
            val c = line.split(',')
            if (c.size < 6) null
            else Place(c[0], c[1], c[2], c[3], c[4].toDoubleOrNull() ?: return@mapNotNull null, c[5].toDoubleOrNull() ?: return@mapNotNull null)
        }.toList())

        fun distanceM(lat1: Double, lng1: Double, lat2: Double, lng2: Double): Double {
            val r = 6_371_000.0
            val dLat = Math.toRadians(lat2 - lat1)
            val dLng = Math.toRadians(lng2 - lng1)
            val a = sin(dLat / 2).pow(2) + cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLng / 2).pow(2)
            return 2 * r * asin(sqrt(a))
        }
    }
}
