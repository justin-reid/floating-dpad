package com.floatingdpad.input;

/**
 * Implemented by KeyInjectorService, which Shizuku forks into a process running as the
 * shell UID. That UID holds android.permission.INJECT_EVENTS, which is the entire reason
 * this app can deliver key events to another app at all.
 */
interface IKeyInjector {

    /**
     * Injects a single KeyEvent into the system input pipeline.
     *
     * oneway so that the repeat timer never blocks the UI thread on a binder round trip.
     * Oneway calls issued from one thread on one binder keep their order, which is all
     * the ordering guarantee a key stream needs.
     *
     * @param downTime uptimeMillis of the ACTION_DOWN that began this press. It must be
     *                 identical for the initial down, every repeat, and the final up, or
     *                 long-press detection in the receiving app breaks.
     */
    oneway void injectKey(int keyCode, int action, int repeatCount, long downTime) = 1;

    /**
     * Required by Shizuku's UserService contract; that fixed id is why every method
     * here needs an explicit one -- aidl refuses a mix of assigned and unassigned ids.
     */
    void destroy() = 16777114;
}
