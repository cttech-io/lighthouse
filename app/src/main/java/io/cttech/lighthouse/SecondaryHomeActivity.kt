package io.cttech.lighthouse

/**
 * Lighthouse as the home screen of an external display (desktop windowing). Android starts one of these per display
 * that has no home of its own, so each screen gets its own task instead of fighting over the phone's single one.
 */
class SecondaryHomeActivity : MainActivity()
