package id.haseda.margaretha.timeline

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

private fun audioAsset(id: String, seconds: Double) = MediaAsset(
    id = id, uri = "content://audio/$id", displayName = "$id.mp3", mimeType = "audio/mpeg",
    durationUs = (seconds * US_PER_SECOND).toLong(), width = 0, height = 0, rotationDegrees = 0,
    hasVideo = false, hasAudio = true, kind = MediaKind.AUDIO,
)

private fun imageAsset(id: String) = MediaAsset(
    id = id, uri = "content://image/$id", displayName = "$id.png", mimeType = "image/png",
    durationUs = TimelineEngine.IMAGE_ASSET_US, width = 1200, height = 800, rotationDegrees = 0,
    hasVideo = true, hasAudio = false, kind = MediaKind.IMAGE,
)

private fun Project.rejected(edit: Edit): String {
    val outcome = TimelineEngine.apply(this, edit)
    check(outcome is EditOutcome.Rejected) { "Expected rejected but was applied" }
    return outcome.reason
}

private fun text(id: String, start: Double, seconds: Double, body: String = "Halo") =
    TextItem(id, body, (start * US_PER_SECOND).toLong(), (seconds * US_PER_SECOND).toLong())

class SpeedTest {
    @Test
    fun speedChangesTimelineDurationAndRipples() {
        val p = projectWith(4.0, 2.0).applyOk(SetClipSpeed("c0", 2000))
        assertEquals(2_000_000L, p.mainTrack.clips[0].durationUs)
        assertEquals(2_000_000L, p.mainTrack.clips[1].timelineStartUs)
        assertEquals(4_000_000L, p.durationUs)
        val slow = p.applyOk(SetClipSpeed("c0", 500))
        assertEquals(8_000_000L, slow.mainTrack.clips[0].durationUs)
    }

    @Test
    fun splitOnSpedUpClipCutsTheRightSourceFrame() {
        val p = projectWith(4.0).applyOk(SetClipSpeed("c0", 2000)).applyOk(SplitClip("c0", 500_000L, "c0b"))
        val (left, right) = p.mainTrack.clips
        assertEquals(1_000_000L, left.sourceOutUs)
        assertEquals(1_000_000L, right.sourceInUs)
        assertEquals(500_000L, right.timelineStartUs)
        assertEquals(2_000_000L, p.durationUs)
    }

    @Test
    fun speedOutOfRangeOrOnImageIsRejected() {
        val p = projectWith(4.0)
        p.rejected(SetClipSpeed("c0", 100))
        p.rejected(SetClipSpeed("c0", 9000))
        p.rejected(SetClipSpeed("c0", 1000))
        val withImage = p.applyOk(AddClips(listOf(imageAsset("img")), listOf("ci")))
        assertTrue(withImage.rejected(SetClipSpeed("ci", 2000)).contains("Gambar"))
        // 0.3 s source at 4x would be shorter than the engine minimum.
        projectWith(0.3).rejected(SetClipSpeed("c0", 4000))
    }

    @Test
    fun renderSegmentCarriesSpeedAndCumulativeStart() {
        val p = projectWith(4.0, 2.0).applyOk(SetClipSpeed("c0", 2000))
        val segments = RenderSegments.of(p)
        assertEquals(2_000_000L, segments[0].durationUs)
        assertEquals(2_000_000L, segments[1].timelineStartUs)
        val plan = (ExportPlanner.plan(p, ExportSettings()) as PlanOutcome.Ready).plan
        assertEquals(4_000_000L, plan.expectedDurationUs)
    }
}

class ImageClipTest {
    @Test
    fun imageGetsDefaultDurationAndCanBeStretchedByTrim() {
        val p = emptyProject().applyOk(AddClips(listOf(imageAsset("img")), listOf("ci")))
        assertEquals(TimelineEngine.DEFAULT_IMAGE_US, p.mainTrack.clips[0].durationUs)
        val longer = p.applyOk(TrimClip("ci", 0L, 8_000_000L))
        assertEquals(8_000_000L, longer.durationUs)
        assertEquals(MediaKind.IMAGE, RenderSegments.of(longer)[0].kind)
        assertEquals(0f, RenderSegments.of(longer)[0].volume)
    }
}

class VisualPropertyTest {
    @Test
    fun gradeFxTransformAndTransitionsAreStoredUndoableAndRendered() {
        val session = EditorSession(projectWith(3.0))
        val grade = ColorGrade(exposure = 0.4f, saturation = -0.2f)
        assertTrue(session.perform(SetClipGrade("c0", grade)) is EditOutcome.Applied)
        assertTrue(session.perform(SetClipFx("c0", ClipFx(vignette = 0.5f))) is EditOutcome.Applied)
        assertTrue(session.perform(SetClipTransform("c0", Transform(scale = 1.5f, rotation = 90f), Crop(left = 0.1f))) is EditOutcome.Applied)
        assertTrue(session.perform(SetClipTransitions("c0", Transition(TransitionKind.FADE), Transition(TransitionKind.WIPE, 1_000_000L))) is EditOutcome.Applied)
        val seg = RenderSegments.of(session.project).single()
        assertEquals(grade, seg.grade)
        assertEquals(0.5f, seg.fx.vignette)
        assertEquals(90f, seg.transform.rotation)
        assertEquals(TransitionKind.WIPE, seg.exit.kind)
        assertTrue(seg.hasVisualEdits)
        repeat(4) { session.undo() }
        assertFalse(RenderSegments.of(session.project).single().hasVisualEdits)
    }

    @Test
    fun noOpAndOutOfRangeValuesAreRejected() {
        val p = projectWith(3.0)
        p.rejected(SetClipGrade("c0", ColorGrade.NEUTRAL))
        p.rejected(SetClipFx("missing", ClipFx(blur = 0.2f)))
        assertTrue(runCatching { ColorGrade(exposure = 2f) }.isFailure)
        assertTrue(runCatching { Transform(scale = 0f) }.isFailure)
        assertTrue(runCatching { Crop(left = 0.45f, right = 0.45f, top = 0.45f, bottom = 0.45f).let { Crop(0.45f, 0.45f, 0.45f, 0.5f) } }.isFailure)
    }

    @Test
    fun splitKeepsOnlyOuterTransitionsAndFades() {
        val p = projectWith(4.0)
            .applyOk(SetClipTransitions("c0", Transition(TransitionKind.FADE), Transition(TransitionKind.ZOOM)))
            .applyOk(SetClipAudio("c0", 1f, false, fadeInUs = 500_000L, fadeOutUs = 700_000L))
            .applyOk(SplitClip("c0", 2_000_000L, "c0b"))
        val (left, right) = p.mainTrack.clips
        assertEquals(TransitionKind.FADE, left.enter.kind)
        assertTrue(left.exit.isNone)
        assertTrue(right.enter.isNone)
        assertEquals(TransitionKind.ZOOM, right.exit.kind)
        assertEquals(500_000L, left.audioFadeInUs)
        assertEquals(0L, left.audioFadeOutUs)
        assertEquals(700_000L, right.audioFadeOutUs)
    }
}

class AudioTrackTest {
    private fun base() = projectWith(10.0)
        .applyOk(AddAudioClip(audioAsset("m", 6.0), "am", Project.MUSIC_TRACK_ID, 1_000_000L))

    @Test
    fun audioClipCreatesTrackAndKeepsItsPosition() {
        val p = base()
        val track = p.track(Project.MUSIC_TRACK_ID)!!
        assertEquals(TrackKind.AUDIO, track.kind)
        assertFalse(track.magnetic)
        assertEquals(1_000_000L, track.clips.single().timelineStartUs)
        assertEquals(10_000_000L, p.durationUs)
        assertTrue(ProjectValidator.issues(p).isEmpty())
    }

    @Test
    fun secondClipIsPushedPastAnOverlapAndMovesCannotOverlap() {
        val p = base().applyOk(AddAudioClip(audioAsset("m2", 2.0), "am2", Project.MUSIC_TRACK_ID, 3_000_000L))
        val clips = p.track(Project.MUSIC_TRACK_ID)!!.clips
        assertEquals(7_000_000L, clips.first { it.id == "am2" }.timelineStartUs)
        assertTrue(p.rejected(MoveFreeClip("am2", 2_000_000L)).contains("Bertabrakan"))
        val moved = p.applyOk(MoveFreeClip("am", 0L))
        assertEquals(0L, moved.findClip("am")!!.second.timelineStartUs)
        p.rejected(MoveFreeClip("c0", 5L))
    }

    @Test
    fun trimmingTheHeadOfAFreeClipKeepsItsTailInPlace() {
        val p = base().applyOk(TrimClip("am", 2_000_000L, 6_000_000L))
        val clip = p.findClip("am")!!.second
        assertEquals(3_000_000L, clip.timelineStartUs)
        assertEquals(7_000_000L, clip.timelineEndUs)
    }

    @Test
    fun videoOnlySettingsAreRejectedOnAudioAndSilentMediaCannotBeAudio() {
        val p = base()
        p.rejected(SetClipGrade("am", ColorGrade(exposure = 0.2f)))
        p.rejected(SetClipSpeed("am", 2000))
        p.rejected(AddAudioClip(asset("silent", 3.0, audio = false), "x", Project.MUSIC_TRACK_ID, 0L))
        p.rejected(AddAudioClip(audioAsset("m9", 3.0), "x", Project.TEXT_TRACK_ID, 0L))
        p.rejected(AddClips(listOf(audioAsset("m9", 3.0)), listOf("x")))
    }

    @Test
    fun renderPlanCutsAudioToProgrammeLengthAndHonoursTrackMute() {
        val p = projectWith(4.0).applyOk(AddAudioClip(audioAsset("m", 6.0), "am", Project.MUSIC_TRACK_ID, 1_000_000L))
            .applyOk(SetClipAudio("am", 0.5f, false, fadeInUs = 300_000L, fadeOutUs = 400_000L))
        val seg = RenderSegments.plan(p).audioTracks.single().single()
        assertEquals(4_000_000L, seg.timelineEndUs)
        assertEquals(0.5f, seg.volume)
        assertEquals(300_000L, seg.fadeInUs)
        val muted = p.applyOk(SetTrackFlags(Project.MUSIC_TRACK_ID, enabled = false, locked = false))
        assertTrue(RenderSegments.plan(muted).audioTracks.isEmpty())
        // Audio that starts after the video ends is dropped completely.
        val late = projectWith(2.0).applyOk(AddAudioClip(audioAsset("m", 6.0), "am", Project.MUSIC_TRACK_ID, 5_000_000L))
        assertTrue(RenderSegments.plan(late).audioTracks.isEmpty())
    }

    @Test
    fun lockedTrackRejectsEveryEdit() {
        val p = base().applyOk(SetTrackFlags(Project.MUSIC_TRACK_ID, enabled = true, locked = true))
        assertTrue(p.rejected(DeleteClips(setOf("am"))).contains("terkunci"))
        p.rejected(MoveFreeClip("am", 0L))
        p.rejected(SetClipAudio("am", 0.2f, false))
        p.rejected(SetTrackFlags(Project.MAIN_TRACK_ID, enabled = false, locked = false))
    }
}

class TextTrackTest {
    @Test
    fun addUpdateDeleteTextIsUndoable() {
        val session = EditorSession(projectWith(5.0))
        assertTrue(session.perform(AddText(text("t1", 1.0, 2.0), Project.TEXT_TRACK_ID)) is EditOutcome.Applied)
        val moved = text("t1", 2.0, 1.0, "Baru").copy(x = 0.2f, style = TextStyle(size = 0.1f, color = 0xFFFF0000))
        assertTrue(session.perform(UpdateText(moved)) is EditOutcome.Applied)
        assertEquals(moved, session.project.findText("t1")!!.second)
        assertEquals(listOf(moved), session.project.textsAt(2_500_000L))
        assertTrue(session.project.textsAt(500_000L).isEmpty())
        assertTrue(session.perform(DeleteTexts(setOf("t1"))) is EditOutcome.Applied)
        assertNull(session.project.findText("t1"))
        session.undo()
        assertNotNull(session.project.findText("t1"))
    }

    @Test
    fun invalidTextIsRejected() {
        val p = projectWith(5.0)
        p.rejected(AddText(text("t1", 0.0, 1.0, "   "), Project.TEXT_TRACK_ID))
        p.rejected(AddText(text("t1", 0.0, 1.0), Project.MUSIC_TRACK_ID))
        p.rejected(AddText(text("c0", 0.0, 1.0), Project.TEXT_TRACK_ID))
        p.rejected(UpdateText(text("nope", 0.0, 1.0)))
        p.rejected(AddText(text("t1", 0.0, 1.0, "x".repeat(501)), Project.TEXT_TRACK_ID))
        assertTrue(runCatching { text("t", 0.0, 0.01) }.isFailure)
    }

    @Test
    fun hiddenTextTrackAndTextPastTheEndAreNotRendered() {
        val p = projectWith(3.0)
            .applyOk(AddText(text("t1", 1.0, 5.0), Project.TEXT_TRACK_ID))
            .applyOk(AddText(text("t2", 9.0, 1.0), Project.TEXT_TRACK_ID))
        val plan = RenderSegments.plan(p)
        assertEquals(listOf("t1"), plan.texts.map { it.id })
        assertEquals(2_000_000L, plan.texts.single().durationUs)
        val hidden = p.applyOk(SetTrackFlags(Project.TEXT_TRACK_ID, enabled = false, locked = false))
        assertTrue(RenderSegments.plan(hidden).texts.isEmpty())
    }

    @Test
    fun subtitleImportAppendsOrReplaces() {
        val cues = Srt.parse("1\n00:00:01,000 --> 00:00:02,000\nSatu\n\n2\n00:00:03,000 --> 00:00:04,500\nDua").cues
        val p = projectWith(10.0).applyOk(ImportSubtitles(Srt.toTextItems(cues, listOf("s1", "s2")), replace = false))
        val track = p.track(Project.SUBTITLE_TRACK_ID)!!
        assertEquals(listOf("Satu", "Dua"), track.texts.map { it.text })
        assertEquals(TextItem.SUBTITLE_Y, track.texts[0].y)
        val replaced = p.applyOk(ImportSubtitles(Srt.toTextItems(cues.take(1), listOf("s1")), replace = true))
        assertEquals(1, replaced.track(Project.SUBTITLE_TRACK_ID)!!.texts.size)
        p.rejected(ImportSubtitles(Srt.toTextItems(cues.take(1), listOf("s1")), replace = false))
        // Track order is stable: main, text, subtitle, music, voice.
        val full = p.applyOk(AddAudioClip(audioAsset("m", 2.0), "am", Project.VOICE_TRACK_ID, 0L))
            .applyOk(AddText(text("t1", 0.0, 1.0), Project.TEXT_TRACK_ID))
        assertEquals(listOf(Project.MAIN_TRACK_ID, Project.TEXT_TRACK_ID, Project.SUBTITLE_TRACK_ID, Project.VOICE_TRACK_ID), full.tracks.map { it.id })
    }
}

class SrtTest {
    @Test
    fun parsesMessyRealWorldFiles() {
        val raw = "﻿1\r\n00:00:01,500 --> 00:00:03,000 X1:10 Y1:20\r\n<i>Halo</i> dunia\r\nbaris dua\r\n\r\n" +
            "00:00:04.25 --> 00:00:05.000\r\n{\\an8}Tanpa indeks\r\n\r\n" +
            "3\r\n0:01:00,000 --> 0:01:02,000\r\nJam pendek\r\n" + // no blank line before the next cue
            "4\r\n00:01:03,000 --> 00:01:04,000\r\nTerakhir"
        val r = Srt.parse(raw)
        assertEquals(0, r.skipped)
        assertEquals(listOf("Halo dunia\nbaris dua", "Tanpa indeks", "Jam pendek", "Terakhir"), r.cues.map { it.text })
        assertEquals(1_500_000L, r.cues[0].startUs)
        assertEquals(4_250_000L, r.cues[1].startUs)
        assertEquals(60_000_000L, r.cues[2].startUs)
    }

    @Test
    fun malformedBlocksAreSkippedNotGuessed() {
        val r = Srt.parse("1\n00:00:05,000 --> 00:00:02,000\nTerbalik\n\n2\n00:00:06,000 --> 00:00:07,000\n\n\n3\n00:99:00,000 --> 00:99:01,000\nMenit salah\n\nsampah\n")
        assertTrue(r.cues.isEmpty())
        assertEquals(3, r.skipped)
        assertTrue(Srt.parse("").cues.isEmpty())
        assertTrue(Srt.parse("bukan subtitle sama sekali").cues.isEmpty())
    }

    @Test
    fun formatRoundTrips() {
        val cues = listOf(SubtitleCue(3_661_250_000L, 3_662_000_000L, "Satu\nDua"), SubtitleCue(0L, 900_000L, "Awal"))
        val text = Srt.format(cues)
        assertTrue(text.startsWith("1\n00:00:00,000 --> 00:00:00,900\nAwal\n\n2\n01:01:01,250 --> 01:01:02,000\nSatu\nDua\n"))
        assertEquals(cues.sortedBy { it.startUs }, Srt.parse(text).cues)
    }
}

class MotionTest {
    @Test
    fun fadeGainRampsAndNeverOverlaps() {
        assertEquals(0f, Motion.fadeGain(0L, 4_000_000L, 1_000_000L, 1_000_000L))
        assertEquals(0.5f, Motion.fadeGain(500_000L, 4_000_000L, 1_000_000L, 1_000_000L))
        assertEquals(1f, Motion.fadeGain(2_000_000L, 4_000_000L, 1_000_000L, 1_000_000L))
        assertEquals(0.5f, Motion.fadeGain(3_500_000L, 4_000_000L, 1_000_000L, 1_000_000L))
        assertEquals(0f, Motion.fadeGain(4_000_000L, 4_000_000L, 1_000_000L, 1_000_000L))
        // Fades longer than the clip are capped at half each.
        assertEquals(1f, Motion.fadeGain(500_000L, 1_000_000L, 9_000_000L, 9_000_000L))
        assertEquals(1f, Motion.fadeGain(123L, 1_000_000L, 0L, 0L))
    }

    @Test
    fun transitionStateFollowsEnterAndExit() {
        val seg = RenderSegment("c", "u", 0L, 4000L, 1f, enter = Transition(TransitionKind.FADE, 1_000_000L), exit = Transition(TransitionKind.SLIDE, 1_000_000L))
        assertEquals(0f, Motion.transition(seg, 0L).visibility)
        assertEquals(TransitionKind.FADE, Motion.transition(seg, 500_000L).kind)
        assertEquals(0.5f, Motion.transition(seg, 500_000L).visibility)
        assertTrue(Motion.transition(seg, 2_000_000L).isIdle)
        val out = Motion.transition(seg, 3_500_000L)
        assertEquals(TransitionKind.SLIDE, out.kind)
        assertFalse(out.entering)
        assertEquals(0.5f, out.visibility)
        assertEquals(0f, Motion.transition(seg, 4_000_000L).visibility)
    }

    @Test
    fun textPoseAnimatesInAndOut() {
        val item = text("t", 1.0, 2.0).copy(animation = TextAnimation.SLIDE_UP)
        assertEquals(0f, Motion.textPose(item, 1_000_000L).alpha)
        assertTrue(Motion.textPose(item, 1_000_000L).offsetY > 0f)
        assertEquals(Motion.TextPose(1f, 0f, 1f), Motion.textPose(item, 2_000_000L))
        assertEquals(0f, Motion.textPose(item, 3_000_000L).alpha)
        assertEquals(1f, Motion.textPose(item.copy(animation = TextAnimation.NONE), 1_000_000L).alpha)
        assertEquals(0.7f, Motion.textPose(item.copy(animation = TextAnimation.POP), 1_000_000L).scale)
    }
}

class StudioCodecTest {
    @Test
    fun everyNewFieldSurvivesARoundTrip() {
        val p = projectWith(6.0, 3.0)
            .applyOk(AddClips(listOf(imageAsset("img")), listOf("ci")))
            .applyOk(SetClipSpeed("c0", 1500))
            .applyOk(SetClipGrade("c0", ColorGrade(0.1f, -0.2f, 0.3f, -0.4f, 0.5f, -0.6f, 0.7f, -0.8f, 0.9f, -1f)))
            .applyOk(SetClipFx("c0", ClipFx(0.1f, 0.2f, 0.3f, 0.4f)))
            .applyOk(SetClipTransform("c1", Transform(1.25f, -30f, 0.1f, -0.2f, flipH = true, flipV = false, opacity = 0.8f), Crop(0.1f, 0.05f, 0.2f, 0f)))
            .applyOk(SetClipTransitions("c1", Transition(TransitionKind.BLUR, 800_000L), Transition(TransitionKind.SLIDE)))
            .applyOk(SetClipAudio("c1", 0.4f, false, 250_000L, 350_000L))
            .applyOk(AddAudioClip(audioAsset("m", 5.0), "am", Project.MUSIC_TRACK_ID, 500_000L))
            .applyOk(AddText(text("t1", 0.5, 2.0, "Judul \"kutip\"\nbaris 2").copy(animation = TextAnimation.POP, style = TextStyle(FontChoice.SERIF, 0.1f, false, true, 0xFF112233, TextAlign.LEFT, 0xFFABCDEF, 0.1f, 0.2f, 0x80000000)), Project.TEXT_TRACK_ID))
            .applyOk(AddText(text("s1", 1.0, 1.0), Project.SUBTITLE_TRACK_ID))
            .applyOk(SetTrackFlags(Project.SUBTITLE_TRACK_ID, enabled = false, locked = true))
            .applyOk(SetCanvas(Canvas(AspectRatio.PORTRAIT_9_16, 60)))
        val decoded = ProjectCodec.decode(ProjectCodec.encode(p))
        assertEquals(p, (decoded as ProjectCodec.DecodeResult.Success).project)
    }

    @Test
    fun v2ProjectFileMigratesWithNeutralDefaults() {
        val v2 = """{"format":"margaretha-editor-project","schemaVersion":2,"id":"p","name":"Lama","createdAtMs":1,"updatedAtMs":2,
            "canvas":{"aspect":"LANDSCAPE_16_9","frameRate":30},
            "assets":[{"id":"a","uri":"content://a","displayName":"a.mp4","mimeType":"video/mp4","durationUs":5000000,"width":1920,"height":1080,"rotationDegrees":0,"hasVideo":true,"hasAudio":true,"frameRate":30.0}],
            "tracks":[{"id":"track-main","kind":"MAIN_VIDEO","magnetic":true,"clips":[{"id":"c","assetId":"a","sourceInUs":0,"sourceOutUs":5000000,"timelineStartUs":0,"volume":0.5,"muted":false,"colorLook":"WARM"}]}]}"""
        val p = (ProjectCodec.decode(v2) as ProjectCodec.DecodeResult.Success).project
        val clip = p.mainTrack.clips.single()
        assertEquals(ColorLook.WARM, clip.colorLook)
        assertEquals(1000, clip.speedPermille)
        assertTrue(clip.grade.isNeutral && clip.fx.isNone && clip.transform.isIdentity && clip.enter.isNone)
        assertEquals(MediaKind.VIDEO, p.assets["a"]!!.kind)
        assertTrue(p.mainTrack.enabled && !p.mainTrack.locked)
    }

    @Test
    fun inconsistentFilesAreRefusedNotRepaired() {
        val p = projectWith(6.0).applyOk(AddAudioClip(audioAsset("m", 5.0), "am", Project.MUSIC_TRACK_ID, 0L))
        val text = ProjectCodec.encode(p)
        fun fails(mutated: String) = assertTrue(ProjectCodec.decode(mutated) is ProjectCodec.DecodeResult.Failure)
        fails(text.replace("\"speedPermille\":1000", "\"speedPermille\":99999"))
        fails(text.replace("\"kind\":\"AUDIO\",\"magnetic\"", "\"kind\":\"BANANA\",\"magnetic\""))
        fails(text.replace("\"id\":\"am\"", "\"id\":\"c0\""))
        val overlapping = p.replaceTrack(p.track(Project.MUSIC_TRACK_ID)!!.let { t -> t.copy(clips = t.clips + t.clips[0].copy(id = "dup", timelineStartUs = 1_000_000L)) })
        fails(ProjectCodec.encode(overlapping))
    }
}
