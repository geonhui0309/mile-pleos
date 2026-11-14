package io.gildongmu.voiceprobe;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.drawable.AnimationDrawable;
import android.view.View;
import android.widget.ImageView;

/** Rendered pose frames, not flat rotation of the original PNG or a real-time 3D rig. */
final class SearchingCompanion extends ImageView {
    private boolean searching;
    private AnimationDrawable motion;

    SearchingCompanion(Context context) {
        super(context);
        setId(R.id.search_companion);
        setScaleType(ScaleType.FIT_CENTER);
        setContentDescription("주변 이야기를 찾는 마일 캐릭터");
        int[] frames = {R.drawable.mile_search_v2_0, R.drawable.mile_search_v2_1,
                R.drawable.mile_search_v2_2, R.drawable.mile_search_v2_3,
                R.drawable.mile_search_v2_4, R.drawable.mile_search_v2_5,
                R.drawable.mile_search_v2_6, R.drawable.mile_search_v2_7};
        int[] durations = {400, 640, 110, 640, 560, 320, 320, 560};
        motion = new AnimationDrawable(); motion.setOneShot(false);
        for (int i = 0; i < frames.length; i++) motion.addFrame(context.getDrawable(frames[i]), durations[i]);
        setImageDrawable(motion); setVisibility(GONE);
    }
    void setPresentation(boolean visible, boolean searching) {
        this.searching = searching;
        setVisibility(visible ? VISIBLE : GONE);
        setContentDescription(searching ? "주변 이야기를 찾는 마일 캐릭터" : "이야기 시작을 기다리는 마일 캐릭터");
        updateMotion();
    }
    boolean isMotionRunning() { return motion != null && motion.isRunning(); }
    int currentFrameIndex() {
        if (motion != null) for (int i = 0; i < motion.getNumberOfFrames(); i++)
            if (motion.getCurrent() == motion.getFrame(i)) return i;
        return -1;
    }
    private void updateMotion() {
        if (motion == null) return;
        boolean play = searching && isAttachedToWindow() && isShown()
                && getWindowVisibility() == VISIBLE && ValueAnimator.areAnimatorsEnabled();
        if (!play) { stopMotion(); return; }
        if (!motion.isRunning()) motion.start();
    }
    private void stopMotion() {
        if (motion != null) { motion.stop(); motion.selectDrawable(0); }
    }
    @Override protected void onAttachedToWindow() { super.onAttachedToWindow(); updateMotion(); }
    @Override protected void onDetachedFromWindow() { stopMotion(); super.onDetachedFromWindow(); }
    @Override protected void onWindowVisibilityChanged(int visibility) { super.onWindowVisibilityChanged(visibility); updateMotion(); }
    @Override protected void onVisibilityChanged(View changedView, int visibility) { super.onVisibilityChanged(changedView, visibility); updateMotion(); }
}
