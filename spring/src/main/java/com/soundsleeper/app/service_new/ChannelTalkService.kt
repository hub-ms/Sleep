package com.soundsleeper.app.service_new

import com.soundsleeper.app.config.ChannelTalkProperties
import com.soundsleeper.app.data.remote.dto.response.ChannelTalkHashResponse
import org.springframework.stereotype.Service
import java.util.HexFormat
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

@Service
class ChannelTalkService(
    private val channelTalkProperties: ChannelTalkProperties,
) {
    /**
     * 채널톡 "회원 인증(고객 정보 암호화)" 기능용 memberHash 계산.
     * 채널톡 데스크에서 발급하는 시크릿 키는 hex 문자열이며, HMAC 키로 쓰기 전에 raw bytes로
     * 디코딩해야 한다(공식 문서 PHP 예제의 pack("H*", $secretKey)에 해당).
     */
    fun getMemberHash(userId: Long): ChannelTalkHashResponse {
        val secretKeyBytes = HexFormat.of().parseHex(channelTalkProperties.hashSecret)
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(secretKeyBytes, "HmacSHA256"))
        val hash = mac.doFinal(userId.toString().toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
        return ChannelTalkHashResponse(memberHash = hash)
    }
}