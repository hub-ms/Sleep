package com.soundsleeper.app.storage

import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import org.springframework.web.multipart.MultipartFile
import org.springframework.web.servlet.support.ServletUriComponentsBuilder
import java.nio.file.Files
import java.nio.file.Paths
import java.nio.file.StandardCopyOption
import java.util.UUID

@Component
class LocalFileStorage(
    @Value("\${app.upload.dir}") private val uploadDir: String,
    @Value("\${app.upload.base-url}") private val baseUrl: String,
) : FileStorage {

    private val log = LoggerFactory.getLogger(javaClass)
    private val root = Paths.get(uploadDir).toAbsolutePath().normalize()

    override fun upload(file: MultipartFile, userId: Long): String {
        require(file.size <= MAX_BYTES) { "이미지는 5MB 이하만 업로드할 수 있습니다." }
        val ext = ALLOWED[file.contentType]
            ?: throw IllegalArgumentException("지원하지 않는 이미지 형식입니다: ${file.contentType}")

        // originalFilename 은 클라이언트가 조작할 수 있으므로 절대 경로에 쓰지 않는다
        val fileName = "profile_${userId}_${UUID.randomUUID()}.$ext"

        Files.createDirectories(root)
        file.inputStream.use {
            Files.copy(it, root.resolve(fileName), StandardCopyOption.REPLACE_EXISTING)
        }
        return publicUrl(fileName)
    }

    /**
     * 호스트를 설정값으로 고정하면, LAN 주소(192.168.x.x)로 저장된 URL을 ngrok으로 접속한
     * 기기가 읽지 못한다. 실제 요청이 들어온 호스트로 URL을 만들어 접속 경로와 항상 일치시킨다.
     * (ngrok의 X-Forwarded-* 반영은 server.forward-headers-strategy=framework 설정에 의존)
     */
    private fun publicUrl(fileName: String): String =
        runCatching {
            ServletUriComponentsBuilder.fromCurrentContextPath()
                .path("/uploads/")
                .path(fileName)
                .toUriString()
        }.getOrElse {
            log.warn("요청 컨텍스트가 없어 설정값 base-url로 대체: {}", it.message)
            "$baseUrl/$fileName"
        }

    override fun deleteIfOwned(url: String) {
        // 호스트가 요청마다 달라질 수 있으므로 base-url 문자열이 아니라 경로로 판별한다.
        if (!url.contains("/uploads/")) return        // 카카오/구글 CDN URL은 건드리지 않음
        val name = url.substringAfterLast('/')
        val target = root.resolve(name).normalize()
        if (!target.startsWith(root)) return          // 경로 탈출 방지
        Files.deleteIfExists(target)
    }

    companion object {
        private const val MAX_BYTES = 5L * 1024 * 1024
        private val ALLOWED = mapOf(
            "image/jpeg" to "jpg",
            "image/png" to "png",
            "image/webp" to "webp",
        )
    }
}