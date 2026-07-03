package com.sleepytime.shared

import androidx.compose.runtime.Composable
import app.cash.sqldelight.ColumnAdapter
import com.sleepytime.shared.data.local.AlarmEntity
import com.sleepytime.shared.data.local.AuthInfoEntity
import com.sleepytime.shared.data.local.SleepMusicEntity
import com.sleepytime.shared.data.local.SleepSessionEntity
import com.sleepytime.shared.data.local.SleepStageEntity
import com.sleepytime.shared.data.local.UserEntity
import com.sleepytime.shared.data.local.generated.SleepDatabase
import com.sleepytime.shared.domain.model.Alarm
import com.sleepytime.shared.domain.model.EnvironmentFeature
import com.sleepytime.shared.domain.model.SleepMetrics
import com.sleepytime.shared.domain.model.SleepStage
import com.sleepytime.shared.enum_.AuthProvider
import com.sleepytime.shared.enum_.SleepStageType
import com.sleepytime.shared.platform.DatabaseDriverFactory
import com.sleepytime.shared.resources.Res
import com.sleepytime.shared.resources.title_sea
import com.sleepytime.shared.util.ResourceMapper
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.datetime.LocalDateTime
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.stringResource
import kotlin.concurrent.Volatile
import kotlin.time.Duration

object DatabaseHolder {

    @Volatile
    private var instance: SleepDatabase? = null

    private val mutex = Mutex()

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    // ── 기본 타입 어댑터 ──────────────────────────────────────

    private val intAdapter = object : ColumnAdapter<Int, Long> {
        override fun decode(databaseValue: Long) = databaseValue.toInt()
        override fun encode(value: Int) = value.toLong()
    }
    private val floatAdapter = object : ColumnAdapter<Float, Double> {
        override fun decode(databaseValue: Double) = databaseValue.toFloat()
        override fun encode(value: Float) = value.toDouble()
    }

    // ── 커스텀 데이터 모델 어댑터 (통합 완료) ─────────────────────

    private val providerAdapter = object : ColumnAdapter<AuthProvider, String> {
        override fun decode(databaseValue: String) = AuthProvider.valueOf(databaseValue)
        override fun encode(value: AuthProvider) = value.name
    }

    private val localDateTimeAdapter = object : ColumnAdapter<LocalDateTime, String> {
        override fun decode(databaseValue: String): LocalDateTime =
            LocalDateTime.parse(databaseValue)

        override fun encode(value: LocalDateTime): String = value.toString()
    }

    private val durationAdapter = object : ColumnAdapter<Duration, String> {
        override fun decode(databaseValue: String): Duration = Duration.parse(databaseValue)
        override fun encode(value: Duration): String = value.toString()
    }

    private val sleepMetricsAdapter = object : ColumnAdapter<SleepMetrics, String> {
        override fun decode(databaseValue: String): SleepMetrics =
            json.decodeFromString(databaseValue) // 💡 기존 Json -> json 변경

        override fun encode(value: SleepMetrics): String = json.encodeToString(value)
    }

    private val sleepStageListAdapter = object : ColumnAdapter<List<SleepStage>, String> {
        override fun decode(databaseValue: String): List<SleepStage> =
            if (databaseValue.isEmpty()) emptyList() else json.decodeFromString(databaseValue)

        override fun encode(value: List<SleepStage>): String = json.encodeToString(value)
    }

    private val sleepStageTypeMapAdapter =
        object : ColumnAdapter<Map<SleepStageType, Float>, String> {
            override fun decode(databaseValue: String): Map<SleepStageType, Float> =
                if (databaseValue.isEmpty()) emptyMap()
                else json.decodeFromString<Map<String, Float>>(databaseValue)
                    .mapKeys { SleepStageType.valueOf(it.key) }

            override fun encode(value: Map<SleepStageType, Float>): String =
                json.encodeToString(value.entries.associate { it.key.name to it.value })
        }

    private val environmentHistoryAdapter =
        object : ColumnAdapter<List<EnvironmentFeature.Snapshot>, String> {
            override fun decode(databaseValue: String): List<EnvironmentFeature.Snapshot> =
                if (databaseValue.isEmpty()) emptyList() else json.decodeFromString(databaseValue)

            override fun encode(value: List<EnvironmentFeature.Snapshot>): String =
                json.encodeToString(value)
        }

    private val environmentFlagsAdapter = object : ColumnAdapter<EnvironmentFeature.Flag, String> {
        override fun decode(databaseValue: String): EnvironmentFeature.Flag =
            json.decodeFromString(databaseValue)

        override fun encode(value: EnvironmentFeature.Flag): String = json.encodeToString(value)
    }

    // 🔥 크래시가 발생했던 핵심 타겟
    private val environmentStatsAdapter =
        object : ColumnAdapter<EnvironmentFeature.Statistics, String> {
            override fun decode(databaseValue: String): EnvironmentFeature.Statistics =
                json.decodeFromString(databaseValue) // 💡 대문자 Json을 소문자 json으로 교체!

            override fun encode(value: EnvironmentFeature.Statistics): String =
                json.encodeToString(value)
        }

    private val soundAdapter = object : ColumnAdapter<Alarm.Sound, String> {
        override fun decode(databaseValue: String): Alarm.Sound =
            json.decodeFromString(databaseValue)

        override fun encode(value: Alarm.Sound): String = json.encodeToString(value)
    }

    // ── 인스턴스 생성 ─────────────────────────────────────────

    suspend fun getInstance(driverFactory: DatabaseDriverFactory): SleepDatabase =
        instance ?: mutex.withLock {
            instance ?: buildDatabase(driverFactory).also { instance = it }
        }

    private suspend fun buildDatabase(driverFactory: DatabaseDriverFactory): SleepDatabase {
        val driver = driverFactory.createDriver()
        return SleepDatabase(
            driver = driver,
            AlarmEntityAdapter = AlarmEntity.Adapter(
                hourAdapter = intAdapter,
                minuteAdapter = intAdapter,
                smartAlarmRangeAdapter = intAdapter,
                soundAdapter = soundAdapter,
            ),
            SleepMusicEntityAdapter = SleepMusicEntity.Adapter(
                volumeAdapter = floatAdapter
            ),
            SleepSessionEntityAdapter = SleepSessionEntity.Adapter(
                sleepMetricsAdapter = sleepMetricsAdapter,
                stageTimelineAdapter = sleepStageListAdapter,
                stagesDistributionAdapter = sleepStageTypeMapAdapter,
                wakeCountAdapter = intAdapter,
                sleepEfficiencyAdapter = intAdapter,
                environmentHistoryAdapter = environmentHistoryAdapter,
                environmentFlagsAdapter = environmentFlagsAdapter,
                environmentStatsAdapter = environmentStatsAdapter
            ),
            AuthInfoEntityAdapter = AuthInfoEntity.Adapter(
                providerAdapter = providerAdapter
            ),
            SleepStageEntityAdapter = SleepStageEntity.Adapter(
                startTimeAdapter = localDateTimeAdapter,
                endTimeAdapter = localDateTimeAdapter,
                durationAdapter = durationAdapter
            ),
            UserEntityAdapter = UserEntity.Adapter(
                createdAtAdapter = localDateTimeAdapter,
                updatedAtAdapter = localDateTimeAdapter,
                lastLoginAtAdapter = localDateTimeAdapter
            )
        ).also { prepopulateIfEmpty(it) }
    }

    // ── 초기 데이터 ───────────────────────────────────────────

    private suspend fun prepopulateIfEmpty(db: SleepDatabase) {
        val count = db.sleepMusicEntityQueries.getAllMusic().executeAsList().size
        if (count > 0) return

        listOf(
            SleepMusicEntity(
                "sea",
                getString(ResourceMapper.getMusicTitleRes("sea")),
                "default",
                "sea",
                1800L,
                0.8f,
                isFavorite = false,
                isLooping = true,
                isPremium = false
            ),
            SleepMusicEntity(
                "forest",
                getString(ResourceMapper.getMusicTitleRes("forest")),
                "default",
                "forest",
                1800L,
                0.8f,
                isFavorite = false,
                isLooping = true,
                isPremium = false
            ),
            SleepMusicEntity(
                "stream",
                getString(ResourceMapper.getMusicTitleRes("stream")),
                "default",
                "stream",
                1800L,
                0.8f,
                isFavorite = false,
                isLooping = true,
                isPremium = false
            ),
            SleepMusicEntity(
                "campfire",
                getString(ResourceMapper.getMusicTitleRes("campfire")),
                "default",
                "campfire",
                1800L,
                0.8f,
                isFavorite = false,
                isLooping = true,
                isPremium = false
            ),
            SleepMusicEntity(
                "cricket",
                getString(ResourceMapper.getMusicTitleRes("cricket")),
                "default",
                "cricket",
                1800L,
                0.8f,
                isFavorite = false,
                isLooping = true,
                isPremium = false
            ),
            SleepMusicEntity(
                "wind",
                getString(ResourceMapper.getMusicTitleRes("wind")),
                "default",
                "wind",
                1800L,
                0.8f,
                isFavorite = false,
                isLooping = true,
                isPremium = false
            ),
            SleepMusicEntity(
                "lake",
                getString(ResourceMapper.getMusicTitleRes("lake")),
                "default",
                "lake",
                1800L,
                0.8f,
                isFavorite = false,
                isLooping = true,
                isPremium = false
            ),
            SleepMusicEntity(
                "delta",
                getString(ResourceMapper.getMusicTitleRes("delta")),
                "default",
                "delta",
                1800L,
                0.8f,
                isFavorite = false,
                isLooping = true,
                isPremium = false
            ),
            SleepMusicEntity(
                "theta",
                getString(ResourceMapper.getMusicTitleRes("theta")),
                "default",
                "theta",
                1800L,
                0.8f,
                isFavorite = false,
                isLooping = true,
                isPremium = false
            ),
        ).forEach { entity ->
            db.sleepMusicEntityQueries.insertOrIgnoreMusic(entity)
        }
    }
}