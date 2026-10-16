package com.soundsleeper.app.util

object PreferencesKeys {
    object Auth {
        const val ACCESS_TOKEN = "auth_access_token"
        const val REFRESH_TOKEN = "auth_refresh_token"
        const val SOCIAL_PROVIDER = "auth_social_provider"

        // 저장된 세션을 복구하기 전에 생체 인증을 요구할지. 토큰 자체는 예전부터
        // SecureStorage(AES-GCM, Keystore)로 암호화돼 있고, 이 플래그는 "꺼내 쓰기 전에
        // 사람을 한 번 확인한다"만 켠다.
        const val BIOMETRIC_LOGIN_ENABLED = "auth_biometric_login_enabled"

        // 생체 인증 등록 팝업을 이미 띄웠는지. 한 번 거절한 사람에게 켤 때마다 다시
        // 묻지 않는다(설정 화면에서 직접 켤 수 있어야 하는 것은 별도 과제).
        const val BIOMETRIC_PROMPT_SHOWN = "auth_biometric_prompt_shown"
    }
    object SleepMusic {
        const val LAST_PLAYED_MUSIC_NAME = "last_played_sleep_music_name"
        const val LAST_PLAYED_POSITION_SECONDS = "last_played_sleep_music_position"
        const val WAS_PLAYING = "last_sleep_music_was_playing"
    }
    object Alarm {
        const val ALARM_HOUR = "alarm_hour"
        const val ALARM_MINUTE = "alarm_minute"
        const val REMINDER_HOUR = "reminder_hour"
        const val REMINDER_MINUTE = "reminder_minute"
        const val ENABLED = "alarm_enabled"
        const val ALARM_NAME = "alarm_music_name"
        const val IS_TIMER = "alarm_is_timer"
        const val TIMER_MINUTES = "alarm_timer_minutes"
        const val VIBRATION = "alarm_vibration"
        const val SMART_ALARM = "alarm_smart_alarm"
        const val SMART_ALARM_RANGE = "alarm_smart_alarm_range"
        const val REMINDER_ENABLED = "reminder_enabled"
        const val VOLUME = "alarm_volume"
    }

    object Session {
        const val KEY_SESSION_ID = "session_id"
        const val KEY_START_TIME = "start_time"
        const val KEY_DURATION = "duration"
        // 저장 키를 "music_title" 에서 바꿨다. 예전에는 여기에 곡 제목이 들어가서
        // 복원된 세션이 sleep_<제목>.ogg 라는 없는 경로를 만들었다. 키를 함께 바꿔
        // 업그레이드 직후 남아 있는 옛 값(제목)은 그냥 무시되게 한다.
        const val KEY_MUSIC_NAME = "music_name"
    }

    object App {
        const val FIRST_LAUNCH = "app_first_launch"
        const val PERMISSION_ONBOARDING_DONE = "app_permission_onboarding_done"
        const val PENDING_SIGNUP_PAYWALL = "app_pending_signup_paywall"

        // 수면 측정 안내 화면은 첫 측정 때 한 번만 보여준다. 매번 거치게 하면 잠들기 직전에
        // 같은 화면을 다시 읽히는 셈이라 성가시다. 이후에는 측정 중 화면의 물음표로 다시 볼 수 있다.
        // PERMISSION_ONBOARDING_DONE 과는 별개다. 그쪽은 최초 실행 온보딩을 마쳤는지를 뜻한다.
        const val TRACKING_GUIDE_SHOWN = "app_tracking_guide_shown"

        // 권한 안내(마이크/알림/활동인식이 왜 필요한지 설명) 화면도 수면 가이드와 마찬가지로
        // 최초 1회만 보여준다. TRACKING_GUIDE_SHOWN 과 별개의 화면이라 플래그도 분리한다.
        const val PERMISSION_GUIDE_SHOWN = "app_permission_guide_shown"
    }

    /** 온보딩 설문 답. 지금은 저장만 하고, 이후 추천·기본값 설정에 쓸 수 있다. */
    object Onboarding {
        fun surveyAnswerKey(question: String) = "onboarding_survey_$question"

        // 설문에서 뽑아낸 동물 유형. 가입 화면과 이메일 가입 화면이 설문 답을 다시 해석하지
        // 않고 이 값만 읽도록, 판정은 설문이 끝나는 순간 한 번만 한다.
        // SleepAnimalType.id 를 담는다(enum name 이 아니다 - 이름을 바꿔도 저장값은 유지된다).
        const val ANIMAL_TYPE = "onboarding_animal_type"
    }

    object Settings {
        const val KEY_PUSH_ENABLED = "is_notification_enabled"
        const val KEY_REMINDER_ENABLED = "is_sleep_reminder_enabled"
        const val KEY_REMINDER_HOUR = "reminder_hour"
        const val KEY_REMINDER_MINUTE = "reminder_minute"
    }

    /** 언어처럼 기기 전체에 적용되는 겉모습 설정. 테마는 항상 다크로 고정이라 저장하지 않는다. */
    object Appearance {
        const val LANGUAGE = "appearance_language"
    }
}