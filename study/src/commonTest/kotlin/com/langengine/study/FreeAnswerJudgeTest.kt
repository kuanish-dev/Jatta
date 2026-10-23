package com.langengine.study

import com.langengine.study.judge.DiffKind
import com.langengine.study.judge.DiffPart
import com.langengine.study.judge.FreeAnswerJudge
import com.langengine.study.judge.JudgeRequest
import com.langengine.study.judge.Judgment
import com.langengine.study.judge.ModelState
import com.langengine.study.judge.OnDeviceModel
import com.langengine.study.judge.WordDiff
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class FreeAnswerJudgeTest {
    private val judge = FreeAnswerJudge(ReplyingModel { error("not called") }, StudyConfig())

    @Test
    fun readsAVerdictInTheAgreedFormat() {
        assertEquals(Judgment(true, null), judge.parse("VERDICT: CORRECT\nFIXED: I met him.", "I met him."))
        assertEquals(
            Judgment(false, "I didn't see your message."),
            judge.parse("**VERDICT:** WRONG\n**FIXED:** \"I didn't see your message.\"", "I didn't saw your message."),
        )
    }

    @Test
    fun throwsAwayRepliesItCannotTrust() {
        // says wrong but changes nothing
        assertNull(judge.parse("VERDICT: WRONG\nFIXED: I didn't see it.", "I didn't see it."))
        // rewrites the sentence instead of fixing it (М22)
        assertNull(judge.parse("VERDICT: WRONG\nFIXED: Unfortunately, your message never reached me.", "I didn't saw your message."))
        // no verdict at all
        assertNull(judge.parse("The sentence looks fine to me!", "I met him."))
    }

    @Test
    fun anAnswerEqualToTheModelAnswerDoesNotNeedTheModel() = runTest {
        val judgment = judge.judge(JudgeRequest("Translate", reference = "I didn't see it.", response = "I did not see it"))
        assertEquals(Judgment(true, null), judgment)
    }

    @Test
    fun theModelGetsTheTaskAndTheSentence() = runTest {
        var prompt = ""
        val judge = FreeAnswerJudge(ReplyingModel { prompt = it; "VERDICT: CORRECT" }, StudyConfig())
        judge.judge(JudgeRequest("Translate into English: «Я не видел»", "I didn't see it.", "I haven't seen it."))
        assertTrue("Translate into English: «Я не видел»" in prompt && "I haven't seen it." in prompt)
    }

    @Test
    fun wordDiffMarksOnlyWhatChanged() {
        assertEquals(
            listOf(
                DiffPart("I", DiffKind.Same),
                DiffPart("didn't", DiffKind.Same),
                DiffPart("saw", DiffKind.Removed),
                DiffPart("see", DiffKind.Added),
                DiffPart("your", DiffKind.Same),
                DiffPart("message.", DiffKind.Same),
            ),
            WordDiff.parts("I didn't saw your message.", "I didn't see your message."),
        )
        assertEquals(1, WordDiff.edits("I have went home.", "I went home."))
    }
}

/** A stand-in for the on-device model that replies with whatever the test wants. */
class ReplyingModel(private val reply: (String) -> String?) : OnDeviceModel {
    override suspend fun state() = ModelState.Ready
    override suspend fun download(onProgress: (Long) -> Unit) = true
    override suspend fun generate(prompt: String) = reply(prompt)
}
