package kr.safestop

import android.graphics.Bitmap
import android.graphics.Color
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter

data class Report(val place: String?, val lat: Double, val lng: Double) {
    val where get() = place ?: "위도 %.5f, 경도 %.5f".format(lat, lng)

    val sms get() = "SMSTO:112:[고속도로 정차] $where (%.5f,%.5f). 차량 고장·사고로 정차, 탑승자 대피 중".format(lat, lng)

    fun qr(size: Int): Bitmap {
        val m = QRCodeWriter().encode(sms, BarcodeFormat.QR_CODE, size, size, mapOf(EncodeHintType.CHARACTER_SET to "UTF-8"))
        val px = IntArray(size * size) { if (m[it % size, it / size]) Color.BLACK else Color.WHITE }
        return Bitmap.createBitmap(px, size, size, Bitmap.Config.RGB_565)
    }
}
