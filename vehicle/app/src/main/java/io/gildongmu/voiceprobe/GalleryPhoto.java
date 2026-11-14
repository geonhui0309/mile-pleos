package io.gildongmu.voiceprobe;

import android.content.Context;
import android.graphics.*;
import android.view.View;

/** Place reference photograph with presentation-only tone and canvas fades. */
final class GalleryPhoto extends View {
    private final Bitmap photo;
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private int canvas;
    private boolean dark;

    GalleryPhoto(Context context) {
        super(context);
        photo = BitmapFactory.decodeResource(getResources(), R.drawable.cheomseongdae_reference);
        setContentDescription("첨성대 장소 참고 사진 · 현재 차창의 모습이 아닙니다");
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_YES);
    }
    void theme(int canvas, boolean dark) {
        if (this.canvas == canvas && this.dark == dark) return;
        this.canvas = canvas; this.dark = dark; invalidate();
    }
    @Override protected void onDraw(Canvas c) {
        super.onDraw(c);
        float w = getWidth(), h = getHeight();
        c.drawColor(canvas);
        float left = w * .24f;
        float scale = Math.max((w - left) / photo.getWidth(), h / photo.getHeight());
        float width = photo.getWidth() * scale, height = photo.getHeight() * scale;
        float x = left + (w - left - width) / 2, y = (h - height) * .58f;
        ColorMatrix gray = new ColorMatrix(); gray.setSaturation(.04f);
        float contrast = dark ? .9f : .78f;
        float offset = dark ? -55f : 35f;
        gray.postConcat(new ColorMatrix(new float[]{contrast,0,0,0,offset, 0,contrast,0,0,offset, 0,0,contrast,0,offset, 0,0,0,1,0}));
        paint.setColorFilter(new ColorMatrixColorFilter(gray));
        paint.setShader(null); paint.setAlpha(255);
        c.save(); c.clipRect(left, 0, w, h);
        c.drawBitmap(photo, null, new RectF(x, y, x + width, y + height), paint);
        c.restore(); paint.setColorFilter(null);
        int clear = canvas & 0x00ffffff;
        paint.setShader(new LinearGradient(0,0,w,0,new int[]{canvas,canvas,clear,clear,canvas},new float[]{0,.24f,.58f,.86f,1},Shader.TileMode.CLAMP));
        c.drawRect(0,0,w,h,paint);
        paint.setShader(new LinearGradient(0,0,0,h,new int[]{canvas,clear,clear},new float[]{0,.3f,1},Shader.TileMode.CLAMP));
        c.drawRect(0,0,w,h,paint);
        paint.setShader(new LinearGradient(0,0,0,h,new int[]{clear,clear,canvas},new float[]{0,.62f,1},Shader.TileMode.CLAMP));
        c.drawRect(0,0,w,h,paint); paint.setShader(null);
    }
}
