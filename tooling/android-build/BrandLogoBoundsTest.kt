package com.nuvio.tv.petalix

import org.junit.Assert.assertArrayEquals
import org.junit.Test

class BrandLogoBoundsTest {
    @Test fun transparentPaddingAndFaintNoiseDoNotShrinkTheVisibleLogo() {
        val pixels=IntArray(20*12)
        pixels[0]=0x01000000
        for(y in 4..7)for(x in 5..14)pixels[y*20+x]=0xff808080.toInt()
        assertArrayEquals(intArrayOf(3,2,14,8),BrandLogoBounds.find(pixels,20,12))
    }
    @Test fun artworkAtTheEdgeIsNeverCutOff() {
        assertArrayEquals(intArrayOf(0,0,4,3),BrandLogoBounds.find(IntArray(12){0xffffffff.toInt()},4,3))
    }
    @Test fun transparentFallbackAlwaysHasNonzeroDimensions() {
        assertArrayEquals(intArrayOf(0,0,4,3),BrandLogoBounds.find(IntArray(12),4,3))
    }
}
