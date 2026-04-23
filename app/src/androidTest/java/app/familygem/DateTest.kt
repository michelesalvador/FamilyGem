package app.familygem

import android.content.Context
import android.widget.EditText
import androidx.test.ext.junit.rules.activityScenarioRule
import androidx.test.internal.runner.junit4.AndroidJUnit4ClassRunner
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Tests some dates. */
@RunWith(AndroidJUnit4ClassRunner::class)
class DateTest {

    @get:Rule
    val activityScenarioRule = activityScenarioRule<LauncherActivity>()

    private lateinit var testContext: Context // Test context (to access files in /assets)
    private lateinit var appContext: Context // Context of app.familygem

    @Before
    fun setup() {
        testContext = InstrumentationRegistry.getInstrumentation().context
        appContext = InstrumentationRegistry.getInstrumentation().targetContext
    }

    class DateWrapper(val gedcomDate: String)

    @Test
    fun testSomeDates() = runTest {
        val dateList = ArrayList<DateWrapper>()
        fun add(date: String) {
            dateList.add(DateWrapper(date))
        }

        add("31 DEc 1998")
        add("CAL 23 AUG")
        add("From 1234/35 to 21 DEc 1699/00")
        add("BC")
        add("CAL 007/08 B.C.")
        add("BET Jun 2000/99 b.C. And 7/8 b.C.")

        for (date in dateList) {
            println("'" + date.gedcomDate + "'")
            val layout = DateEditorLayout(appContext, null)
            assertNotNull(layout)
            val editText = EditText(appContext)
            assertNotNull(editText)
            editText.setText(date.gedcomDate)
            layout.testInitialize(editText)
            layout.testGenerateDate()
            println("\t" + editText.text)
            println("\t" + layout.testDateConverter.writeDateLong())
        }
    }
}
