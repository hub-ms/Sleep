package com.soundsleeper.app.storage

import org.springframework.web.multipart.MultipartFile

interface FileStorage {
    /** 저장 후 접근 가능한 URL 반환 */
    fun upload(file: MultipartFile, userId: Long): String
    /** 우리가 저장한 파일일 때만 삭제 (소셜 CDN URL 등은 무시) */
    fun deleteIfOwned(url: String)
}