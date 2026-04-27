package com.zhoulesin.zutils.functions.weather

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

class GetWeatherFunction : ZFunction {
    override val info = FunctionInfo(
        name = "getWeather",
        description = "Query real-time weather for a city. Returns temperature, weather condition, humidity, wind speed.",
        parameters = listOf(
            Parameter(
                name = "city",
                description = "City name in English or Chinese, e.g. Beijing, 上海, Tokyo",
                type = ParameterType.STRING,
                required = true,
            )
        ),
        outputType = OutputType.OBJECT,
    )

    override suspend fun execute(context: ExecutionContext, args: JsonObject): ZResult = withContext(Dispatchers.IO) {
        val city = args["city"]?.jsonPrimitive?.content
            ?: return@withContext ZResult.fail("Missing required argument: city", "MISSING_ARG")

        return@withContext try {
            val url = "https://wttr.in/${java.net.URLEncoder.encode(city, "UTF-8")}?format=j1"
            val jsonText = URL(url).readText()
            val root = kotlinx.serialization.json.Json.parseToJsonElement(jsonText).jsonObject
            val current = root["current_condition"]?.jsonArray?.firstOrNull()?.jsonObject
                ?: return@withContext ZResult.fail("No weather data for city: $city", "NO_DATA")

            val temp = current["temp_C"]?.jsonPrimitive?.content ?: "?"
            val desc = current["weatherDesc"]?.jsonArray?.firstOrNull()?.jsonObject?.get("value")?.jsonPrimitive?.content ?: "?"
            val humidity = current["humidity"]?.jsonPrimitive?.content ?: "?"
            val windSpeed = current["windspeedKmph"]?.jsonPrimitive?.content ?: "?"
            val feelsLike = current["FeelsLikeC"]?.jsonPrimitive?.content ?: "?"

            ZResult.Success(JsonObject(buildMap {
                put("city", JsonPrimitive(city))
                put("temperature", JsonPrimitive("${temp}°C"))
                put("condition", JsonPrimitive(desc))
                put("feelsLike", JsonPrimitive("${feelsLike}°C"))
                put("humidity", JsonPrimitive("${humidity}%"))
                put("windSpeed", JsonPrimitive("${windSpeed} km/h"))
            }))
        } catch (e: Exception) {
            ZResult.fail("Weather query failed: ${e.message}", "API_ERROR")
        }
    }
}
