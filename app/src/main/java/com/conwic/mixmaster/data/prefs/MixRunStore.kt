package com.conwic.mixmaster.data.prefs

import android.content.Context
import com.conwic.mixmaster.domain.ComponentAmount
import com.conwic.mixmaster.domain.MixPart
import com.conwic.mixmaster.domain.MixingStep
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.json.JSONArray
import org.json.JSONObject

/**
 * A mixing run as it is written down: everything the mixing screen needs to draw itself without
 * the calculator that started it.
 */
data class SavedMixRun(
    val title: String,
    val batchSize: String,
    val mixSeconds: Int,
    /** How long the batch stays workable once it is mixed, in minutes. 0 when unknown. */
    val potLifeMinutes: Int = 0,
    val parts: List<MixPart>,
    val steps: List<MixingStep>,
    /** The job it was started for, so what gets mixed can be written against it. 0 for none. */
    val projectId: Long = 0L,
    val roomId: Long = 0L,
    val solutionId: Long = 0L,
    /** The project and the room, as the calculator named them. */
    val jobLabel: String = "",
)

/**
 * How far a run has got.
 *
 * All facts about the clock, none about the recipe, and the times are moments rather than
 * counts — a countdown picked up again half an hour later is still the same deadline.
 */
data class MixProgress(
    val stepIndex: Int = 0,
    /** The screen's own phase, by name. This store does not care what the names mean. */
    val phase: String = "READY",
    val deadline: Long = 0L,
    val stepStartedAt: Long = 0L,
    val startedAt: Long = 0L,
    val durations: List<Long> = emptyList(),
    val doneSteps: Int = 0,
    val seconds: Int = 0,
    val finished: Boolean = false,
    val finishedAt: Long = 0L,
    /** Whether what was mixed has been written against the project. Once only. */
    val recorded: Boolean = false,
)

/**
 * The run under way, kept where it outlives everything that can go wrong around it.
 *
 * A mix used to live in the calculator's screen, which meant it lived exactly as long as that
 * screen did: the app lock closing over it, Android reclaiming the app in somebody's pocket, or
 * — on this phone, most often — a new build being installed, and the batch was gone. The alarm
 * still went off and brought the app up, and the app came up on the front door with nothing to
 * come back to, which is the complaint that started this.
 *
 * So it is written to disk the moment mixing starts, updated when something actually happens
 * (never on the tick), and read back by whoever picks the run up next — the screen, or the
 * receiver that re-books the alarm after a restart. SharedPreferences rather than DataStore for
 * the same reason [LanguageStore] uses it: a broadcast receiver has nowhere to suspend.
 */
object MixRunStore {

    private const val FILE = "mixmaster_mix_run"
    private const val KEY_RUN = "run"
    private const val KEY_PROGRESS = "progress"
    /**
     * Bumped if the shape below ever changes in a way that can be half-understood. Fields that
     * are simply absent in an older run are not that: the job was added here, and a run written
     * before it reads back with no job, which is exactly what it had. Bumping for that would
     * throw away the batch somebody is standing over while the new build installs.
     */
    private const val VERSION = 1

    private val state = MutableStateFlow<SavedMixRun?>(null)
    private var loaded = false

    private fun prefs(context: Context) = context.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    /**
     * The run under way, if there is one, as something the app can watch.
     *
     * Seeded from disk the first time it is asked for, which is what makes a run that outlived
     * the process turn up again on its own.
     */
    fun active(context: Context): StateFlow<SavedMixRun?> {
        if (!loaded) {
            loaded = true
            state.value = read(context)
        }
        return state
    }

    /** Mixing has started: this is the run, and it begins at the beginning. */
    fun start(context: Context, run: SavedMixRun) {
        val fresh = MixProgress(startedAt = System.currentTimeMillis())
        runCatching {
            prefs(context).edit()
                .putString(KEY_RUN, runJson(run).toString())
                .putString(KEY_PROGRESS, progressJson(fresh).toString())
                .apply()
        }
        loaded = true
        state.value = run
    }

    /**
     * How far it has got.
     *
     * Deliberately does not tell anybody: the only screen that would listen is the one that
     * just said it, and waking it for its own news would reset the very clock it is reporting.
     */
    fun saveProgress(context: Context, progress: MixProgress) {
        runCatching {
            prefs(context).edit().putString(KEY_PROGRESS, progressJson(progress).toString()).apply()
        }
    }

    fun progress(context: Context): MixProgress = runCatching {
        val raw = prefs(context).getString(KEY_PROGRESS, null) ?: return@runCatching MixProgress()
        readProgress(JSONObject(raw))
    }.getOrDefault(MixProgress())

    /** The run is over — closed, or finished and read. */
    fun clear(context: Context) {
        runCatching { prefs(context).edit().remove(KEY_RUN).remove(KEY_PROGRESS).apply() }
        loaded = true
        state.value = null
    }

    /** What is on disk, readable from anywhere, including a receiver at boot. */
    fun read(context: Context): SavedMixRun? = runCatching {
        val raw = prefs(context).getString(KEY_RUN, null) ?: return@runCatching null
        val json = JSONObject(raw)
        if (json.optInt("v") != VERSION) return@runCatching null
        val run = SavedMixRun(
            title = json.optString("title"),
            batchSize = json.optString("batchSize"),
            mixSeconds = json.optInt("mixSeconds"),
            potLifeMinutes = json.optInt("potLifeMinutes"),
            parts = json.optJSONArray("parts").map { readPart(it) },
            steps = json.optJSONArray("steps").map { step ->
                MixingStep(
                    amounts = step.optJSONArray("amounts").map { readAmount(it) },
                    isPartBatch = step.optBoolean("part"),
                )
            },
            projectId = json.optLong("projectId"),
            roomId = json.optLong("roomId"),
            solutionId = json.optLong("solutionId"),
            jobLabel = json.optString("jobLabel"),
        )
        // A run with no batches in it is not a run, and would leave the screen with nothing to
        // show and no way out.
        if (run.steps.isEmpty()) null else run
    }.getOrNull()

    private fun runJson(run: SavedMixRun) = JSONObject().apply {
        put("v", VERSION)
        put("title", run.title)
        put("batchSize", run.batchSize)
        put("mixSeconds", run.mixSeconds)
        put("potLifeMinutes", run.potLifeMinutes)
        put("projectId", run.projectId)
        put("roomId", run.roomId)
        put("solutionId", run.solutionId)
        put("jobLabel", run.jobLabel)
        put("parts", JSONArray().apply { run.parts.forEach { put(partJson(it)) } })
        put(
            "steps",
            JSONArray().apply {
                run.steps.forEach { step ->
                    put(
                        JSONObject().apply {
                            put("part", step.isPartBatch)
                            put("amounts", JSONArray().apply { step.amounts.forEach { put(amountJson(it)) } })
                        },
                    )
                }
            },
        )
    }

    private fun partJson(part: MixPart) = JSONObject().apply {
        put("productId", part.productId)
        put("label", part.label)
        put("ratioParts", part.ratioParts)
        put("packageSize", part.packageSize)
        put("packageUnit", part.packageUnit)
        put("packageType", part.packageType)
        put("density", part.densityKgPerL)
    }

    private fun readPart(json: JSONObject) = MixPart(
        productId = json.optLong("productId"),
        label = json.optString("label"),
        ratioParts = json.optDouble("ratioParts", 0.0),
        packageSize = json.optDouble("packageSize", 0.0),
        packageUnit = json.optString("packageUnit"),
        packageType = json.optString("packageType"),
        densityKgPerL = json.optDouble("density", 0.0),
    )

    private fun amountJson(amount: ComponentAmount) = JSONObject().apply {
        put("label", amount.label)
        put("grams", amount.grams)
        put("productId", amount.productId)
    }

    private fun readAmount(json: JSONObject) = ComponentAmount(
        label = json.optString("label"),
        grams = json.optDouble("grams", 0.0),
        productId = json.optLong("productId"),
    )

    private fun progressJson(progress: MixProgress) = JSONObject().apply {
        put("stepIndex", progress.stepIndex)
        put("phase", progress.phase)
        put("deadline", progress.deadline)
        put("stepStartedAt", progress.stepStartedAt)
        put("startedAt", progress.startedAt)
        put("durations", JSONArray().apply { progress.durations.forEach { put(it) } })
        put("doneSteps", progress.doneSteps)
        put("seconds", progress.seconds)
        put("finished", progress.finished)
        put("finishedAt", progress.finishedAt)
        put("recorded", progress.recorded)
    }

    private fun readProgress(json: JSONObject) = MixProgress(
        stepIndex = json.optInt("stepIndex"),
        phase = json.optString("phase", "READY"),
        deadline = json.optLong("deadline"),
        stepStartedAt = json.optLong("stepStartedAt"),
        startedAt = json.optLong("startedAt"),
        durations = json.optJSONArray("durations").let { array ->
            if (array == null) emptyList() else (0 until array.length()).map { array.optLong(it) }
        },
        doneSteps = json.optInt("doneSteps"),
        seconds = json.optInt("seconds"),
        finished = json.optBoolean("finished"),
        finishedAt = json.optLong("finishedAt"),
        recorded = json.optBoolean("recorded"),
    )

    /** Every object in an array, or nothing where there is no array. */
    private fun <T> JSONArray?.map(read: (JSONObject) -> T): List<T> {
        if (this == null) return emptyList()
        return (0 until length()).mapNotNull { index -> optJSONObject(index)?.let(read) }
    }
}
