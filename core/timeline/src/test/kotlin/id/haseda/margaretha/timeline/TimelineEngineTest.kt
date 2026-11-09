package id.haseda.margaretha.timeline

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

internal fun asset(id: String, seconds: Double, audio: Boolean = true) = MediaAsset(
    id = id,
    uri = "content://media/$id",
    displayName = "$id.mp4",
    mimeType = "video/mp4",
    durationUs = (seconds * US_PER_SECOND).toLong(),
    width = 1920,
    height = 1080,
    rotationDegrees = 0,
    hasVideo = true,
    hasAudio = audio,
    frameRate = 30f,
)

internal fun emptyProject() = Project.create("p1", "Uji", Canvas(AspectRatio.LANDSCAPE_16_9), nowMs = 1L)

internal fun Project.applyOk(edit: Edit): Project {
    val outcome = TimelineEngine.apply(this, edit)
    check(outcome is EditOutcome.Applied) { "Expected applied but was $outcome" }
    return outcome.project
}

internal fun projectWith(vararg seconds: Double): Project {
    val assets = seconds.mapIndexed { i, s -> asset("a$i", s) }
    return emptyProject().applyOk(AddClips(assets, assets.indices.map { "c$it" }))
}

class TimelineEngineTest {

    @Test
    fun addClipsLaysOutContiguously() {
        val p = projectWith(2.0, 3.0, 1.5)
        val clips = p.mainTrack.clips
        assertEquals(listOf(0L, 2_000_000L, 5_000_000L), clips.map { it.timelineStartUs })
        assertEquals(6_500_000L, p.durationUs)
        assertEquals(3, p.assets.size)
    }

    @Test
    fun addRejectsAudioOnlyAndTinyMedia() {
        val noVideo = asset("x", 3.0).copy(hasVideo = false)
        assertTrue(TimelineEngine.apply(emptyProject(), AddClips(listOf(noVideo), listOf("c"))) is EditOutcome.Rejected)
        val tiny = asset("y", 0.05)
        assertTrue(TimelineEngine.apply(emptyProject(), AddClips(listOf(tiny), listOf("c"))) is EditOutcome.Rejected)
    }

    @Test
    fun addRejectsInvalidOrDuplicateAssetsWithoutChangingProject() {
        val before = projectWith(2.0)
        val zeroWidth = asset("new", 2.0).copy(width = 0)
        assertTrue(TimelineEngine.apply(before, AddClips(listOf(zeroWidth), listOf("new-clip"))) is EditOutcome.Rejected)

        val duplicateIds = listOf(asset("new", 2.0), asset("new", 3.0))
        assertTrue(TimelineEngine.apply(before, AddClips(duplicateIds, listOf("new-1", "new-2"))) is EditOutcome.Rejected)
        assertEquals(1, before.assets.size)
        assertEquals(listOf("c0"), before.mainTrack.clips.map { it.id })
    }

    @Test
    fun splitCreatesTwoGaplessPiecesWithSameSource() {
        val p = projectWith(4.0).applyOk(SplitClip("c0", 1_500_000L, "c0b"))
        val (left, right) = p.mainTrack.clips
        assertEquals(0L to 1_500_000L, left.sourceInUs to left.sourceOutUs)
        assertEquals(1_500_000L to 4_000_000L, right.sourceInUs to right.sourceOutUs)
        assertEquals(left.timelineEndUs, right.timelineStartUs)
        assertEquals(4_000_000L, p.durationUs)
        assertEquals("c0b", right.id)
    }

    @Test
    fun splitOnSecondClipUsesTimelineTime() {
        val p = projectWith(2.0, 3.0).applyOk(SplitClip("c1", 3_000_000L, "n"))
        val right = p.mainTrack.clip("n")!!
        assertEquals(1_000_000L, right.sourceInUs) // 3.0s timeline = 1.0s into second clip
        assertEquals(3_000_000L, right.timelineStartUs)
    }

    @Test
    fun splitTooCloseToEdgeIsRejected() {
        val p = projectWith(4.0)
        val r = TimelineEngine.apply(p, SplitClip("c0", 50_000L, "n"))
        assertTrue(r is EditOutcome.Rejected)
    }

    @Test
    fun trimStartRipplesFollowingClips() {
        val p = projectWith(4.0, 2.0).applyOk(TrimClip("c0", 1_000_000L, 4_000_000L))
        val clips = p.mainTrack.clips
        assertEquals(3_000_000L, clips[0].durationUs)
        assertEquals(3_000_000L, clips[1].timelineStartUs)
        assertEquals(5_000_000L, p.durationUs)
    }

    @Test
    fun trimBeyondSourceOrTooShortIsRejected() {
        val p = projectWith(4.0)
        assertTrue(TimelineEngine.apply(p, TrimClip("c0", 0L, 5_000_000L)) is EditOutcome.Rejected)
        assertTrue(TimelineEngine.apply(p, TrimClip("c0", 1_000_000L, 1_050_000L)) is EditOutcome.Rejected)
        assertTrue(TimelineEngine.apply(p, TrimClip("c0", -1L, 2_000_000L)) is EditOutcome.Rejected)
    }

    @Test
    fun moveReordersAndRelayouts() {
        val p = projectWith(1.0, 2.0, 3.0).applyOk(MoveClip("c2", 0))
        assertEquals(listOf("c2", "c0", "c1"), p.mainTrack.clips.map { it.id })
        assertEquals(listOf(0L, 3_000_000L, 4_000_000L), p.mainTrack.clips.map { it.timelineStartUs })
    }

    @Test
    fun deleteRipplesAndDropsUnusedAssets() {
        val p = projectWith(1.0, 2.0, 3.0).applyOk(DeleteClips(setOf("c1")))
        assertEquals(listOf("c0", "c2"), p.mainTrack.clips.map { it.id })
        assertEquals(1_000_000L, p.mainTrack.clips[1].timelineStartUs)
        assertFalse(p.assets.containsKey("a1"))
    }

    @Test
    fun deleteKeepsAssetStillUsedBySplitPiece() {
        val p = projectWith(4.0).applyOk(SplitClip("c0", 2_000_000L, "n")).applyOk(DeleteClips(setOf("c0")))
        assertTrue(p.assets.containsKey("a0"))
        assertEquals(2_000_000L, p.durationUs)
    }

    @Test
    fun duplicateInsertsRightAfter() {
        val p = projectWith(1.0, 2.0).applyOk(DuplicateClip("c0", "d"))
        assertEquals(listOf("c0", "d", "c1"), p.mainTrack.clips.map { it.id })
        assertEquals(4_000_000L, p.durationUs)
    }

    @Test
    fun audioSettingsAreValidated() {
        val p = projectWith(1.0).applyOk(SetClipAudio("c0", 0.5f, muted = true))
        assertEquals(0f, p.mainTrack.clips[0].effectiveVolume)
        assertTrue(TimelineEngine.apply(p, SetClipAudio("c0", 1.5f, false)) is EditOutcome.Rejected)
    }

    @Test
    fun renameTrimsAndRejectsEmpty() {
        val p = emptyProject().applyOk(RenameProject("  Liburan  "))
        assertEquals("Liburan", p.name)
        assertTrue(TimelineEngine.apply(p, RenameProject("   ")) is EditOutcome.Rejected)
    }

    @Test
    fun locateMapsTimelineToClipAndClampsAtEnd() {
        val track = projectWith(2.0, 3.0).mainTrack
        val pos = track.locate(2_500_000L)!!
        assertEquals(1, pos.clipIndex)
        assertEquals(500_000L, pos.offsetUs)
        val end = track.locate(99_000_000L)!!
        assertEquals(1, end.clipIndex)
        assertEquals(3_000_000L, end.offsetUs)
        assertNull(emptyProject().mainTrack.locate(0L))
    }

    @Test
    fun manyEditsNeverDrift() {
        var p = projectWith(10.0)
        var n = 0
        for (t in 1..9) p = p.applyOk(SplitClip(p.mainTrack.clipAt(t * 1_000_000L)!!.id, t * 1_000_000L, "s${n++}"))
        assertEquals(10, p.mainTrack.clips.size)
        assertEquals(10_000_000L, p.durationUs)
        p.mainTrack.clips.forEach { assertEquals(1_000_000L, it.durationUs) }
        assertTrue(ProjectValidator.issues(p).isEmpty())
    }

    @Test
    fun relinkKeepsEditsAndValidatesLength() {
        val p = projectWith(4.0).applyOk(TrimClip("c0", 0L, 3_000_000L))
        val moved = asset("new", 3.5).copy(uri = "content://moved")
        val relinked = p.applyOk(RelinkAsset("a0", moved))
        assertEquals("content://moved", relinked.assets.getValue("a0").uri)
        assertEquals("a0", relinked.assets.getValue("a0").id)
        assertEquals(p.mainTrack, relinked.mainTrack)
        assertTrue(TimelineEngine.apply(p, RelinkAsset("a0", asset("short", 2.0))) is EditOutcome.Rejected)
    }
}
