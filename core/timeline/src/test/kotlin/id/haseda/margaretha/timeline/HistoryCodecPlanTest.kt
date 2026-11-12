package id.haseda.margaretha.timeline

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EditorSessionTest {

    @Test
    fun undoRedoRestoreExactStates() {
        val start = projectWith(4.0, 2.0)
        val session = EditorSession(start)
        session.perform(SplitClip("c0", 1_000_000L, "n"))
        val afterSplit = session.project
        session.perform(DeleteClips(setOf("c1")))
        val afterDelete = session.project

        assertEquals("Hapus klip", session.undo())
        assertEquals(afterSplit, session.project)
        assertEquals("Belah klip", session.undo())
        assertEquals(start, session.project)
        assertNull(session.undo())

        assertEquals("Belah klip", session.redo())
        assertEquals(afterSplit, session.project)
        session.redo()
        assertEquals(afterDelete, session.project)
        assertFalse(session.canRedo)
    }

    @Test
    fun newEditClearsRedoAndRejectedEditsAreNotRecorded() {
        val session = EditorSession(projectWith(4.0))
        session.perform(SplitClip("c0", 2_000_000L, "n"))
        session.undo()
        assertTrue(session.canRedo)
        session.perform(TrimClip("c0", 500_000L, 4_000_000L))
        assertFalse(session.canRedo)
        val depth = session.undoDepth
        assertTrue(session.perform(SplitClip("missing", 1L, "z")) is EditOutcome.Rejected)
        assertEquals(depth, session.undoDepth)
    }

    @Test
    fun historyIsBounded() {
        val session = EditorSession(projectWith(4.0), historyLimit = 3)
        repeat(6) { i -> session.perform(SetClipAudio("c0", if (i % 2 == 0) 0.5f else 0.6f, false)) }
        assertEquals(3, session.undoDepth)
    }
}

class ProjectCodecTest {

    @Test
    fun roundTripPreservesEverything() {
        val p = projectWith(4.0, 2.5)
            .applyOk(SplitClip("c0", 1_234_567L, "n\"quoted\\name"))
            .applyOk(SetClipAudio("c1", 0.35f, muted = false))
            .applyOk(RenameProject("Liburan \"Bali\" 🌴\nbaris 2"))
        val decoded = ProjectCodec.decode(ProjectCodec.encode(p))
        assertTrue(decoded is ProjectCodec.DecodeResult.Success)
        assertEquals(p, (decoded as ProjectCodec.DecodeResult.Success).project)
    }

    @Test
    fun corruptedInputFailsGracefully() {
        val good = ProjectCodec.encode(projectWith(2.0))
        listOf("", "{", good.dropLast(5), "[]", "{\"format\":\"x\"}", good.replace("\"sourceOutUs\":2000000", "\"sourceOutUs\":9000000"))
            .forEach { bad ->
                val r = ProjectCodec.decode(bad)
                assertTrue("Should fail: $bad", r is ProjectCodec.DecodeResult.Failure)
            }
    }

    @Test
    fun duplicateAssetIdsAreRejectedBeforeMapConstruction() {
        val encoded = ProjectCodec.encode(projectWith(2.0, 3.0))
        val duplicateId = encoded.replaceFirst("\"id\":\"a1\"", "\"id\":\"a0\"")
        val result = ProjectCodec.decode(duplicateId)
        assertTrue(result is ProjectCodec.DecodeResult.Failure)
        assertTrue((result as ProjectCodec.DecodeResult.Failure).reason.contains("ID media ganda"))
    }

    @Test
    fun newerSchemaIsRefusedWithClearMessage() {
        val text = ProjectCodec.encode(projectWith(2.0)).replace("\"schemaVersion\":${ProjectCodec.SCHEMA_VERSION}", "\"schemaVersion\":99")
        val r = ProjectCodec.decode(text) as ProjectCodec.DecodeResult.Failure
        assertTrue(r.reason.contains("lebih baru"))
    }

    @Test
    fun v1ProjectsMigrateWithOriginalColorLook() {
        val v1 = ProjectCodec.encode(projectWith(2.0))
            .replace("\"schemaVersion\":2", "\"schemaVersion\":1")
            .replace(",\"colorLook\":\"ORIGINAL\"", "")
        val decoded = ProjectCodec.decode(v1) as ProjectCodec.DecodeResult.Success
        assertEquals(ColorLook.ORIGINAL, decoded.project.mainTrack.clips.single().colorLook)
    }

    @Test
    fun colorLookIsUndoableAndIncludedInRenderPlan() {
        val session = EditorSession(projectWith(2.0))
        assertTrue(session.perform(SetClipColorLook("c0", ColorLook.CINEMATIC)) is EditOutcome.Applied)
        assertEquals(ColorLook.CINEMATIC, RenderSegments.of(session.project).single().colorLook)
        session.undo()
        assertEquals(ColorLook.ORIGINAL, session.project.mainTrack.clips.single().colorLook)
    }

    @Test
    fun unknownFieldsAreIgnored() {
        val text = ProjectCodec.encode(projectWith(2.0)).replaceFirst("{", "{\"futureField\":[1,2,{\"a\":null}],")
        assertTrue(ProjectCodec.decode(text) is ProjectCodec.DecodeResult.Success)
    }
}

class ExportPlannerTest {

    @Test
    fun outputSizesFollowCanvasAspect() {
        assertEquals(OutputSize(1920, 1080), ExportPlanner.outputSize(AspectRatio.LANDSCAPE_16_9, ResolutionPreset.P1080))
        assertEquals(OutputSize(1080, 1920), ExportPlanner.outputSize(AspectRatio.PORTRAIT_9_16, ResolutionPreset.P1080))
        assertEquals(OutputSize(1080, 1080), ExportPlanner.outputSize(AspectRatio.SQUARE_1_1, ResolutionPreset.P1080))
        assertEquals(OutputSize(1080, 1350), ExportPlanner.outputSize(AspectRatio.PORTRAIT_4_5, ResolutionPreset.P1080))
        assertEquals(OutputSize(3840, 2160), ExportPlanner.outputSize(AspectRatio.LANDSCAPE_16_9, ResolutionPreset.P2160))
        assertEquals(OutputSize(1280, 720), ExportPlanner.outputSize(AspectRatio.LANDSCAPE_16_9, ResolutionPreset.P720))
    }

    @Test
    fun planUsesSegmentDurationsAndEstimatesSize() {
        val p = projectWith(4.0, 2.0).applyOk(TrimClip("c0", 1_000_000L, 3_000_000L))
        val plan = (ExportPlanner.plan(p, ExportSettings()) as PlanOutcome.Ready).plan
        assertEquals(4_000_000L, plan.expectedDurationUs)
        assertEquals(listOf(1_000L to 3_000L, 0L to 2_000L), plan.segments.map { it.startMs to it.endMs })
        // 1920*1080*30*0.12 ≈ 7.46 Mbps
        assertTrue(plan.videoBitrate in 7_400_000..7_500_000)
        val expected = ((plan.videoBitrate + plan.audioBitrate) * 4.0 / 8 * 1.02).toLong()
        assertTrue(kotlin.math.abs(plan.estimatedBytes - expected) <= 1)
    }

    @Test
    fun emptyTimelineCannotExport() {
        assertTrue(ExportPlanner.plan(emptyProject(), ExportSettings()) is PlanOutcome.Invalid)
    }

    @Test
    fun mutedClipRendersWithZeroVolume() {
        val p = projectWith(2.0).applyOk(SetClipAudio("c0", 0.8f, muted = true))
        assertEquals(0f, RenderSegments.of(p).single().volume)
    }

    @Test
    fun splitSegmentsStayGaplessAfterMsRounding() {
        val p = projectWith(4.0).applyOk(SplitClip("c0", 1_234_567L, "n"))
        val (a, b) = RenderSegments.of(p)
        assertEquals(a.endMs, b.startMs)
    }

    @Test
    fun durationVerificationTolerance() {
        assertTrue(ExportPlanner.durationMatches(10_000_000L, 10_200_000L))
        assertFalse(ExportPlanner.durationMatches(10_000_000L, 10_400_000L))
        assertTrue(ExportPlanner.durationMatches(60_000_000L, 61_000_000L))
        assertFalse(ExportPlanner.durationMatches(10_000_000L, 0L))
    }

    @Test
    fun timecodeFormatting() {
        assertEquals("00:01.15", Timecode.format(1_500_000L, 30))
        assertEquals("1:01:01.00", Timecode.format(3_661_000_000L, 30))
        assertEquals("1:05", Timecode.short(65_000_000L))
        assertEquals("7.5 MB", Timecode.bytes(7_500_000L))
    }
}
