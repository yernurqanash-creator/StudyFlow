package kz.studyflow.app

import android.app.Activity
import android.app.AlertDialog
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.Spinner
import android.widget.TextView
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

private data class StudyTask(
    val id: String,
    val title: String,
    val subject: String,
    val priority: Int,
    var done: Boolean = false
)

class MainActivity : Activity() {
    private val bg = Color.rgb(246, 247, 252)
    private val ink = Color.rgb(27, 31, 47)
    private val subtle = Color.rgb(115, 123, 141)
    private val accent = Color.rgb(103, 87, 237)
    private val mint = Color.rgb(96, 212, 172)
    private val stroke = Color.rgb(230, 232, 243)
    private val tasks = mutableListOf<StudyTask>()
    private var filter = 0 // 0 — all; 1 — active; 2 — completed
    private lateinit var screen: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = bg
        window.navigationBarColor = bg
        @Suppress("DEPRECATION")
        window.decorView.systemUiVisibility =
            View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR or View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
        restoreTasks()
        screen = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(bg)
        }
        setContentView(screen)
        render()
    }

    private fun dp(value: Int): Int = (resources.displayMetrics.density * value + 0.5f).toInt()

    private fun rounded(color: Int, radius: Int = 20, borderColor: Int? = null): GradientDrawable =
        GradientDrawable().apply {
            setColor(color)
            cornerRadius = dp(radius).toFloat()
            if (borderColor != null) setStroke(dp(1), borderColor)
        }

    private fun label(text: String, size: Float, color: Int = ink, bold: Boolean = false): TextView =
        TextView(this).apply {
            this.text = text
            textSize = size
            setTextColor(color)
            if (bold) typeface = Typeface.create("sans-serif", Typeface.BOLD)
            includeFontPadding = false
        }

    private fun LinearLayout.addSpace(height: Int) {
        addView(View(this@MainActivity), LinearLayout.LayoutParams(1, dp(height)))
    }

    private fun padded(horizontal: Int = 18, vertical: Int = 16) = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(horizontal), dp(vertical), dp(horizontal), dp(vertical))
    }

    private fun render() {
        screen.removeAllViews()
        val scroll = ScrollView(this).apply {
            fillViewport = true
            isVerticalScrollBarEnabled = false
        }
        val page = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(22), dp(20), dp(32))
        }
        scroll.addView(page)
        screen.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))

        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val titleGroup = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        titleGroup.addView(label("StudyFlow", 29f, ink, true))
        titleGroup.addSpace(7)
        val date = SimpleDateFormat("d MMMM, EEEE", Locale.forLanguageTag("kk-KZ")).format(Date())
        titleGroup.addView(label(date, 13f, subtle))
        header.addView(titleGroup, LinearLayout.LayoutParams(0, -2, 1f))
        val plus = label("+  Қосу", 14f, Color.WHITE, true).apply {
            gravity = Gravity.CENTER
            background = rounded(accent, 14)
            setPadding(dp(18), dp(13), dp(18), dp(13))
            setOnClickListener { openAddDialog() }
        }
        header.addView(plus)
        page.addView(header)
        page.addSpace(24)

        val finished = tasks.count { it.done }
        val total = tasks.size
        val pct = if (total == 0) 0 else finished * 100 / total
        val banner = padded(21, 22).apply {
            background = GradientDrawable(GradientDrawable.Orientation.TL_BR,
                intArrayOf(Color.rgb(111, 92, 243), Color.rgb(75, 61, 179))).apply {
                cornerRadius = dp(24).toFloat()
            }
        }
        banner.addView(label("БҮГІНГІ ПРОГРЕСС", 11f, Color.rgb(231, 225, 255), true))
        banner.addSpace(14)
        banner.addView(label("$pct% аяқталды", 27f, Color.WHITE, true))
        banner.addSpace(8)
        banner.addView(label("$finished / $total тапсырма орындалды", 14f, Color.rgb(234, 230, 255)))
        banner.addSpace(20)
        val progress = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
            max = 100
            progress = pct
            progressTintList = ColorStateList.valueOf(mint)
            progressBackgroundTintList = ColorStateList.valueOf(Color.rgb(140, 128, 229))
        }
        banner.addView(progress, LinearLayout.LayoutParams(-1, dp(9)))
        page.addView(banner)
        page.addSpace(26)

        val stats = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        stats.addView(statCard("Барлығы", total.toString(), Color.rgb(237, 233, 255)),
            LinearLayout.LayoutParams(0, -2, 1f).apply { rightMargin = dp(9) })
        stats.addView(statCard("Орындалды", finished.toString(), Color.rgb(224, 249, 240)),
            LinearLayout.LayoutParams(0, -2, 1f).apply { leftMargin = dp(9) })
        page.addView(stats)
        page.addSpace(30)

        page.addView(label("Менің тапсырмаларым", 22f, ink, true))
        page.addSpace(16)
        val filterRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        val filters = listOf("Барлығы", "Белсенді", "Дайын")
        filters.forEachIndexed { index, name ->
            val selected = filter == index
            val chip = label(name, 13f, if (selected) Color.WHITE else subtle, selected).apply {
                gravity = Gravity.CENTER
                background = rounded(if (selected) accent else Color.WHITE, 12,
                    if (selected) null else stroke)
                setPadding(dp(8), dp(12), dp(8), dp(12))
                setOnClickListener { filter = index; render() }
            }
            filterRow.addView(chip, LinearLayout.LayoutParams(0, -2, 1f).apply {
                if (index != filters.lastIndex) rightMargin = dp(8)
            })
        }
        page.addView(filterRow)
        page.addSpace(16)

        val filtered = tasks.filter {
            when (filter) {
                1 -> !it.done
                2 -> it.done
                else -> true
            }
        }
        if (filtered.isEmpty()) {
            val empty = padded(20, 35).apply {
                gravity = Gravity.CENTER
                background = rounded(Color.WHITE, 20, stroke)
            }
            empty.addView(label("✦", 35f, accent))
            empty.addSpace(12)
            empty.addView(label("Әзірге тапсырма жоқ", 17f, ink, true))
            empty.addSpace(9)
            empty.addView(label("Жаңа тапсырма қосып, жұмысты баста!", 13f, subtle).apply {
                gravity = Gravity.CENTER
            })
            page.addView(empty)
        } else {
            filtered.forEach { task ->
                val taskCard = createTaskCard(task)
                page.addView(taskCard)
                page.addSpace(11)
            }
        }
        page.addSpace(18)
        page.addView(label("Кеңес: тапсырманы ұзақ бассаң, оны өшіре аласың.", 12f, subtle))
    }

    private fun statCard(caption: String, value: String, tint: Int): View =
        padded(17, 18).apply {
            background = rounded(tint, 18)
            addView(label(value, 28f, ink, true))
            addSpace(6)
            addView(label(caption, 13f, subtle))
        }

    private fun createTaskCard(task: StudyTask): View {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(13), dp(18), dp(14), dp(18))
            background = rounded(Color.WHITE, 18, stroke)
        }
        val checkbox = CheckBox(this).apply {
            isChecked = task.done
            buttonTintList = ColorStateList.valueOf(accent)
            setOnCheckedChangeListener { _, checked ->
                task.done = checked
                persistTasks()
                render()
            }
        }
        row.addView(checkbox, LinearLayout.LayoutParams(dp(44), dp(44)))
        val content = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        content.addView(label(task.title, 16f, if (task.done) subtle else ink, true).apply {
            if (task.done) paintFlags = paintFlags or android.graphics.Paint.STRIKE_THRU_TEXT_FLAG
        })
        content.addSpace(8)
        content.addView(label("${task.subject}   •   ${priorityLabel(task.priority)}", 12f, subtle))
        row.addView(content, LinearLayout.LayoutParams(0, -2, 1f))
        row.setOnClickListener {
            task.done = !task.done
            persistTasks()
            render()
        }
        row.setOnLongClickListener {
            AlertDialog.Builder(this)
                .setTitle("Тапсырманы өшіру")
                .setMessage("«${task.title}» тапсырмасын өшіресің бе?")
                .setNegativeButton("Болдырмау", null)
                .setPositiveButton("Өшіру") { _, _ ->
                    tasks.removeAll { it.id == task.id }
                    persistTasks()
                    render()
                }
                .show()
            true
        }
        return row
    }

    private fun priorityLabel(priority: Int): String = when (priority) {
        2 -> "Жоғары басымдық"
        1 -> "Орташа басымдық"
        else -> "Төмен басымдық"
    }

    private fun openAddDialog() {
        val body = padded(22, 8)
        val taskTitle = EditText(this).apply {
            hint = "Мысалы: Kotlin жобасын қорғау"
            isSingleLine = true
            textSize = 15f
        }
        val subject = EditText(this).apply {
            hint = "Пәні немесе санаты"
            isSingleLine = true
            textSize = 15f
        }
        val spinner = Spinner(this).apply {
            adapter = ArrayAdapter(
                this@MainActivity,
                android.R.layout.simple_spinner_dropdown_item,
                listOf("Төмен басымдық", "Орташа басымдық", "Жоғары басымдық")
            )
            setSelection(1)
        }
        body.addView(taskTitle, LinearLayout.LayoutParams(-1, dp(54)))
        body.addSpace(12)
        body.addView(subject, LinearLayout.LayoutParams(-1, dp(54)))
        body.addSpace(14)
        body.addView(label("Маңыздылығы", 13f, subtle))
        body.addSpace(5)
        body.addView(spinner)

        val dialog = AlertDialog.Builder(this)
            .setTitle("Жаңа тапсырма")
            .setView(body)
            .setNegativeButton("Болдырмау", null)
            .setPositiveButton("Қосу", null)
            .create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val name = taskTitle.text.toString().trim()
                if (name.isEmpty()) {
                    taskTitle.error = "Тапсырма атауын жаз"
                    return@setOnClickListener
                }
                tasks.add(0, StudyTask(
                    id = UUID.randomUUID().toString(),
                    title = name,
                    subject = subject.text.toString().trim().ifEmpty { "Жалпы" },
                    priority = spinner.selectedItemPosition
                ))
                persistTasks()
                filter = 0
                dialog.dismiss()
                render()
            }
        }
        dialog.show()
    }

    private fun persistTasks() {
        val array = JSONArray()
        tasks.forEach { item ->
            array.put(JSONObject().apply {
                put("id", item.id)
                put("title", item.title)
                put("subject", item.subject)
                put("priority", item.priority)
                put("done", item.done)
            })
        }
        getSharedPreferences("studyflow", MODE_PRIVATE)
            .edit().putString("tasks", array.toString()).apply()
    }

    private fun restoreTasks() {
        val raw = getSharedPreferences("studyflow", MODE_PRIVATE).getString("tasks", "[]") ?: "[]"
        try {
            val array = JSONArray(raw)
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                tasks.add(StudyTask(
                    id = obj.optString("id", UUID.randomUUID().toString()),
                    title = obj.optString("title"),
                    subject = obj.optString("subject", "Жалпы"),
                    priority = obj.optInt("priority", 1),
                    done = obj.optBoolean("done", false)
                ))
            }
        } catch (_: Exception) {
            // Damaged local data must not crash the app.
        }
    }
}
