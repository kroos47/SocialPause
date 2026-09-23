package app.socialpause;

import android.Manifest;
import android.app.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.graphics.drawable.Icon;
import android.os.*;
import android.widget.RemoteViews;
import android.content.res.ColorStateList;
import app.socialpause.engine.*;

/** Active app uses a standard live-eligible template; the idle overview has individual timer rows. */
final class TimerNotifications {
    private final Context context;
    private final NotificationManager manager;
    private final SharedPreferences visibility;
    private String previous="";
    TimerNotifications(Context c) {
        context=c;manager=c.getSystemService(NotificationManager.class);
        visibility=c.getSharedPreferences("notification-visibility",Context.MODE_PRIVATE);
        NotificationChannel channel=new NotificationChannel("timer","Social timers",NotificationManager.IMPORTANCE_LOW);
        channel.setSound(null,null);channel.enableVibration(false);manager.createNotificationChannel(channel);
    }
    void newRun(){visibility.edit().remove("ordinary-run").remove("dismissed-cycle").apply();previous="";}
    void dismissed(long run){visibility.edit().putLong("ordinary-run",run).apply();previous="";}
    private RemoteViews overview(TimerPresentation p, String title, long wall) {
        Design d=new Design(context);RemoteViews view=new RemoteViews(context.getPackageName(),R.layout.notification_overview);
        view.setTextViewText(R.id.overview_title,title);view.removeAllViews(R.id.timer_rows);
        for(TimerPresentation.Row row:p.rows){
            RemoteViews item=new RemoteViews(context.getPackageName(),R.layout.notification_timer_row);
            item.setImageViewResource(R.id.timer_icon,Design.appIcon(row.app()));item.setInt(R.id.timer_icon,"setColorFilter",d.appColor(row.app()));
            item.setTextViewText(R.id.timer_name,AppController.label(context,row.app()));
            item.setTextViewText(R.id.timer_value,row.noAllowance()?"No allowance":AppController.duration(row.remaining())+(row.cooling()?" cooldown":" left"));
            item.setProgressBar(R.id.timer_progress,100,row.progress(),false);
            item.setColorStateList(R.id.timer_progress,"setProgressTintList",ColorStateList.valueOf(row.cooling()||row.noAllowance()?d.muted:d.appColor(row.app())));
            item.setContentDescription(R.id.timer_progress,row.noAllowance()?"No usage allowance is configured":row.cooling()?"Available at "+AppController.at(wall+row.remaining()):"Usage allowance remaining");
            view.addView(R.id.timer_rows,item);
        }
        boolean showShared=p.sharedLimit>0 && p.kind!=TimerPresentation.Kind.LUNCH_COOLDOWN && p.kind!=TimerPresentation.Kind.SHARED_COOLDOWN;
        view.setViewVisibility(R.id.overview_shared,showShared?android.view.View.VISIBLE:android.view.View.GONE);
        view.setTextViewText(R.id.overview_shared,"Shared allowance · "+AppController.duration(p.sharedRemaining)+" remaining");
        view.setTextViewText(R.id.overview_note,"Tap to open SocialPause");return view;
    }
    void update(RulesEngine e,boolean connected) {
        long wall=AppController.wall(),elapsed=AppController.elapsed();
        TimerPresentation p=TimerPresentation.of(e,connected,wall,elapsed);
        if(p.kind==TimerPresentation.Kind.HIDDEN || context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED || !manager.areNotificationsEnabled()) {
            manager.cancel(10);previous="";return;
        }
        boolean awake=context.getSystemService(PowerManager.class).isInteractive();
        Design d=new Design(context);
        String title,body;boolean overview=p.kind==TimerPresentation.Kind.OVERVIEW||p.kind==TimerPresentation.Kind.NO_ALLOWANCE||p.kind==TimerPresentation.Kind.ALL_COOLDOWN||p.kind==TimerPresentation.Kind.LUNCH_COOLDOWN||p.kind==TimerPresentation.Kind.SHARED_COOLDOWN;
        switch(p.kind) {
            case APP -> {
                String name=AppController.label(context,p.app);
                title=p.sharedLimiting?"Shared allowance · "+name:name;
                body=p.sharedLimiting?p.shortCriticalText()+" shared time left · "+AppController.duration(e.remaining(p.app))+" in "+name
                        :p.shortCriticalText()+" app time left"+(p.sharedLimit>0?" · "+AppController.duration(p.sharedRemaining)+" shared":" · "+e.limit(p.app)/RulesEngine.MINUTE+" min allowance");
            }
            case LUNCH -> {title="Lunch break · unrestricted";body="Cooldown starts at "+AppController.at(wall+p.remaining)+" · Fresh allowances at "+AppController.at(wall+p.remaining+RulesEngine.COOLDOWN);}
            case LUNCH_COOLDOWN -> {title="After lunch · all apps resting";body="Post-lunch cooldown ends at "+AppController.at(wall+p.remaining);}
            case SHARED_COOLDOWN -> {title="Shared allowance used · all apps resting";body="Shared cooldown ends at "+AppController.at(wall+p.remaining);}
            default -> {
                title=p.kind==TimerPresentation.Kind.NO_ALLOWANCE?"No app allowance":p.kind==TimerPresentation.Kind.ALL_COOLDOWN?"All apps cooling down":"Your app timers";
                StringBuilder rows=new StringBuilder();
                if(p.sharedLimit>0)rows.append("Shared allowance · ").append(AppController.duration(p.sharedRemaining)).append(" remaining\n");
                if(p.app!=null)rows.append("Next available: ").append(AppController.label(context,p.app)).append(" at ").append(AppController.at(wall+p.remaining)).append('\n');
                for(TimerPresentation.Row row:p.rows) {
                    rows.append(AppController.label(context,row.app())).append(" · ");
                    if(row.noAllowance()) rows.append("No allowance");
                    else if(row.cooling()) {
                        rows.append("Cooldown ");
                        if(awake)rows.append(AppController.duration(row.remaining())).append(" · ");
                        rows.append("until ").append(AppController.at(wall+row.remaining()));
                    } else rows.append(AppController.duration(row.remaining())).append(" available");
                    rows.append('\n');
                }
                body=rows.toString().stripTrailing();
            }
        }
        boolean ordinary=visibility.getLong("ordinary-run",-1)==e.cycleId();
        String key=e.cycleId()+":"+p.kind+":"+p.app+":"+(awake?p.remaining/1000:p.remaining/60000)+":"+p.rows.stream().map(r->r.app()+":"+r.remaining()/1000+":"+r.cooling()+":"+r.noAllowance()).collect(java.util.stream.Collectors.toList())+":"+body+":"+ordinary+":"+awake+":"+d.dark;
        if(key.equals(previous))return;previous=key;
        int icon=p.kind==TimerPresentation.Kind.APP && !p.sharedLimiting?Design.appIcon(p.app):R.drawable.ic_pause;
        int timerColor=p.activeChip() && !p.sharedLimiting?d.appColor(p.app):d.accent;
        PendingIntent open=PendingIntent.getActivity(context,0,new Intent(context,MainActivity.class),PendingIntent.FLAG_IMMUTABLE|PendingIntent.FLAG_UPDATE_CURRENT);
        PendingIntent dismiss=PendingIntent.getBroadcast(context,10,new Intent(context,NotificationDismissReceiver.class).setAction(NotificationDismissReceiver.ACTION).putExtra("run",e.cycleId()),PendingIntent.FLAG_IMMUTABLE|PendingIntent.FLAG_UPDATE_CURRENT);
        Notification.Builder b=new Notification.Builder(context,"timer").setSmallIcon(icon).setContentTitle(title)
                .setContentText(body).setContentIntent(open).setDeleteIntent(dismiss).setOngoing(true).setOnlyAlertOnce(true)
                .setColor(timerColor).setCategory(Notification.CATEGORY_PROGRESS);
        if(p.activeChip() && Build.VERSION.SDK_INT>=36) {
            b.setStyle(new Notification.ProgressStyle().setProgressSegments(java.util.List.of(new Notification.ProgressStyle.Segment(100).setColor(timerColor)))
                    .setProgress(p.progress()).setProgressTrackerIcon(Icon.createWithResource(context,icon)));
        } else if(overview) {
            b.setStyle(new Notification.DecoratedCustomViewStyle()).setCustomBigContentView(overview(p,title,wall));
        } else {
            b.setStyle(new Notification.BigTextStyle().bigText(body));
            if(p.activeChip())b.setProgress(100,p.progress(),false);
        }
        if(p.activeChip()) {
            // Explicit chip text takes precedence over the automatic chronometer fallback.
            b.setWhen(wall+p.remaining).setShowWhen(true).setUsesChronometer(true).setChronometerCountDown(true);
            if(Build.VERSION.SDK_INT>=36)b.setShortCriticalText(p.shortCriticalText());
            if(!ordinary){Bundle extras=new Bundle();extras.putBoolean("android.requestPromotedOngoing",true);b.addExtras(extras);}
        } else {
            b.setWhen(0).setShowWhen(false).setUsesChronometer(false);
            if(Build.VERSION.SDK_INT>=36)b.setShortCriticalText("");
        }
        Notification notification=b.build();
        if(p.activeChip() && !ordinary && Build.VERSION.SDK_INT>=36 && !notification.hasPromotableCharacteristics()) {
            // Initial Android 16 requires colorization; later releases require the opposite.
            // Ask this OS which public-API format it accepts, preserving the modern default.
            Notification legacy=Notification.Builder.recoverBuilder(context,notification.clone()).setColorized(true).build();
            if(legacy.hasPromotableCharacteristics())notification=legacy;
        }
        manager.notify(10,notification);
    }
}
