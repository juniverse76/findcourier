package xyz.juniverse.findcourier

import android.graphics.ImageFormat.*
import android.util.Log
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProcessingUtil
import androidx.camera.core.ImageProxy
import com.google.zxing.*
import com.google.zxing.common.HybridBinarizer
import java.nio.ByteBuffer


private fun ByteBuffer.toByteArray(): ByteArray {
    rewind()
    val data = ByteArray(remaining())
    get(data)
    return data
}

// QR코드 분석기.
class QrCodeAnalyzer(
    private val onQrCodesDetected: (qrCode: Result) -> Unit
) : ImageAnalysis.Analyzer {
    private val yuvFormats = mutableListOf(YUV_420_888, YUV_422_888, YUV_444_888)

    private val reader = MultiFormatReader().apply {
        val map = mapOf(
            // TODO add barcode
//            DecodeHintType.POSSIBLE_FORMATS to arrayListOf(BarcodeFormat.QR_CODE)
            DecodeHintType.POSSIBLE_FORMATS to arrayListOf(
                BarcodeFormat.CODE_39, BarcodeFormat.CODE_93,
                BarcodeFormat.CODE_128, BarcodeFormat.QR_CODE,
                BarcodeFormat.UPC_A, BarcodeFormat.UPC_E,
                BarcodeFormat.CODABAR,
                BarcodeFormat.EAN_8, BarcodeFormat.EAN_13)
        )
        setHints(map)
    }

    override fun analyze(image: ImageProxy) {
//        Log.d("juniverse", "analyze image ${image.width}, ${image.height}, ${image.format}")
        if (image.format !in yuvFormats) {
            Log.e("juniverse", "Expect YUV, now = ${image.format}")
            return
        }

        val data = image.planes[0].buffer.toByteArray()
//        val source = PlanarYUVLuminanceSource(
//            data,
//            image.width,
//            image.height,
//            0,
//            0,
//            image.width,
//            image.height,
//            false
//        )
        val rotated = rotateYUV420Degree90(data, image.width, image.height)
        val source = PlanarYUVLuminanceSource(
            rotated,
            image.height,
            image.width,
            0,
            0,
            image.height,
            image.width,
            false
        )

        val binaryBitmap = BinaryBitmap(HybridBinarizer(source))
        try {
            val result = reader.decodeWithState(binaryBitmap)
            onQrCodesDetected(result)
        } catch (e: NotFoundException) {
//            e.printStackTrace()
        }
        image.close()
    }

    private fun rotateYUV420Degree90( data: ByteArray, imageWidth: Int, imageHeight: Int ): ByteArray? {
//        Log.i("juniverse", "data length? ${data.size}")
//        val yuvSize = imageWidth * imageHeight * 3 / 2
        val yuv = ByteArray(data.size)
        // Rotate the Y luma
        var i = 0
        for (x in 0 until imageWidth) {
            for (y in imageHeight - 1 downTo 0) {
                yuv[i] = data[y * imageWidth + x]
                i++
            }
        }
        // Rotate the U and V color components
//        i = yuvSize - 1
//        var x = imageWidth - 1
//        while (x > 0) {
//            for (y in 0 until imageHeight / 2) {
//                yuv[i] = data[(imageWidth * imageHeight) + (y * imageWidth) + x]
//                i--
//                yuv[i] = data[(imageWidth * imageHeight) + (y * imageWidth) + (x - 1)]
//                i--
//            }
//            x -= 2
//        }
        return yuv
    }
}