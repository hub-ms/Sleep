package com.soundsleeper.app

import app.cash.sqldelight.ColumnAdapter
import com.soundsleeper.app.data.local.AuthInfoEntity
import com.soundsleeper.app.data.local.SleepMusicEntity
import com.soundsleeper.app.data.local.SleepSessionEntity
import com.soundsleeper.app.data.local.SleepSettingEntity
import com.soundsleeper.app.data.local.SleepStageEntity
import com.soundsleeper.app.data.local.UserEntity
import com.soundsleeper.app.data.local.generated.SleepDatabase
import com.soundsleeper.app.domain.model.Alarm
import com.soundsleeper.app.domain.model.EnvironmentFeature
import com.soundsleeper.app.domain.model.SleepStage
import com.soundsleeper.app.enum_.AuthProvider
import com.soundsleeper.app.enum_.MusicCategory
import com.soundsleeper.app.enum_.SleepStageType
import com.soundsleeper.app.platform.DatabaseDriverFactory
import com.soundsleeper.app.util.ResourceMapper
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.serialization.json.Json
import org.jetbrains.compose.resources.getString
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
    private val localDateAdapter = object : ColumnAdapter<LocalDate, String> {
        override fun decode(databaseValue: String): LocalDate {
            return LocalDate.parse(databaseValue)
        }

        override fun encode(value: LocalDate): String {
            return value.toString()
        }
    }
    private val categoryAdapter = object : ColumnAdapter<MusicCategory, String> {
        override fun decode(databaseValue: String): MusicCategory =
            json.decodeFromString(databaseValue)
        override fun encode(value: MusicCategory): String = json.encodeToString(value)
    }
    private val durationAdapter = object : ColumnAdapter<Duration, String> {
        override fun decode(databaseValue: String): Duration = Duration.parse(databaseValue)
        override fun encode(value: Duration): String = value.toString()
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
            SleepSettingEntityAdapter = SleepSettingEntity.Adapter(
                alarmHourAdapter = intAdapter,
                alarmMinuteAdapter = intAdapter,
                reminderHourAdapter = intAdapter,
                reminderMinuteAdapter = intAdapter,
                smartAlarmRangeAdapter = intAdapter,
                soundAdapter = soundAdapter,
            ),
            SleepMusicEntityAdapter = SleepMusicEntity.Adapter(
                volumeAdapter = floatAdapter,
                categoryAdapter = categoryAdapter,
            ),
            SleepSessionEntityAdapter = SleepSessionEntity.Adapter(
                dateAdapter = localDateAdapter,
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
        ).also {
            prepopulateMusicCatalog(it)
            syncPremiumMusic(it)
        }
    }
    private data class SeedMusic(
        val musicName: String,
        val category: MusicCategory,
        val isPremium: Boolean = false,
    )

    private val seedMusic = listOf(
        SeedMusic("rain",MusicCategory.NATURE),
        SeedMusic("cricket",MusicCategory.NATURE),
        SeedMusic("wave",MusicCategory.NATURE),
        SeedMusic("campfire",MusicCategory.NATURE),
        SeedMusic("wind",MusicCategory.NATURE),
        SeedMusic("underwater",MusicCategory.NATURE),

        SeedMusic("meditation", MusicCategory.AMBIENT),
        SeedMusic("piano",MusicCategory.AMBIENT),
        SeedMusic("relaxation",MusicCategory.AMBIENT),
        SeedMusic("space",MusicCategory.AMBIENT),

        // 뇌파(MusicCategory.WAVE) — 에셋이 들어오면 아래 네 줄의 주석을 풀면 끝이다.
        //
        // 켜기 전에 이름이 네 곳에서 모두 일치해야 한다:
        //   musicName == imageName == drawable/<name>.webp == files/sleep_<name>.ogg
        // 제목(title_delta/theta/alpha/binaural)과 ResourceMapper.getMusicTitleRes 분기는
        // 이미 넣어 두었다. 남은 것은 getMusicImageRes 분기, 아래 네 줄, 실제 파일 8개.
        // 스키마가 그대로라 마이그레이션도 버전 업도 필요 없다(INSERT OR IGNORE).
        //
        // 지금 켜면 카드는 보이지만 .ogg 가 없어 재생이 실패하므로, 파일 수급 전까지는
        // 뇌파 칩이 빈 상태 안내(music_category_empty)를 보여주는 쪽이 맞다.
        //
        // SeedMusic("delta",MusicCategory.WAVE),
        // SeedMusic("theta",MusicCategory.WAVE),
        // SeedMusic("alpha",MusicCategory.WAVE),
        // SeedMusic("binaural",MusicCategory.WAVE),
    )
    private val premiumMusicNames: Set<String> =
        seedMusic.filter { it.isPremium }.map { it.musicName }.toSet()
    private fun syncPremiumMusic(db: SleepDatabase) {
        db.sleepMusicEntityQueries.markPremiumMusic(premiumMusicNames)
        db.sleepMusicEntityQueries.markFreeMusic(premiumMusicNames)
    }
    private suspend fun prepopulateMusicCatalog(db: SleepDatabase) {
        seedMusic.forEach { seed ->
            db.sleepMusicEntityQueries.insertOrIgnoreMusic(
                SleepMusicEntity(
                    musicName = seed.musicName,
                    // ⚠ 이 값은 더 이상 화면에 쓰이지 않는다. INSERT OR IGNORE 라서 한 번
                    // 들어간 제목이 영영 그대로이고(= strings.xml 수정이 기존 기기에 반영되지
                    // 않는다) 시드 당시 로케일로 굳어 있어 언어를 바꿔도 음악 제목만 번역되지
                    // 않았다. HomeScreen/CurrentMusicCard 는 그리는 시점에
                    // ResourceMapper.getMusicTitleRes 를 직접 읽는다. 컬럼은 NOT NULL 이라 채운다.
                    title = getString(ResourceMapper.getMusicTitleRes(seed.musicName)),
                    category = seed.category,
                    imageName = seed.musicName,
                    duration = 1800L,
                    volume = 0.7f,
                    isFavorite = false,
                    isLooping = true,
                    isPremium = seed.isPremium
                )
            )
        }
    }
}
