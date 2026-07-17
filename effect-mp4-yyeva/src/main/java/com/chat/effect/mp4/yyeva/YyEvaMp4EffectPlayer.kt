package com.chat.effect.mp4.yyeva

import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import com.chat.effect.EffectLog
import com.chat.effect.EffectResource
import com.chat.effect.IEffectPlayer
import com.chat.effect.PlayCallback
import com.yy.yyeva.EvaAnimConfig
import com.yy.yyeva.inter.IEvaAnimListener
import com.yy.yyeva.util.ScaleType
import com.yy.yyeva.view.EvaAnimViewV3
import java.io.File
import kotlin.math.roundToInt

/**
 * MP4 (alpha 通道) 特效播放器实现，基于 YYEVA（[EvaAnimViewV3]）。
 *
 * YYEVA 使用 Native OpenGL ES 渲染 alpha-MP4，适合直播礼物 / 进场动画等场景。
 * 类名带 `YyEva` 前缀，便于业务方同时引入 VAP / AlphaPlayer 等 MP4 实现时区分。
 */
open class YyEvaMp4EffectPlayer : IEffectPlayer {

    private companion object {
        const val TAG = "YyEvaMp4EffectPlayer"
    }

    private var stage: ViewGroup? = null
    private var view: EvaAnimViewV3? = null

    override fun attach(stage: ViewGroup) {
        if (this.stage === stage && view != null) {
            view?.visibility = View.VISIBLE
            return
        }
        view?.let { (it.parent as? ViewGroup)?.removeView(it) }
        this.stage = stage
        view = EvaAnimViewV3(stage.context).apply {
            visibility = View.VISIBLE
            isClickable = false
            isFocusable = false
            setScaleType(ScaleType.FIT_CENTER)
            setLoop(1)
        }
        view?.let { onPlayerViewCreated(it) }
        stage.addView(
            view,
            ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
            ),
        )
    }

    /**
     * 返回当前 YYEVA 播放控件。子类可在播放前后读取并设置业务侧需要的 View 属性。
     */
    protected fun getPlayerView(): EvaAnimViewV3? = view

    /**
     * YYEVA 播放控件创建完成后回调。子类可重写此方法设置 scaleType、循环次数、静音等属性。
     */
    protected open fun onPlayerViewCreated(view: EvaAnimViewV3) = Unit

    override fun play(localPath: String, resource: EffectResource, callback: PlayCallback) {
        val v = view ?: return callback.onError("yyeva view not attached")
        val file = File(localPath)
        if (!file.exists() || file.length() == 0L) {
            return callback.onError("mp4 file not exists: $localPath")
        }
        val once = OncePlayCallback(callback)
        v.visibility = View.VISIBLE
        v.setAnimListener(object : IEvaAnimListener {
            override fun onVideoConfigReady(config: EvaAnimConfig): Boolean {
                layoutByVideoConfig(v, config)
                return true
            }

            override fun onVideoStart() {}

            override fun onVideoRestart() {}

            override fun onVideoRender(frameIndex: Int, config: EvaAnimConfig?) {}

            override fun onVideoDestroy() {}

            override fun onFailed(errorType: Int, errorMsg: String?) {
                EffectLog.e(TAG) { "mp4 play failed url=${resource.url} code=$errorType msg=$errorMsg" }
                once.onError("yyeva $errorType:${errorMsg.orEmpty()}")
            }

            override fun onVideoComplete(lastFrame: Boolean) {
                once.onComplete()
            }
        })
        v.startPlay(file)
    }

    private fun layoutByVideoConfig(v: EvaAnimViewV3, config: EvaAnimConfig) {
        val videoWidth = config.width.takeIf { it > 0 } ?: config.videoWidth
        val videoHeight = config.height.takeIf { it > 0 } ?: config.videoHeight
        if (videoWidth <= 0 || videoHeight <= 0) return

        val parent = (v.parent as? ViewGroup) ?: stage ?: return
        val targetWidth = parent.width.takeIf { it > 0 } ?: parent.resources.displayMetrics.widthPixels
        if (targetWidth <= 0) return

        val targetHeight = (targetWidth * videoHeight.toDouble() / videoWidth.toDouble()).roundToInt()
        val params = when (val current = v.layoutParams) {
            is FrameLayout.LayoutParams -> current
            else -> FrameLayout.LayoutParams(targetWidth, targetHeight)
        }
        params.width = targetWidth
        params.height = targetHeight
        params.gravity = Gravity.CENTER
        v.layoutParams = params
        v.updateTextureViewLayout()
    }

    override fun release() {
        view?.let { v ->
            v.setAnimListener(null)
            v.stopPlay()
            v.visibility = View.GONE
        }
    }

    private class OncePlayCallback(
        private val delegate: PlayCallback,
    ) : PlayCallback {
        private var called = false

        override fun onComplete() {
            if (called) return
            called = true
            delegate.onComplete()
        }

        override fun onError(reason: String) {
            if (called) return
            called = true
            delegate.onError(reason)
        }
    }
}
