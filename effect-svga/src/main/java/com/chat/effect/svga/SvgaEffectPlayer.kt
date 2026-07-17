package com.chat.effect.svga


import android.view.View
import android.view.Gravity
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import com.chat.effect.EffectIO
import com.chat.effect.EffectLog
import com.chat.effect.EffectResource
import com.chat.effect.IEffectPlayer
import com.chat.effect.PlayCallback
import com.opensource.svgaplayer.SVGACallback
import com.opensource.svgaplayer.SVGADrawable
import com.opensource.svgaplayer.SVGAImageView
import com.opensource.svgaplayer.SVGAParser
import com.opensource.svgaplayer.SVGAVideoEntity
import java.io.File
import java.io.FileInputStream
import kotlin.math.roundToInt

/**
 * SVGA 特效播放器实现。
 */
open class SvgaEffectPlayer : IEffectPlayer {

    private companion object {
        const val TAG = "SvgaEffectPlayer"
    }

    private var stage: ViewGroup? = null
    private var view: SVGAImageView? = null
    private val parser by lazy {
        val ctx = EffectIO.appContext() ?: error("EffectManager.init(context) 未调用")
        SVGAParser(ctx)
    }

    override fun attach(stage: ViewGroup) {
        if (this.stage === stage && view != null) {
            view?.visibility = View.VISIBLE
            return
        }
        view?.let { (it.parent as? ViewGroup)?.removeView(it) }
        this.stage = stage
        view = SVGAImageView(stage.context).apply {
            loops = 1
            scaleType = ImageView.ScaleType.FIT_CENTER
            isClickable = false
            isFocusable = false
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
     * 返回当前 SVGA 播放控件。子类可在播放前后读取并设置业务侧需要的 View 属性。
     */
    protected fun getPlayerView(): SVGAImageView? = view

    /**
     * SVGA 播放控件创建完成后回调。子类可重写此方法设置 scaleType、层级、透明度等属性。
     */
    protected open fun onPlayerViewCreated(view: SVGAImageView) = Unit

    /**
     * 每次播放前回调。适合设置和单条素材绑定、或可能被 SDK 播放流程改写的属性。
     */
    protected open fun onBeforeStartPlay(
        view: SVGAImageView,
        file: File,
        resource: EffectResource,
    ) = Unit

    override fun play(localPath: String, resource: EffectResource, callback: PlayCallback) {
        val v = view ?: return callback.onError("svga view not attached")
        val file = File(localPath)
        if (!file.exists() || file.length() == 0L) {
            return callback.onError("svga file not exists: $localPath")
        }
        try {
            onBeforeStartPlay(v, file, resource)
            val inputStream = FileInputStream(file)
            parser.decodeFromInputStream(
                inputStream,
                resource.url,
                object : SVGAParser.ParseCompletion {
                    override fun onComplete(videoItem: SVGAVideoEntity) {
                        if (view !== v) {
                            return
                        }
                        layoutByVideoSize(v, videoItem)
                        v.setImageDrawable(SVGADrawable(videoItem))
                        v.callback = object : SVGACallback {
                            override fun onFinished() = callback.onComplete()
                            override fun onPause() {}
                            override fun onRepeat() {}
                            override fun onStep(frame: Int, percentage: Double) {}
                        }
                        v.startAnimation()
                    }

                    override fun onError() {
                        EffectLog.e(TAG) { "svga parse error url=${resource.url}" }
                        callback.onError("svga parse error")
                    }
                },
                true,
                null,
            )
        } catch (e: Exception) {
            EffectLog.e(TAG, e) { "svga play exception url=${resource.url}" }
            callback.onError(e.message ?: "svga play exception")
        }
    }

    private fun layoutByVideoSize(v: SVGAImageView, videoItem: SVGAVideoEntity) {
        val videoWidth = videoItem.videoSize.width
        val videoHeight = videoItem.videoSize.height
        if (videoWidth <= 0.0 || videoHeight <= 0.0) return

        val parent = (v.parent as? ViewGroup) ?: stage ?: return
        val targetWidth = parent.width.takeIf { it > 0 } ?: parent.resources.displayMetrics.widthPixels
        if (targetWidth <= 0) return

        val targetHeight = (targetWidth * videoHeight / videoWidth).roundToInt()
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
            v.stopAnimation(true)
            v.callback = null
            v.setImageDrawable(null)
            v.visibility = View.GONE
        }
    }
}
