package kr.safestop

import android.graphics.Bitmap
import android.graphics.Color
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter

data class Report(val place: String?, val lat: Double, val lng: Double, val around: Places.Around? = null) {
    val where get() = place ?: "위도 %.5f, 경도 %.5f".format(lat, lng)

    val sms get() = "SMSTO:112:[고속도로 정차] $where (%.5f,%.5f). 차량 고장·사고로 정차, 탑승자 대피 중".format(lat, lng)

    // 받는 사람을 비워 두면 휴대폰 문자 앱에서 가족을 고른다. 지도 링크는 앱이 없어도 웹으로 열린다
    val familySms get() = "SMSTO::[세이프스톱] 고속도로에서 차가 멈춰 가드레일 밖으로 대피했어요. $where " +
        "https://map.kakao.com/link/map/SafeStop,%.6f,%.6f".format(lat, lng)

    companion object {
        fun qr(text: String, size: Int): Bitmap {
            val m = QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, size, size, mapOf(EncodeHintType.CHARACTER_SET to "UTF-8"))
            val px = IntArray(size * size) { if (m[it % size, it / size]) Color.BLACK else Color.WHITE }
            return Bitmap.createBitmap(px, size, size, Bitmap.Config.RGB_565)
        }
    }
}
