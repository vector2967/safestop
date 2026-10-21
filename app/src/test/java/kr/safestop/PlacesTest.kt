package kr.safestop

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlacesTest {
    private val places = Places.parse(
        """
        type,name,route,direction,lat,lng
        rest,망향(부산),경부선,부산방향,36.9295,127.1520
        toll,서울TG,경부선,,37.4590,127.0420
        broken,row
        """.trimIndent()
    )

    @Test fun parsesValidRowsOnly() = assertEquals("망향(부산)", places.nearest(36.93, 127.15)!!.first.name)

    @Test fun distanceIsAboutRight() {
        val d = Places.distanceM(37.0, 127.0, 37.01, 127.0)
        assertTrue(d in 1100.0..1125.0)
    }

    @Test fun describesNearestWithoutHeading() =
        assertEquals("경부고속도로 망향휴게소에서 2.3km 떨어진 곳", places.describe(36.95, 127.152))

    @Test fun describesAheadAndBehindWithHeading() {
        val road = Places.parse(
            """
            type,name,route,direction,lat,lng
            rest,북쪽(서울),경부선,서울방향,37.00,127.0
            shelter,남쪽,경부선,부산방향,36.90,127.0
            """.trimIndent()
        )
        assertEquals("경부고속도로 북쪽휴게소 5.6km 전, 남쪽졸음쉼터에서 5.6km 지난 곳", road.describe(36.95, 127.0, 0.0))
    }

    @Test fun nextAheadSkipsPlacesBehindAndWrongType() {
        val road = Places.parse(
            """
            type,name,route,direction,lat,lng
            rest,뒤(서울),경부선,서울방향,36.90,127.0
            shelter,앞쉼터,경부선,서울방향,37.00,127.0
            rest,더앞(서울),경부선,서울방향,37.10,127.0
            """.trimIndent()
        )
        assertEquals("앞쉼터", road.nextAhead(36.95, 127.0, 0.0, setOf("rest", "shelter"))!!.first.name)
        assertEquals("더앞(서울)", road.nextAhead(36.95, 127.0, 0.0, setOf("rest"))!!.first.name)
    }

    @Test fun stopZoneRadius() {
        assertTrue(places.inStopZone(36.9300, 127.1520))
        assertFalse(places.inStopZone(36.9500, 127.1520))
    }
}
