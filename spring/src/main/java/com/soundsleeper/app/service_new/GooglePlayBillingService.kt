package com.soundsleeper.app.service_new

import com.google.api.services.androidpublisher.AndroidPublisher
import com.google.api.services.androidpublisher.model.SubscriptionPurchaseV2
import com.google.api.services.androidpublisher.model.SubscriptionPurchasesAcknowledgeRequest
import com.soundsleeper.app.config.GooglePlayProperties
import org.springframework.beans.factory.ObjectProvider
import org.springframework.stereotype.Service

// AndroidPublisher는 ObjectProvider로 지연 조회한다. 생성자에서 직접 주입받으면 컨트롤러 등
// 이 서비스에 의존하는 비지연(non-lazy) 싱글톤 빈들 때문에 Spring 컨텍스트 기동 시점에
// AndroidPublisher 빈이 즉시 만들어지고, service-account-key-path가 아직 없는 로컬 개발
// 환경에서는 서버 자체가 기동에 실패한다. ObjectProvider.getObject()는 실제로 결제 검증
// 기능이 호출되는 시점까지 그 생성을 미뤄준다.
//
// 빈을 만드는 쪽은 GooglePlayApiConfig 다. 오랫동안 그 @Configuration 이 저장소에 없어서
// 이 Provider 는 항상 비어 있었고, 아래 두 메서드는 어떤 환경에서도
// NoSuchBeanDefinitionException 으로 실패했다. 지금은 @Lazy 빈이라 기동은 그대로 가볍고,
// 키가 없으면 호출 시점에 무엇을 설정해야 하는지 적힌 예외가 난다.
@Service
class GooglePlayBillingService(
    private val androidPublisherProvider: ObjectProvider<AndroidPublisher>,
    private val props: GooglePlayProperties,
) {
    private val androidPublisher: AndroidPublisher
        get() = androidPublisherProvider.getObject()

    fun getSubscriptionPurchase(purchaseToken: String): SubscriptionPurchaseV2 =
        androidPublisher.purchases()
            .subscriptionsv2()
            .get(props.packageName, purchaseToken)
            .execute()

    fun acknowledgePurchase(productId: String, purchaseToken: String) {
        androidPublisher.purchases()
            .subscriptions()
            .acknowledge(
                props.packageName,
                productId,
                purchaseToken,
                SubscriptionPurchasesAcknowledgeRequest()
            )
            .execute()
    }
}
