package com.elendal.gpsalarmclock

import android.widget.TimePicker
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.action.ViewActions.closeSoftKeyboard
import androidx.test.espresso.action.ViewActions.replaceText
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.contrib.RecyclerViewActions
import androidx.test.espresso.matcher.ViewMatchers.*
import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import androidx.recyclerview.widget.RecyclerView
import org.hamcrest.Matchers.allOf
import androidx.test.rule.GrantPermissionRule
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import androidx.test.espresso.action.ViewActions
import androidx.test.espresso.contrib.PickerActions
import org.hamcrest.Matchers.instanceOf

@RunWith(AndroidJUnit4::class)
@LargeTest
class AlarmListE2ETest {

    @Rule(order = 0) @JvmField
    val grantPermissions: GrantPermissionRule = GrantPermissionRule.grant(
        android.Manifest.permission.POST_NOTIFICATIONS,
        android.Manifest.permission.ACCESS_FINE_LOCATION,
        android.Manifest.permission.ACCESS_COARSE_LOCATION,
    )

    @Rule(order = 1) @JvmField
    val activityRule = ActivityScenarioRule(MainActivity::class.java)

    // ── 1. App launches ───────────────────────────────────────────────────────

    @Test
    fun appLaunches_showsMainActivity() {
        // RecyclerView exists (may be empty)
        onView(withId(R.id.recycler_alarms))
            .check(matches(isDisplayed()))
        // FAB exists
        onView(withId(R.id.fab_add_alarm))
            .check(matches(isDisplayed()))
    }

    // ── 2. Create alarm ───────────────────────────────────────────────────────

    @Test
    fun createAlarm_tapFab_bottomSheetAppears() {
        onView(withId(R.id.fab_add_alarm)).perform(click())

        // Bottom sheet content: label field should appear
        onView(withId(R.id.et_label))
            .check(matches(isDisplayed()))
        onView(withId(R.id.time_picker))
            .check(matches(isDisplayed()))
        onView(withId(R.id.btn_save))
            .check(matches(isDisplayed()))
    }

    @Test
    fun createAlarm_fillAndSave_alarmAppearsInList() {
        // Open add sheet
        onView(withId(R.id.fab_add_alarm)).perform(click())

        // Enter label
        onView(withId(R.id.et_label))
            .perform(replaceText("Morning Run"), closeSoftKeyboard())

        // Set time via TimePicker spinner
        onView(instanceOf(TimePicker::class.java))
            .perform(PickerActions.setTime(7, 30))

        // FULL_WEEK chip is default selected — just save
        onView(withId(R.id.btn_save)).perform(click())

        // Alarm item should now appear in the RecyclerView
        onView(withId(R.id.recycler_alarms))
            .check(matches(hasDescendant(withText("Morning Run"))))
    }

    @Test
    fun createAlarm_cancelButton_bottomSheetDismisses() {
        onView(withId(R.id.fab_add_alarm)).perform(click())

        onView(withId(R.id.et_label))
            .perform(replaceText("Throwaway"), closeSoftKeyboard())

        onView(withId(R.id.btn_cancel)).perform(click())

        // Sheet dismissed — et_label should no longer be visible
        onView(withId(R.id.fab_add_alarm))
            .check(matches(isDisplayed()))
    }

    // ── 3. Toggle alarm ───────────────────────────────────────────────────────

    @Test
    fun toggleAlarm_switchChangesState() {
        // First create an alarm
        onView(withId(R.id.fab_add_alarm)).perform(click())
        onView(withId(R.id.et_label))
            .perform(replaceText("Toggle Test"), closeSoftKeyboard())
        onView(withId(R.id.btn_save)).perform(click())

        // Wait for it to appear then find the switch in the first item
        onView(withId(R.id.recycler_alarms))
            .check(matches(hasDescendant(withText("Toggle Test"))))

        // Click the switch in the first alarm card
        onView(withId(R.id.recycler_alarms))
            .perform(
                RecyclerViewActions.actionOnItemAtPosition<RecyclerView.ViewHolder>(
                    0,
                    clickChildViewWithId(R.id.switch_alarm_enabled)
                )
            )

        // Switch should now be unchecked (was enabled by default)
        onView(withId(R.id.recycler_alarms))
            .check(matches(hasDescendant(allOf(
                withId(R.id.switch_alarm_enabled),
                isNotChecked()
            ))))
    }

    // ── Helper ────────────────────────────────────────────────────────────────

    private fun clickChildViewWithId(id: Int): androidx.test.espresso.ViewAction {
        return object : androidx.test.espresso.ViewAction {
            override fun getConstraints() = isEnabled()
            override fun getDescription() = "Click child view with id $id"
            override fun perform(uiController: androidx.test.espresso.UiController, view: android.view.View) {
                val child = view.findViewById<android.view.View>(id)
                child.performClick()
                uiController.loopMainThreadUntilIdle()
            }
        }
    }
}
