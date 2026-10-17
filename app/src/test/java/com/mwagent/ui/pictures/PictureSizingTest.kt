package com.mwagent.ui.pictures

import org.junit.Assert.assertEquals
import org.junit.Test

class PictureSizingTest {
    @Test
    fun `one picture is always single`() {
        assertEquals(GroupLayout.SINGLE, GroupLayout.of(1, null))
        assertEquals(GroupLayout.SINGLE, GroupLayout.of(1, PictureGrouping.SLIDER))
    }

    @Test
    fun `a few pictures make an album and many make a slider`() {
        assertEquals(GroupLayout.ALBUM, GroupLayout.of(2, null))
        assertEquals(GroupLayout.ALBUM, GroupLayout.of(4, null))
        assertEquals(GroupLayout.SLIDER, GroupLayout.of(5, null))
    }

    @Test
    fun `a requested grouping beats the count`() {
        assertEquals(GroupLayout.SLIDER, GroupLayout.of(2, PictureGrouping.SLIDER))
        assertEquals(GroupLayout.ALBUM, GroupLayout.of(9, PictureGrouping.ALBUM))
    }

    @Test
    fun `the default width stops at the attachment width`() {
        assertEquals(360f, PictureSizing.widthCap(500f, null, null))
        assertEquals(300f, PictureSizing.widthCap(300f, null, null))
    }

    @Test
    fun `an explicit share is taken from the available width`() {
        assertEquals(250f, PictureSizing.widthCap(500f, 0.5f, null))
        assertEquals(500f, PictureSizing.widthCap(500f, 1f, null))
    }

    @Test
    fun `small pictures are not blown up past twice their size`() {
        assertEquals(148f, PictureSizing.widthCap(360f, 1f, 74f))
        assertEquals(360f, PictureSizing.widthCap(360f, 1f, 800f))
    }
}
