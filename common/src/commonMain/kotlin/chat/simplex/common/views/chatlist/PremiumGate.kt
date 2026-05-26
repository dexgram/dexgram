package chat.simplex.common.views.chatlist

import chat.simplex.common.model.ChatController.appPrefs
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * App-wide premium subscription gate.
 *
 * Every premium-only feature should call [requirePremium] before executing its
 * action. If the user is on the free tier, the global upgrade dialog (rendered
 * inside ChatListView) is shown instead of running the action.
 *
 * `appPrefs.premiumActive` is the single source of truth and is set/cleared by
 * the DexgramApi subscription check on app launch and after a Google Play
 * purchase / 16-digit code activation.
 */
object PremiumGate {

  /** True when the user has an active Dexgram Pro subscription. */
  fun isActive(): Boolean = appPrefs.premiumActive.get()

  /** Global request flag — ChatListView observes this and opens the upgrade dialog. */
  val upgradeRequested = MutableStateFlow(false)

  /**
   * Runs [then] if the user is premium. Otherwise asks ChatListView to show
   * the upgrade dialog and does NOT run the action.
   */
  inline fun requirePremium(then: () -> Unit) {
    if (isActive()) {
      then()
    } else {
      upgradeRequested.value = true
    }
  }
}
