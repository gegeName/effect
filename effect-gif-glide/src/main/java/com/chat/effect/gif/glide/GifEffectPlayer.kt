package com.chat.effect.gif.glide


import android.graphics.drawable.Drawable
import android.view.View
import android.view.Gravity
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import androidx.vectordrawable.graphics.drawable.Animatable2Compat
import com.bumptech.glide.Glide
import com.bumptech.glide.load.DataSource
import com.bumptech.glide.load.engine.GlideException
import com.bumptech.glide.load.resource.gif.GifDrawable
import com.bumptech.glide.request.RequestListener
import com.bumptech.glide.request.target.Target
import com.chat.effect.EffectLog
import com.chat.effect.EffectResource
import com.chat.effect.IEffectPlayer
import com.chat.effect.PlayCallback
import java.io.File
import kotlin.math.roundToInt

/**
 * GIF 特效播放器实现，基于 Glide 5.0.5。
 */
open class GifEffectPlayer : IEffectPlayer {

    private companion object {
        const val TAG = "GifEffectPlayer"
    }

    private var stage: ViewGroup? = null
    private var view: ImageView? = null
    private var endCallback: Animatable2Compat.AnimationCallback? = null
    private var gifDrawable: GifDrawable? = null

    override fun attach(stage: ViewGroup) {
        if (this.stage === stage && view != null) {
            view?.visibility = View.VISIBLE
            return
        }
        view?.let { (it.parent as? ViewGroup)?.removeView(it) }
        this.stage = stage
        view = ImageView(stage.context).apply {
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
     * 返回当前 GIF 播放控件。子类可在播放前后读取并设置业务侧需要的 View 属性。
     */
    protected fun getPlayerView(): ImageView? = view

    /**
     * GIF 播放控件创建完成后回调。子类可重写此方法设置 scaleType、透明度、背景等属性。
     */
    protected open fun onPlayerViewCreated(view: ImageView) = Unit

    override fun play(localPath: String, resource: EffectResource, callback: PlayCallback) {
        val v = view ?: return callback.onError("gif view not attached")
        val file = File(localPath)
        if (!file.exists() || file.length() == 0L) {
            return callback.onError("gif file not exists: $localPath")
        }
        Glide.with(v.context)
            .asGif()
            .load(file)
            .listener(object : RequestListener<GifDrawable> {
                override fun onResourceReady(
                    resourceReady: GifDrawable,
                    model: Any,
                    target: Target<GifDrawable>?,
                    dataSource: DataSource,
                    isFirstResource: Boolean,
                ): Boolean {
                    if (view !== v) return false
                    gifDrawable = resourceReady
                    layoutByDrawableSize(v, resourceReady)
                    resourceReady.setLoopCount(1)
                    val cb = object : Animatable2Compat.AnimationCallback() {
                        override fun onAnimationEnd(drawable: Drawable) {
                            callback.onComplete()
                        }
                    }
                    endCallback = cb
                    resourceReady.registerAnimationCallback(cb)
                    return false
                }

                override fun onLoadFailed(
                    e: GlideException?,
                    model: Any?,
                    target: Target<GifDrawable>,
                    isFirstResource: Boolean,
                ): Boolean {
                    EffectLog.e(TAG, e) { "gif load failed url=${resource.url}" }
                    callback.onError(e?.message ?: "gif load failed")
                    return true
                }
            })
            .into(v)
    }

    private fun layoutByDrawableSize(v: ImageView, drawable: Drawable) {
        val drawableWidth = drawable.intrinsicWidth
        val drawableHeight = drawable.intrinsicHeight
        if (drawableWidth <= 0 || drawableHeight <= 0) return

        val parent = (v.parent as? ViewGroup) ?: stage ?: return
        val targetWidth = parent.width.takeIf { it > 0 } ?: parent.resources.displayMetrics.widthPixels
        if (targetWidth <= 0) return

        val targetHeight = (targetWidth * drawableHeight.toDouble() / drawableWidth.toDouble()).roundToInt()
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
        endCallback?.let { cb -> gifDrawable?.unregisterAnimationCallback(cb) }
        gifDrawable?.stop()
        view?.let { v ->
            Glide.with(v.context).clear(v)
            v.setImageDrawable(null)
            v.visibility = View.GONE
        }
        endCallback = null
        gifDrawable = null
    }
}
