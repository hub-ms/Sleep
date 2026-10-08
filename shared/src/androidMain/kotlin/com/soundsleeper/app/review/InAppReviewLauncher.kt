package com.soundsleeper.app.review

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.util.Log

object InAppReviewLauncher {

    private const val TAG = "InAppReviewLauncher"

    /** 마이페이지의 "플레이스토어 리뷰 남기기" 메뉴를 누르면 스토어 상세 페이지로 바로 이동한다. */
    fun launch(activity: Activity) {
        openPlayStore(activity)
    }

    private fun openPlayStore(activity: Activity) {
        val packageName = activity.packageName
        // market:// 는 Play 앱을 바로 연다. Play가 없는 기기(일부 태블릿/에뮬레이터)에서는
        // ActivityNotFoundException이 나므로 웹 URL로 한 번 더 시도한다.
        val marketIntent = Intent(
            Intent.ACTION_VIEW,
            "market://details?id=$packageName".toUri()
        )
        try {
            activity.startActivity(marketIntent)
        } catch (e: ActivityNotFoundException) {
            Log.w(TAG, "Play 앱을 찾지 못해 웹으로 엽니다.", e)
            try {
                activity.startActivity(
                    Intent(
                        Intent.ACTION_VIEW,
                        "https://play.google.com/store/apps/details?id=$packageName".toUri()
                    )
                )
            } catch (e2: ActivityNotFoundException) {
                Log.e(TAG, "스토어를 열 수 있는 앱이 없습니다.", e2)
            }
        }
    }

    private fun String.toUri(): Uri = Uri.parse(this)
}
