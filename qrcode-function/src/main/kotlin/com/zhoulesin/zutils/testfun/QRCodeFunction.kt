package com.zhoulesin.zutils.testfun

import android.graphics.Bitmap
import android.graphics.Color
import android.util.Base64
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.zhoulesin.zutils.engine.core.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonPrimitive
import java.io.ByteArrayOutputStream
import java.util.*

class QRCodeFunction : ZFunction {
    companion object {
        const val VERSION = "1.0.0"
    }

    override val requiredDependencies: Map<String, String> get() = mapOf("zxing-core" to "3.5.3")

    override val info = FunctionInfo(
        name = "generateQRCode",
        description = "Generate a QR code image from text and return it as a base64-encoded PNG data URI",
        parameters = listOf(
            Parameter(
                name = "content",
                description = "The text content to encode in the QR code",
                type = ParameterType.STRING,
                required = true,
            ),
            Parameter(
                name = "size",
                description = "Width and height of the QR code image in pixels (default 300)",
                type = ParameterType.INTEGER,
                required = false,
                defaultValue = JsonPrimitive(300),
            ),
            Parameter(
                name = "foreground",
                description = "Foreground color as hex (e.g. \"#000000\"). Default black.",
                type = ParameterType.STRING,
                required = false,
                defaultValue = JsonPrimitive("#000000"),
            ),
            Parameter(
                name = "background",
                description = "Background color as hex (e.g. \"#FFFFFF\"). Default white.",
                type = ParameterType.STRING,
                required = false,
                defaultValue = JsonPrimitive("#FFFFFF"),
            ),
        ),
    )

    override suspend fun execute(context: ExecutionContext, args: JsonObject): ZResult = withContext(Dispatchers.Default) {
        val content = args["content"]?.jsonPrimitive?.content
            ?: return@withContext ZResult.fail("Missing required argument: content", "MISSING_ARG")

        val size = args["size"]?.jsonPrimitive?.content?.toIntOrNull() ?: 300
        if (size !in 50..2000) {
            return@withContext ZResult.fail("Size must be between 50 and 2000", "INVALID_ARG")
        }

        val fgColor = parseColor(args["foreground"]?.jsonPrimitive?.content, Color.BLACK)
        val bgColor = parseColor(args["background"]?.jsonPrimitive?.content, Color.WHITE)

        return@withContext try {
            val writer = QRCodeWriter()
            val hints = mapOf(
                EncodeHintType.MARGIN to 1,
                EncodeHintType.CHARACTER_SET to "UTF-8",
            )
            val bitMatrix = writer.encode(content, BarcodeFormat.QR_CODE, size, size, hints)

            val width = bitMatrix.width
            val height = bitMatrix.height
            val pixels = IntArray(width * height)

            for (y in 0 until height) {
                for (x in 0 until width) {
                    pixels[y * width + x] = if (bitMatrix[x, y]) fgColor else bgColor
                }
            }

            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            bitmap.setPixels(pixels, 0, width, 0, 0, width, height)

            val output = ByteArrayOutputStream()
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)
            bitmap.recycle()

            val base64 = Base64.encodeToString(output.toByteArray(), Base64.NO_WRAP)
            val dataUri = "data:image/png;base64,$base64"

            ZResult.Success(JsonObject(buildMap {
                put("dataUri", JsonPrimitive(dataUri))
                put("width", JsonPrimitive(width))
                put("height", JsonPrimitive(height))
                put("content", JsonPrimitive(content))
            }), MediaType.IMAGE_PNG)
        } catch (e: Exception) {
            ZResult.fail("QR code generation failed: ${e.message}", "GENERATION_ERROR")
        }
    }

    private fun parseColor(hex: String?, fallback: Int): Int {
        if (hex == null) return fallback
        return try {
            Color.parseColor(hex)
        } catch (_: Exception) {
            fallback
        }
    }
}
