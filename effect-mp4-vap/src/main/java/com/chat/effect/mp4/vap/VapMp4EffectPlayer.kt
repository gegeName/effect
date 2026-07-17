package com.chat.effect.mp4.vap


import android.view.View
import android.view.Gravity
import android.view.ViewGroup
import android.widget.FrameLayout
import com.chat.effect.EffectLog
import com.chat.effect.EffectResource
import com.chat.effect.IEffectPlayer
import com.chat.effect.PlayCallback
import com.tencent.qgame.animplayer.AnimConfig
import com.tencent.qgame.animplayer.AnimView
import com.tencent.qgame.animplayer.inter.IAnimListener
import com.tencent.qgame.animplayer.util.ScaleType
import java.io.File
import kotlin.math.roundToInt

/**
 * MP4 (alpha 通道) 特效播放器实现，基于腾讯 VAP（[com.tencent.qgame.animplayer.AnimView]）。
 *
 * VAP 工作流：把 MP4 视频左右两半中的右半作为 alpha mask，运行期合成出带透明通道的特效，
 * 适合礼物 / 入场动画等场景，相比 SVGA 资源更小、表现力更强。
 *
 * 类名带 `Vap` 前缀，便于业务方将来同时引入其它 MP4 实现
 * （AlphaPlayer / YYEVA 等）时区分。
 */
open class VapMp4EffectPlayer : IEffectPlayer {

    private companion object {
        const val TAG = "VapMp4EffectPlayer"
    }

    private var stage: ViewGroup? = null
    private var view: AnimView? = null

    override fun attach(stage: ViewGroup) {
        if (this.stage === stage && view != null) {
            view?.visibility = View.VISIBLE
            return
        }
        view?.let { (it.parent as? ViewGroup)?.removeView(it) }
        this.stage = stage
        view = AnimView(stage.context).apply {
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
     * 返回当前 VAP 播放控件。子类可在播放前后读取并设置业务侧需要的 View 属性。
     */
    protected fun getPlayerView(): AnimView? = view

    /**
     * VAP 播放控件创建完成后回调。子类可重写此方法设置 scaleType、循环次数、静音等属性。
     */
    protected open fun onPlayerViewCreated(view: AnimView) = Unit

    /**
     * 每次播放前回调。适合设置和单条素材绑定、或可能被 SDK 播放流程改写的属性。
     */
    protected open fun onBeforeStartPlay(
        view: AnimView,
        file: File,
        resource: EffectResource,
    ) = Unit

    override fun play(localPath: String, resource: EffectResource, callback: PlayCallback) {
        val v = view ?: return callback.onError("vap view not attached")
        val file = File(localPath)
        if (!file.exists() || file.length() == 0L) {
            return callback.onError("mp4 file not exists: $localPath")
        }
        v.setAnimListener(object : IAnimListener {
            override fun onVideoConfigReady(config: AnimConfig): Boolean {
                layoutByVideoConfig(v, config)
                return true
            }

            override fun onVideoStart() {}

            override fun onVideoRender(frameIndex: Int, config: AnimConfig?) {}

            override fun onVideoComplete() {
                callback.onComplete()
            }

            override fun onFailed(errorType: Int, errorMsg: String?) {
                EffectLog.e(TAG) { "mp4 play failed url=${resource.url} code=$errorType msg=$errorMsg" }
                callback.onError("vap $errorType:${errorMsg.orEmpty()}")
            }

            override fun onVideoDestroy() {}
        })
        onBeforeStartPlay(v, file, resource)
        v.startPlay(file)
    }

    private fun layoutByVideoConfig(v: AnimView, config: AnimConfig) {
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
    }

    override fun release() {
        view?.let { v ->
            v.stopPlay()
            v.visibility = View.GONE
        }
    }
}
