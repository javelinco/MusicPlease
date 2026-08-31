# Library Search Autofocus Design

## Goal

Make library search a single-step action: tapping the search icon opens the search field, places the text cursor in it, and brings up the software keyboard so the user can type immediately.

## Current behavior

The search icon only changes `LibraryScreenState.searchOpen`. The newly rendered `OutlinedTextField` is visible, but Compose is never asked to focus it or show the keyboard.

## Design

`LibraryScreen` will own a remembered `FocusRequester` and attach it to the search text field. A `LaunchedEffect` keyed to `state.searchOpen` will request focus and then explicitly request the software keyboard when search transitions open.

Focus and keyboard state remain UI concerns. The ViewModel continues to own only durable search state: whether search is open and the current query.

The existing close-search behavior remains unchanged. Removing the focused field from composition naturally clears focus and allows Android to dismiss the keyboard.

## Reliability and testing

Explicitly calling the software keyboard controller is more dependable across Samsung keyboard configurations than relying on focus alone. A Robolectric Compose test will click the search icon and assert that the search field becomes focused. Keyboard visibility itself is not stable to assert in a host-side test, so compilation plus the explicit `show()` call forms the boundary for that portion of the behavior.

## Scope

This change applies only to the existing Library search control. It does not alter search matching, results, layout, or keyboard IME actions.
