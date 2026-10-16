@file:Suppress("UnstableApiUsage")
pluginManagement {
    repositories {
        gradlePluginPortal()
        google()
        mavenCentral()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        maven { url = uri("https://devrepo.kakao.com/nexus/content/groups/public/") }
        maven {
            // 이 저장소는 없는 아티팩트에 404가 아닌 403을 돌려줘서 다른 의존성 해석까지 깨뜨린다.
            // 채널톡 아티팩트에만 조회하도록 제한한다.
            url = uri("https://maven.channel.io/maven2")
            content {
                includeGroupByRegex("io\\.channel.*")
                includeGroupByRegex("com\\.zoyi.*")
            }
        }
    }
}

rootProject.name = "SoundSleeper"
include(":shared")
include(":androidApp")
include(":spring")
