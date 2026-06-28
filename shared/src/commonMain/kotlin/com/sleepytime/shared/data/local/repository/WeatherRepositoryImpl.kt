package com.sleepytime.shared.data.local.repository

import com.russhwolf.settings.ObservableSettings
import com.sleepytime.shared.platform.DeviceSensorProvider
import com.sleepytime.shared.platform.LocationProvider
import com.sleepytime.shared.platform.SensorReadings
import com.sleepytime.shared.data.remote.dto.response.KmaResponse
import com.sleepytime.shared.util.GridCoordinateConverter
import com.sleepytime.shared.util.WeatherCorrectionCalculator
import com.sleepytime.shared.domain.repository.WeatherRepository
import com.sleepytime.shared.domain.model.WeatherState
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.datetime.Clock
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.TimeZone
import kotlinx.datetime.minus
import kotlinx.datetime.toLocalDateTime
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

// ── 이 파일에서 사용하는 로컬 타입 정의 ─────────────────────────────

/** Repository 반환 상태 — domain 레이어에 있으면 그쪽에서 import */


// ── Repository 구현 ──────────────────────────────────────────────────

class WeatherRepositoryImpl(
    private val httpClient: HttpClient,
    private val settings: ObservableSettings,
    private val locationProvider: LocationProvider,
    private val sensorProvider: DeviceSensorProvider,
    private val gridConverter: GridCoordinateConverter,
    private val calculator: WeatherCorrectionCalculator,
    private val kmaServiceKey: String
) : WeatherRepository {

    companion object {
        private const val KMA_BASE_URL       = "https://apis.data.go.kr/1360000/VilageFcstInfoService_2.0/getUltraSrtNcst"
        private const val KEY_HOURLY_HISTORY = "hourly_history"
        private val seoulTz = TimeZone.of("Asia/Seoul")

        // Json 인스턴스는 객체 하나만 사용
        private val json = Json { ignoreUnknownKeys = true }
    }

    private var cachedWeather: WeatherState.CachedWeather? = null
    private var lastUpdateMs: Long = 0L
    private val cacheDurationMs = 60_000L

    private var correctionParams: WeatherState.CorrectionParams = loadCorrectionParams()

    private val hourlyHistory: Array<MutableList<WeatherState.HourlyObservation>> =
        Array(24) { mutableListOf() }
    private val historyMutex = Mutex()

    init {
        loadHourlyHistory()   // 앱 시작 시 이전 관측 복원
    }

    // ── 실시간 추적용 데이터 ─────────────────────────────────────────────
    private var lastIndoorWeather: WeatherState.IndoorWeather? = null

    // ── Public API ────────────────────────────────────────────────────

    override suspend fun fetchCurrentWeather(): WeatherState =
        withContext(Dispatchers.Default) {
            runCatching {
                val latLng = locationProvider.getCurrentLocation()
                val raw    = fetchWeatherData(latLng.lat, latLng.lng)
                val indoor = applyAllCorrections(raw, latLng.lat, latLng.lng)
                lastIndoorWeather = indoor
                WeatherState.Success(
                    indoor,
                    cachedWeather,
                    correctionParams
                )
            }.getOrElse { e ->
                WeatherState.Error(e.message ?: "알 수 없는 오류")
            }
        }

    override suspend fun getCurrentTemperature(): Float {
        val latLng = locationProvider.getCurrentLocation()
        val raw = fetchWeatherData(latLng.lat, latLng.lng)
        val indoor = applyAllCorrections(raw, latLng.lat, latLng.lng)
        lastIndoorWeather = indoor
        return indoor.indoorTemp
    }

    override suspend fun getCurrentHumidity(): Float {
        val latLng = locationProvider.getCurrentLocation()
        val raw = fetchWeatherData(latLng.lat, latLng.lng)
        val indoor = applyAllCorrections(raw, latLng.lat, latLng.lng)
        lastIndoorWeather = indoor
        return indoor.indoorHumidity
    }

    // ── 보정 파이프라인 ────────────────────────────────────────────────

    private suspend fun applyAllCorrections(
        raw: WeatherState.CachedWeather,
        lat: Double,
        lng: Double
    ): WeatherState.IndoorWeather = withContext(Dispatchers.Default) {

        // Clock.System.now() 한 번만 호출 → hour와 month가 같은 순간을 가리킴
        val nowLocal  = Clock.System.now().toLocalDateTime(seoulTz)
        val hour      = nowLocal.hour
        val month     = nowLocal.monthNumber

        val sensors   = sensorProvider.getLatestReadings()

        // 1단계
        val (t1, h1)  = applyDeviceSensorCorrection(raw.temp, raw.humidity, sensors)

        // 2단계
        val (t2, h2)  = applyGeographicCorrection(t1, h1, lat, lng)

        // 3단계
        val (t3, h3)  = applyTimePatternCorrection(t2, h2, hour)
        val histSize  = historyMutex.withLock { hourlyHistory[hour].size }
        val conf      = if (histSize >= 7) 0.82f else 0.74f

        // 범위 제한
        val season    = calculator.getSeason(month)
        val bounds    = calculator.getSeasonalBounds(season)  // List<Float> size=4
        val finalTemp     = t3.coerceIn(bounds[0], bounds[1])
        val finalHumidity = h3.coerceIn(bounds[2], bounds[3])

        recordHourlyObservation(hour, finalTemp, finalHumidity)

        WeatherState.IndoorWeather(
            outdoorTemp = raw.temp,
            outdoorHumidity = raw.humidity,
            indoorTemp = finalTemp,
            indoorHumidity = finalHumidity,
            season = season,
            correctionStage = 3,
            confidence = conf
        )
    }

    // ── 보정 단계별 함수 ──────────────────────────────────────────────

    private fun applyDeviceSensorCorrection(
        baseTemp: Float, baseHumidity: Float, sensors: SensorReadings
    ): Pair<Float, Float> {
        var temp     = baseTemp
        var humidity = baseHumidity

        sensors.pressureHpa?.let { pressure ->
            temp -= calculator.pressureToAltitudeMeters(pressure) * correctionParams.elevationTempDrop
        }
        sensors.ambientTempC?.let { sensorTemp ->
            temp = temp * 0.65f + sensorTemp * 0.35f + correctionParams.deviceTempOffset
        }
        sensors.humidityPercent?.let { sensorHumidity ->
            humidity = humidity * 0.65f + sensorHumidity * 0.35f + correctionParams.deviceHumidityOffset
        }
        return temp to humidity
    }

    private fun applyGeographicCorrection(
        baseTemp: Float, baseHumidity: Float, lat: Double, lng: Double
    ): Pair<Float, Float> {
        val coastalFactor = calculator.estimateCoastalFactor(lat, lng)
        val climateZone   = calculator.estimateKoreanClimateZone(lat, lng)
        val urbanHeat     = calculator.estimateUrbanHeatIsland(lat, lng)

        return (baseTemp + correctionParams.climateZoneBias * climateZone + urbanHeat) to
                (baseHumidity + coastalFactor * correctionParams.coastalHumidityBonus)
    }

    private suspend fun applyTimePatternCorrection(
        baseTemp: Float, baseHumidity: Float, hour: Int
    ): Pair<Float, Float> {
        val recent = historyMutex.withLock { hourlyHistory[hour].toList() }
            .sortedByDescending { it.timestamp }
            .take(14)

        if (recent.size < 3) return baseTemp to baseHumidity

        val totalWeight = recent.sumOf { it.sleepQuality.toDouble() }.toFloat()
        if (totalWeight < 1f) return baseTemp to baseHumidity

        val avgTemp     = recent.sumOf { (it.indoorTemp     * it.sleepQuality).toDouble() }.toFloat() / totalWeight
        val avgHumidity = recent.sumOf { (it.indoorHumidity * it.sleepQuality).toDouble() }.toFloat() / totalWeight
        val w           = correctionParams.timePatternWeight

        return (baseTemp * (1f - w) + avgTemp * w) to
                (baseHumidity * (1f - w) + avgHumidity * w)
    }

    // ── API 호출 ─────────────────────────────────────────────────────

    private suspend fun fetchWeatherData(lat: Double, lng: Double): WeatherState.CachedWeather {
        val now = Clock.System.now().toEpochMilliseconds()
        if (now - lastUpdateMs < cacheDurationMs) {
            return cachedWeather ?: defaultWeather()
        }

        val (nx, ny)             = gridConverter.convert(lat, lng)
        val (baseDate, baseTime) = getBaseDateTime()

        return runCatching {
            val response: KmaResponse = httpClient.get(KMA_BASE_URL) {
                parameter("serviceKey", kmaServiceKey)
                parameter("pageNo",     1)
                parameter("numOfRows",  1000)
                parameter("dataType",   "JSON")
                parameter("base_date",  baseDate)
                parameter("base_time",  baseTime)
                parameter("nx",         nx)
                parameter("ny",         ny)
            }.body()
            parseKmaResponse(response, nx, ny)
        }.getOrElse {
            cachedWeather ?: defaultWeather(nx, ny)
        }.also {
            cachedWeather = it
            lastUpdateMs  = Clock.System.now().toEpochMilliseconds()
        }
    }

    private fun parseKmaResponse(response: KmaResponse, nx: String, ny: String): WeatherState.CachedWeather {
        val items = response.response.body.items.item ?: emptyList()
        fun Float.valid() = takeIf { it > -900f }

        val temp     = items.find { it.category == "T1H" }?.obsrValue?.toFloatOrNull()?.valid() ?: 22.5f
        val humidity = items.find { it.category == "REH" }?.obsrValue?.toFloatOrNull()?.valid() ?: 50f
        val precip   = items.find { it.category == "RN1" }?.obsrValue?.toFloatOrNull()?.valid()

        return WeatherState.CachedWeather(temp, humidity, precip, nx, ny)
    }

    // ── 관측 기록 저장/복원 ──────────────────────────────────────────

    private suspend fun recordHourlyObservation(hour: Int, temp: Float, humidity: Float) {
        val obs = WeatherState.HourlyObservation(
            hour = hour,
            indoorTemp = temp,
            indoorHumidity = humidity,
            sleepQuality = 50f,
            timestamp = Clock.System.now().toEpochMilliseconds()
        )
        historyMutex.withLock {
            hourlyHistory[hour].apply {
                add(obs)
                if (size > 30) removeAt(0)
            }
        }
        saveHourlyHistory()
    }

    private fun saveHourlyHistory() {
        val flat = hourlyHistory.flatMapIndexed { hour, list ->
            list.map { obs ->
                WeatherState.HourlyObservation(
                    hour,
                    obs.indoorTemp,
                    obs.indoorHumidity,
                    obs.sleepQuality,
                    obs.timestamp
                )
            }
        }
        settings.putString(KEY_HOURLY_HISTORY, json.encodeToString(flat))
    }

    private fun loadHourlyHistory() {
        val raw = settings.getStringOrNull(KEY_HOURLY_HISTORY) ?: return
        runCatching {
            json.decodeFromString<List<WeatherState.HourlyObservation>>(raw)
                .forEach { obs ->
                    if (obs.hour in 0..23) {
                        hourlyHistory[obs.hour].add(
                            WeatherState.HourlyObservation(
                                hour = obs.hour,
                                indoorTemp = obs.indoorTemp,
                                indoorHumidity = obs.indoorHumidity,
                                sleepQuality = obs.sleepQuality,
                                timestamp = obs.timestamp
                            )
                        )
                    }
                }
        }
    }

    // ── 유틸 ─────────────────────────────────────────────────────────

    private fun loadCorrectionParams() = WeatherState.CorrectionParams(
        deviceTempOffset = settings.getFloat("deviceTempOffset", 0f),
        deviceHumidityOffset = settings.getFloat("deviceHumidityOffset", 0f),
        elevationTempDrop = settings.getFloat("elevationTempDrop", 0.0065f),
        coastalHumidityBonus = settings.getFloat("coastalHumidityBonus", 0f),
        climateZoneBias = settings.getFloat("climateZoneBias", 0f),
        timePatternWeight = settings.getFloat("timePatternWeight", 0.30f)
    )

    private fun getBaseDateTime(): Pair<String, String> {
        val now  = Clock.System.now().toLocalDateTime(seoulTz)
        val hour = now.hour
        val min  = now.minute

        val baseTime = if (min >= 40)
            "${hour.toString().padStart(2, '0')}30"
        else
            "${(if (hour == 0) 23 else hour - 1).toString().padStart(2, '0')}30"

        val baseDate = if (hour == 0 && min < 40)
            now.date.minus(1, DateTimeUnit.DAY).toString().replace("-", "")
        else
            now.date.toString().replace("-", "")

        return baseDate to baseTime
    }

    private fun defaultWeather(nx: String = "60", ny: String = "127") =
        WeatherState.CachedWeather(22.5f, 50f, null, nx, ny)
}