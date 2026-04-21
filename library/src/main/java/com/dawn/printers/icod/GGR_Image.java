package com.dawn.printers.icod;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.ColorMatrix;
import android.graphics.ColorMatrixColorFilter;
import android.graphics.Paint;

public class GGR_Image {
    /**
     * 转换Bitmap为黑白图片(Floyd-Steinberg)，图片颜色只有0x000000和0xffffff两种
     *
     * @param img   需要转换的图片
     * @param shake 是否抖动模式
     */
    public static Bitmap threshold(Bitmap img) {
        //转灰色图
        int height = img.getHeight();
        int width = img.getWidth();
        Bitmap bmpGrayscale = Bitmap.createBitmap(width, height, img.getConfig());
        Canvas c = new Canvas(bmpGrayscale);
        Paint paint = new Paint();
        ColorMatrix cm = new ColorMatrix();
        cm.setSaturation(0.0F);
        ColorMatrixColorFilter f = new ColorMatrixColorFilter(cm);
        paint.setColorFilter(f);
        c.drawBitmap(img, 0.0F, 0.0F, paint);
        img = bmpGrayscale;
        width = img.getWidth();
        height = img.getHeight();
        //通过位图的大小创建像素点数组
        int[] pixels = new int[width * height];
        img.getPixels(pixels, 0, width, 0, 0, width, height);
        int[] gray = new int[height * width];
        for (int i = 0; i < height; i++) {
            for (int j = 0; j < width; j++) {
                int grey = pixels[width * i + j];
                int red = ((grey & 0x00FF0000) >> 16);
                gray[width * i + j] = red;
            }
        }
        int e = 0;
        for (int i = 0; i < height; i++) {
            for (int j = 0; j < width; j++) {
                int g = gray[width * i + j];
                if (g >= 128) {
                    pixels[width * i + j] = 0xffffffff;
                    e = g - 255;
                } else {
                    pixels[width * i + j] = 0xff000000;
                    e = g - 0;
                }
                //处理抖动
                if (j < width - 1 && i < height - 1) {
                    //右边像素处理
                    gray[width * i + j + 1] += 7 * e / 16;
                    //下
                    gray[width * (i + 1) + j] += 5 * e / 16;
                    //右下
                    gray[width * (i + 1) + j + 1] += e / 16;
                    //左下
                    if (j > 0) {
                        gray[width * (i + 1) + j - 1] += 3 * e / 16;
                    }
                } else if (j == width - 1 && i < height - 1) {
                    //下方像素处理
                    gray[width * (i + 1) + j] += 5 * e / 16;
                } else if (j < width - 1 && i == height - 1) {
                    //右边像素处理
                    gray[width * (i) + j + 1] += 7 * e / 16;
                }

            }
        }
        Bitmap mBitmap = Bitmap.createBitmap(width, height, img.getConfig());
        mBitmap.setPixels(pixels, 0, width, 0, 0, width, height);
        return mBitmap;
    }


}
