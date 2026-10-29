package com.nuvio.tv.petalix

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache

/** Ignore transparent padding while laying out logos; the source artwork is kept unchanged. */
internal object BrandLogoBounds {
    fun find(pixels:IntArray,width:Int,height:Int):IntArray {
        require(width>0&&height>0&&pixels.size==width*height)
        var left=width;var top=height;var right=-1;var bottom=-1
        for(y in 0 until height)for(x in 0 until width)if((pixels[y*width+x] ushr 24)>=32){
            if(x<left)left=x;if(x>right)right=x;if(y<top)top=y;if(y>bottom)bottom=y
        }
        if(right<left)return intArrayOf(0,0,width,height)
        left=(left-2).coerceAtLeast(0);top=(top-2).coerceAtLeast(0)
        right=(right+3).coerceAtMost(width);bottom=(bottom+3).coerceAtMost(height)
        return intArrayOf(left,top,right-left,bottom-top)
    }
}

internal object BrandLogoAssets {
    private val cache=LruCache<Int,Bitmap>(3)
    @Synchronized fun bitmap(context:Context,id:Int):Bitmap? {
        cache.get(id)?.let{return it}
        val original=BitmapFactory.decodeResource(context.resources,id,BitmapFactory.Options().apply{inScaled=false})?:return null
        val pixels=IntArray(original.width*original.height)
        original.getPixels(pixels,0,original.width,0,0,original.width,original.height)
        val bounds=BrandLogoBounds.find(pixels,original.width,original.height)
        val logo=Bitmap.createBitmap(original,bounds[0],bounds[1],bounds[2],bounds[3])
        cache.put(id,logo)
        return logo
    }
}
