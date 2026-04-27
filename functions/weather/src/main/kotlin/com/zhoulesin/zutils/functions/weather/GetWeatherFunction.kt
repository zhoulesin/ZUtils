package com.zhoulesin.zutils.functions.weather

import android.util.Log
import com.zhoulesin.zutils.engine.core.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.net.URL

private const val TAG = "ZUtils-LLM"

class GetWeatherFunction : ZFunction {
    override val info = FunctionInfo(
        name = "getWeather",
        description = "Query real-time weather for a city. Returns temperature, weather condition, humidity, wind speed.",
        parameters = listOf(
            Parameter(
                name = "city",
                description = "City name preferably in English, e.g. Beijing, Tokyo, London, Wuhan. Chinese names like 上海 also supported but English is more reliable.",
                type = ParameterType.STRING,
                required = true,
            )
        ),
        outputType = OutputType.OBJECT,
    )

    override suspend fun execute(context: ExecutionContext, args: JsonObject): ZResult = withContext(Dispatchers.IO) {
        val city = args["city"]?.jsonPrimitive?.content
            ?: return@withContext ZResult.fail("Missing required argument: city", "MISSING_ARG")

        Log.i(TAG, "getWeather: city=$city")

        return@withContext try {
            val encodedCity = java.net.URLEncoder.encode(city, "UTF-8")
            val url = "https://wttr.in/$encodedCity?format=j1"
            Log.i(TAG, "getWeather: url=$url")
            val jsonText = URL(url).readText()
            Log.i(TAG, "getWeather: response=${jsonText.take(500)}")

            val root = kotlinx.serialization.json.Json.parseToJsonElement(jsonText).jsonObject
            val current = root["current_condition"]?.jsonArray?.firstOrNull()?.jsonObject
                ?: return@withContext ZResult.fail("No weather data for city: $city", "NO_DATA")

            val temp = current["temp_C"]?.jsonPrimitive?.content ?: "?"
            val desc = current["weatherDesc"]?.jsonArray?.firstOrNull()?.jsonObject?.get("value")?.jsonPrimitive?.content ?: "?"
            val humidity = current["humidity"]?.jsonPrimitive?.content ?: "?"
            val windSpeed = current["windspeedKmph"]?.jsonPrimitive?.content ?: "?"
            val feelsLike = current["FeelsLikeC"]?.jsonPrimitive?.content ?: "?"

            Log.i(TAG, "getWeather: result city=$city temp=$temp condition=$desc")

            ZResult.Success(JsonObject(buildMap {
                put("city", JsonPrimitive(city))
                put("temperature", JsonPrimitive("${temp}°C"))
                put("condition", JsonPrimitive(desc))
                put("feelsLike", JsonPrimitive("${feelsLike}°C"))
                put("humidity", JsonPrimitive("${humidity}%"))
                put("windSpeed", JsonPrimitive("${windSpeed} km/h"))
            }))
        } catch (e: Exception) {
            Log.w(TAG, "getWeather: failed city=$city error=${e.message}", e)
            ZResult.fail("Weather query failed: ${e.message}", "API_ERROR")
        }
    }
}
