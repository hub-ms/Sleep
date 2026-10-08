package com.soundsleeper.app.data.local.repository

import com.soundsleeper.app.data.remote.api.LegalApi
import com.soundsleeper.app.domain.model.LegalDocument
import com.soundsleeper.app.domain.repository.LegalRepository
import com.soundsleeper.app.enum_.LegalType
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.Duration.Companion.seconds

class LegalRepositoryImpl(
    private val api: LegalApi,
    private val cache: LegalCache,
    private val bundled: BundledLegalSource,
) : LegalRepository {

    override suspend fun load(type: LegalType): LegalDocument {
        val remote = try {
            withTimeoutOrNull(5.seconds) { api.getDocument(type) }
        } catch (e: CancellationException) {
            throw e // 코루틴 취소는 삼키지 않는다
        } catch (e: Exception) {
            null // 네트워크/서버/파싱 오류
        }

        if (remote != null) {
            cache.save(type, remote)
            return remote
        }

        // 서버 실패 → 캐시와 번들 중 더 최신 버전을 사용 (version 은 yyyy-MM-dd)
        val cached = cache.load(type)
        val fallback = bundled.load(type)
        return if (cached != null && cached.version >= fallback.version) cached else fallback
    }
}