package app.socialpause;

import android.app.Application;

public final class SocialPauseApp extends Application {
    private AppController controller;
    @Override public void onCreate() { super.onCreate(); controller = new AppController(this); }
    public AppController controller() { return controller; }
}
