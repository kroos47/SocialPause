package app.socialpause.engine;

import java.util.List;
import static app.socialpause.engine.EngineTests.*;
import static app.socialpause.engine.FocusResolver.Kind.*;
import static app.socialpause.engine.RulesEngine.MINUTE;

/** Visibility is notification metadata; a covering drawer must never pause usage. */
final class FocusResolverTests {
    static void run() {
        test("drawer closure exposes the same app without restarting its allowance", () -> {
            Clock c = new Clock(); FocusResolver resolver = new FocusResolver();
            var app = resolver.resolveFocus(true, true, List.of(window(X, APP, 1)), X, false);
            eq(new FocusResolver.Focus(X, true), app); c.focus(app.app()); c.minutes(1);
            var drawer = resolver.resolveFocus(true, true, List.of(window("com.android.systemui", SYSTEM_PANEL, 10)), null, false);
            eq(new FocusResolver.Focus(X, false), drawer); c.focus(drawer.app()); c.minutes(2);
            var returned = resolver.resolveFocus(true, true, List.of(window(X, APP, 1)), X, false);
            eq(new FocusResolver.Focus(X, true), returned); c.focus(returned.app()); c.minutes(1);
            eq(4 * MINUTE, c.e.used(X)); eq(6 * MINUTE, c.e.remaining(X));
        });
        test("active redacted panel covers a still-focused app", () -> {
            FocusResolver resolver = new FocusResolver();
            resolver.resolveFocus(true, true, List.of(window(I, APP, 1)), I, false);
            var shade = new FocusResolver.Window(null, SYSTEM_PANEL, false, true, 10);
            eq(new FocusResolver.Focus(I, false), resolver.resolveFocus(true, true, List.of(window(I, APP, 1), shade), I, false));
            var statusBar = new FocusResolver.Window(null, SYSTEM_PANEL, false, false, 10);
            eq(new FocusResolver.Focus(I, true), resolver.resolveFocus(true, true, List.of(window(I, APP, 1), statusBar), I, false));
        });
        test("transitional and fallback SystemUI evidence cannot expose a remembered app", () -> {
            FocusResolver resolver = new FocusResolver();
            resolver.resolveFocus(true, true, List.of(window(X, APP, 0)), X, false);
            eq(new FocusResolver.Focus(X, false), resolver.resolveFocus(true, true, List.of(window(X, APP, 0)), "com.android.systemui", false));
            eq(new FocusResolver.Focus(X, false), resolver.resolveFocus(true, true, List.of(window(null, APP, 0)), "com.android.systemui", false));
            eq(new FocusResolver.Focus(X, false), resolver.resolveFocus(true, true, List.of(), "com.android.systemui", false));
            eq(new FocusResolver.Focus(null, false), resolver.resolveFocus(true, true, List.of(), null, false));
            eq(new FocusResolver.Focus(null, false), resolver.resolveFocus(true, true, List.of(), "com.android.systemui", false));
        });
        test("app root fallback can confirm return but keyboard roots cannot", () -> {
            FocusResolver resolver = new FocusResolver();
            eq(new FocusResolver.Focus(X, true), resolver.resolveFocus(true, true, List.of(), X, false));
            var keyboard = window("keyboard", INPUT_METHOD, 5);
            eq(new FocusResolver.Focus(X, false), resolver.resolveFocus(true, true, List.of(keyboard), "keyboard", false));
            eq(new FocusResolver.Focus(X, true), resolver.resolveFocus(true, true, List.of(window(X, APP, 1), keyboard), "keyboard", false));
        });
        test("settings launcher lock recents and stopped state require fresh visibility", () -> {
            FocusResolver resolver = new FocusResolver();
            resolver.resolveFocus(true, true, List.of(window(X, APP, 1)), X, false);
            for (String pkg : List.of("com.android.settings", "com.sec.android.app.launcher"))
                eq(new FocusResolver.Focus(pkg, true), resolver.resolveFocus(true, true, List.of(window(pkg, APP, 1)), pkg, false));
            eq(new FocusResolver.Focus(null, false), resolver.resolveFocus(false, true, List.of(window(X, APP, 1)), X, false));
            eq(new FocusResolver.Focus(null, false), resolver.resolveFocus(true, true, List.of(window(null, SYSTEM_PANEL, 5)), null, false));
            eq(new FocusResolver.Focus(null, false), resolver.resolveFocus(true, true, List.of(window(X, APP, 1)), X, true));
            eq(new FocusResolver.Focus(null, false), resolver.resolveFocus(true, false, List.of(window(X, APP, 1)), X, false));
        });
    }
}
