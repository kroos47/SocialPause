package app.socialpause;

import android.Manifest;
import android.app.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.net.Uri;
import android.os.*;
import android.provider.Settings;
import android.view.*;
import android.widget.*;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.time.temporal.TemporalAdjusters;
import java.util.*;
import app.socialpause.engine.RulesEngine;

/** Three-tab native UI. All allowance decisions remain in the timing engine. */
public final class MainActivity extends Activity {
    private AppController controller;
    private Design d;
    private LinearLayout root, content, navigation;
    private ScrollView scroll;
    private int tab;
    private boolean week = true;
    private String structure = "";
    private final List<Runnable> bindings = new ArrayList<>();
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable tick = new Runnable() {
        @Override public void run() { controller.refresh(); render(); handler.postDelayed(this, 1000); }
    };
    @Override public void onCreate(Bundle saved) {
        setTheme(R.style.Theme_SocialPause);
        super.onCreate(saved); controller = AppController.get(this); d = new Design(this);
        if (saved != null) { tab = saved.getInt("tab"); week = saved.getBoolean("week", true); }
        root = d.column(); root.setBackgroundColor(d.background);
        scroll = new ScrollView(this); scroll.setFillViewport(true); scroll.setClipToPadding(false);
        content = d.column(); content.setPadding(d.dp(24),d.dp(24),d.dp(24),d.dp(28)); scroll.addView(content);
        root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1)); navigation=d.row(); root.addView(navigation);
        setContentView(root);
        root.setOnApplyWindowInsetsListener((v,insets) -> {
            var bars=insets.getInsets(WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout());
            root.setPadding(bars.left,bars.top,bars.right,0);
            navigation.setPadding(d.dp(10),d.dp(10),d.dp(10),bars.bottom+d.dp(10)); return insets;
        });
        getWindow().getInsetsController().setSystemBarsAppearance(d.dark ? 0 : WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS | WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS,
                WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS | WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS);
        controller.refresh(); render();
    }
    @Override protected void onSaveInstanceState(Bundle state) { super.onSaveInstanceState(state); state.putInt("tab",tab); state.putBoolean("week",week); }
    private RulesEngine e() { return controller.engine; }
    private RulesEngine.Mode mode() { return e().mode(AppController.wall(),AppController.elapsed()); }
    private TextView text(String value,int size,int color,boolean bold) { return d.text(value,size,color,bold); }
    private void line(String value,int size,int color,boolean bold,int top) { d.add(content,text(value,size,color,bold),top); }
    private void bind(Runnable runnable) { bindings.add(runnable); runnable.run(); }
    private void render() {
        String key = tab+":"+week+":"+mode()+":"+controller.connected+":"+e().selected+":"+e().pendingAt()+":"+e().quiet(AppController.wall())+":"+e().lunchEnabled+":"+e().lunchMinute+":"+e().sleepEnabled+":"+e().sleepStart+":"+e().sleepEnd+":"+LocalDate.now();
        if (!key.equals(structure)) { structure=key; buildPage(); }
        for(Runnable binding:bindings)binding.run();
    }
    private void buildPage() {
        int y=scroll.getScrollY(); bindings.clear(); content.removeAllViews(); navigation.removeAllViews(); navigation.setBackgroundColor(d.surface);
        String[] titles={"Home","Insights","Settings"}, icons={"home","chart","settings"};
        for(int i=0;i<3;i++) {
            final int page=i; LinearLayout item=d.column(); item.setGravity(Gravity.CENTER); item.setMinimumHeight(d.dp(58));
            LinearLayout pill=d.row(); pill.setGravity(Gravity.CENTER); pill.setBackground(d.shape(i==tab?d.mint:d.surface,24));
            pill.addView(d.icon(icons[i],i==tab?d.ink:d.muted),new LinearLayout.LayoutParams(d.dp(24),d.dp(24)));
            item.addView(pill,new LinearLayout.LayoutParams(d.dp(60),d.dp(32)));
            TextView label=text(titles[i],12,i==tab?d.ink:d.muted,i==tab); label.setGravity(Gravity.CENTER);label.setPadding(0,d.dp(5),0,0);item.addView(label);
            item.setContentDescription(titles[i]);item.setSelected(i==tab);item.setFocusable(true); item.setOnClickListener(v->{tab=page;structure="";scroll.scrollTo(0,0);render();});
            navigation.addView(item,new LinearLayout.LayoutParams(0,-2,1));
        }
        if(tab==0)home();else if(tab==1)insights();else settings();
        scroll.post(()->scroll.scrollTo(0,y));
    }
    private void heading(String title,String subtitle) { line(title,28,d.ink,true,0);line(subtitle,15,d.muted,false,10); }
    private void banner(String icon,String title,String subtitle,int top,Runnable action) {
        LinearLayout card=d.card(d.mint,16), row=d.row(), words=d.column();
        row.addView(d.icon(icon,d.ink),new LinearLayout.LayoutParams(d.dp(22),d.dp(22)));
        words.setPadding(d.dp(14),0,0,0);words.addView(text(title,14,d.ink,true));d.add(words,text(subtitle,12,d.muted,false),6);d.weighted(row,words);card.addView(row);
        if(action!=null){card.setOnClickListener(v->action.run());card.setFocusable(true);card.setMinimumHeight(d.dp(64));}
        d.add(content,card,24);
    }
    private void home() {
        if(mode()==RulesEngine.Mode.COOLDOWN){cooldown();return;}
        LinearLayout title=d.row();d.weighted(title,text("SocialPause",28,d.ink,true));
        Button toggle=d.button(e().running?"Stop":"Start",false,()->{if(e().running)controller.stop();else controller.start();render();});
        toggle.setEnabled(e().running || controller.connected);toggle.setAlpha(toggle.isEnabled()?1f:.5f);title.addView(toggle);content.addView(title);
        line("A little social. A little more life.",15,d.muted,false,8);
        LinearLayout hero=d.card(d.accent,20),row=d.row(),words=d.column();
        String label=mode()==RulesEngine.Mode.LUNCH?"LUNCH BREAK":mode()==RulesEngine.Mode.STOPPED?"TRACKING STOPPED":"SHARED TIME LEFT";
        words.addView(text(label,12,d.onAccent,true));TextView time=text("",50,d.onAccent,false);d.add(words,time,12);
        TextView note=text("",13,0xFFE4EFE4,false);d.add(words,note,18);d.weighted(row,words);
        Design.Ring ring=d.new Ring();row.addView(ring,new LinearLayout.LayoutParams(d.dp(88),d.dp(88)));hero.addView(row);d.add(content,hero,24);
        bind(()->{
            long remaining=RulesEngine.TOTAL_LIMIT-e().total();
            if(mode()==RulesEngine.Mode.STOPPED){time.setText(R.string.free_time);time.setTextSize(34);note.setText(R.string.start_hint);ring.setFraction(0);}
            else if(mode()==RulesEngine.Mode.LUNCH){time.setText(AppController.duration(e().countdown(AppController.wall(),AppController.elapsed())));note.setText(R.string.lunch_hint);ring.setFraction(e().countdown(AppController.wall(),AppController.elapsed())/(float)RulesEngine.COOLDOWN);}
            else{time.setText(AppController.duration(remaining));note.setText(R.string.shared_cycle_hint);ring.setFraction(remaining/(float)RulesEngine.TOTAL_LIMIT);}
        });
        LinearLayout section=d.row();d.weighted(section,text("Your social apps",20,d.ink,true));section.addView(text("10 min each",12,d.muted,false));d.add(content,section,24);
        for(String pkg:e().selected)appCard(pkg,false);
        if(!controller.connected)banner("settings","App monitoring is off","Tap to enable Accessibility and restore monitoring.",24,()->{tab=2;structure="";render();});
        else if(mode()==RulesEngine.Mode.STOPPED)banner("play","Enjoy your free time","Tracking and limits are off until you press Start.",24,null);
        else if(mode()==RulesEngine.Mode.LUNCH)banner("clock","Lunch is unrestricted","Your next allowance starts after the following cooldown.",24,null);
        else banner("pause","Usage paused","Resumes when a social app is in focus.",24,null);
        if(e().quiet(AppController.wall()) && e().running)line("Sleep Time · notifications hidden, limits still active.",12,d.muted,false,16);
    }
    private void appCard(String pkg,boolean compact) {
        LinearLayout card=d.card(d.surface,16),row=d.row(),words=d.column();
        row.addView(d.badge(pkg),new LinearLayout.LayoutParams(d.dp(40),d.dp(40)));words.setPadding(d.dp(14),0,d.dp(8),0);
        words.addView(text(AppController.label(this,pkg),16,d.ink,true));TextView used=text("",12,d.muted,false);if(!compact)d.add(words,used,6);d.weighted(row,words);
        TextView left=text("",compact?12:20,compact?d.muted:d.ink,!compact);row.addView(left);card.addView(row);
        Design.UsageBar bar=d.new UsageBar();if(!compact){LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,d.dp(4));lp.topMargin=d.dp(18);card.addView(bar,lp);}
        d.add(content,card,12);
        bind(()->{
            boolean blocked=e().blocked(pkg,AppController.wall(),AppController.elapsed());
            left.setText(!e().running?"Free":mode()==RulesEngine.Mode.LUNCH?"Free":blocked?"Blocked":AppController.duration(e().remaining(pkg)));
            used.setText(e().used(pkg)==0?"Not used yet":Design.usage(e().used(pkg))+" used");bar.setFraction(e().used(pkg)/(float)RulesEngine.APP_LIMIT);
        });
    }
    private void cooldown() {
        heading("Time for a breather","Your social time is complete for now.");
        LinearLayout card=d.card(d.accent,28);card.setGravity(Gravity.CENTER);
        card.addView(d.icon("clock",0xFFDCEFD0),new LinearLayout.LayoutParams(d.dp(26),d.dp(26)));
        TextView label=text("COOLDOWN REMAINING",12,d.onAccent,true);label.setGravity(Gravity.CENTER);d.add(card,label,28);
        TextView time=text("",62,d.onAccent,false);time.setGravity(Gravity.CENTER);d.add(card,time,16);
        TextView fresh=text("Fresh allowance after cooldown",13,0xFFE4EFE4,false);fresh.setGravity(Gravity.CENTER);d.add(card,fresh,28);d.add(content,card,28);
        bind(()->time.setText(AppController.duration(e().countdown(AppController.wall(),AppController.elapsed()))));
        line("All social apps are resting",21,d.ink,true,26);for(String pkg:e().selected)appCard(pkg,true);
        d.add(content,d.button("Stop tracking · use socials freely",false,()->{controller.stop();render();}),32);
        if(!controller.connected)banner("settings","Monitoring needs attention","Accessibility is disconnected. Open Settings to reconnect.",16,()->{tab=2;structure="";render();});
        if(e().quiet(AppController.wall()))line("Sleep Time · timer notifications hidden.",12,d.muted,false,16);
    }
    private void insights() {
        heading("Your insights","A clearer picture of your social time.");
        LinearLayout tabs=d.row();tabs.setPadding(d.dp(4),d.dp(4),d.dp(4),d.dp(4));tabs.setBackground(d.shape(d.line,24));
        for(int i=0;i<2;i++){boolean value=i==1;TextView t=text(value?"This week":"Today",14,d.ink,week==value);t.setGravity(Gravity.CENTER);t.setMinHeight(d.dp(44));t.setBackground(d.shape(week==value?d.surface:d.line,24));t.setOnClickListener(v->{week=value;structure="";render();});t.setFocusable(true);t.setSelected(week==value);tabs.addView(t,new LinearLayout.LayoutParams(0,-2,1));}d.add(content,tabs,22);
        LocalDate today=LocalDate.now(),start=week?today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)):today,end=week?start.plusDays(6):today;
        LinearLayout hero=d.card(d.accent,22);hero.addView(text("TRACKED SOCIAL TIME",12,d.onAccent,true));TextView total=text("",46,d.onAccent,false);d.add(hero,total,20);
        d.add(hero,text(week?start.format(DateTimeFormatter.ofPattern("d MMM"))+" – "+end.format(DateTimeFormatter.ofPattern("d MMM")):today.format(DateTimeFormatter.ofPattern("EEEE, d MMM")),13,0xFFE4EFE4,false),28);d.add(content,hero,20);
        line(week?"Your week, at a glance":"Your day, at a glance",21,d.ink,true,26);
        Design.Chart chart=d.new Chart();d.add(content,chart,12);chart.setLayoutParams(new LinearLayout.LayoutParams(-1,d.dp(158)));
        TextView empty=text("",13,d.muted,false);d.add(content,empty,10);
        line("By app",21,d.ink,true,24);LinearLayout appRows=d.column();d.add(content,appRows,12);
        final String[] previous={""}; final Map<String,TextView> counters=new HashMap<>();
        bind(()->{
            var history=e().history();var apps=history.byApp(start,end);long ms=history.total(start,end);total.setText(Design.usage(ms));
            empty.setText(ms==0?"Your first tracked minutes will appear here.":"Stored on this phone · hours and minutes");
            long[] values;String[] labels;int selected;
            if(week){values=new long[7];labels=new String[]{"M","T","W","T","F","S","S"};for(int i=0;i<7;i++)values[i]=history.total(start.plusDays(i),start.plusDays(i));selected=today.getDayOfWeek().getValue()-1;}
            else{values=new long[6];labels=new String[]{"00","04","08","12","16","20"};long[] hours=history.hours(today);for(int i=0;i<24;i++)values[i/4]+=hours[i];selected=LocalTime.now().getHour()/4;}
            chart.data(values,labels,selected);
            Set<String> packages=new LinkedHashSet<>(e().selected);packages.addAll(apps.keySet());
            if(!previous[0].equals(packages.toString())){
                previous[0]=packages.toString();appRows.removeAllViews();counters.clear();
                for(String pkg:packages){LinearLayout row=d.row();row.setPadding(0,d.dp(8),0,d.dp(8));row.addView(d.badge(pkg),new LinearLayout.LayoutParams(d.dp(40),d.dp(40)));TextView name=text(AppController.label(this,pkg),16,d.ink,false);name.setPadding(d.dp(14),0,d.dp(8),0);d.weighted(row,name);TextView val=text("",16,d.ink,true);row.addView(val);appRows.addView(row);counters.put(pkg,val);}
            }
            for(String pkg:packages)counters.get(pkg).setText(Design.usage(apps.getOrDefault(pkg,0L)));
        });
        line("Only tracked allowance time is included. Lunch and Stop periods are excluded. History starts with this version and stays on this phone.",12,d.muted,false,18);
    }
    private void settings() {
        heading("Your rhythm","Set limits that fit your day.");
        line("DAILY SCHEDULE",12,d.muted,true,30);
        settingRow("clock","Lunch break",e().lunchEnabled?AppController.time(e().lunchMinute)+"–"+AppController.time((e().lunchMinute+60)%1440)+" · unrestricted":"Off",this::lunchDialog);
        if(e().lunchEnabled){LinearLayout note=d.card(d.mint,16);note.addView(text("Then a little space.",14,d.ink,true));d.add(note,text("Social apps cool down from "+AppController.time((e().lunchMinute+60)%1440)+" to "+AppController.time((e().lunchMinute+120)%1440)+".",12,d.muted,false),6);d.add(content,note,8);}
        settingRow("moon","Sleep Time",e().sleepEnabled?AppController.time(e().sleepStart)+"–"+AppController.time(e().sleepEnd)+" · notifications hidden":"Off",this::sleepDialog);
        line("Limits stay active during Sleep Time.",13,d.muted,false,12);
        line("YOUR ALLOWANCE",12,d.muted,true,30);
        StringJoiner selected=new StringJoiner(", ");for(String pkg:e().selected)selected.add(AppController.label(this,pkg));
        settingRow("chart","Selected apps",selected.toString(),this::chooseApps);
        LinearLayout fixed=d.card(d.surface,18);fixed.addView(text("10 min per app",16,d.ink,true));d.add(fixed,text("20 min combined · 60 min cooldown",13,d.muted,false),8);d.add(content,fixed,16);
        line(e().pendingAt()>0?"Lunch change scheduled for "+Instant.ofEpochMilli(e().pendingAt()).atZone(ZoneId.systemDefault()).toLocalDate()+".":"Lunch schedule edits apply tomorrow.",13,d.muted,false,24);
        line("Use Stop on Home when you need free access.",12,d.muted,false,8);
        line("APP SETUP",12,d.muted,true,30);
        settingRow("settings","App monitoring",controller.connected?"Connected":"Required · tap to enable",this::accessibilityDialog);
        settingRow("clock","Timer notifications",getSystemService(NotificationManager.class).areNotificationsEnabled()?"Enabled · hidden during Sleep Time":"Off · tap to enable",()->{
            if(checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED)requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS},1);
            else open(new Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE,getPackageName()));
        });
        settingRow("clock","Precise schedule alarms",getSystemService(AlarmManager.class).canScheduleExactAlarms()?"Allowed":"Optional · improves idle transitions",()->open(new Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,Uri.parse("package:"+getPackageName()))));
        settingRow("settings","Battery & app settings","Review if Samsung delays monitoring",()->open(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,Uri.parse("package:"+getPackageName()))));
        line("Samsung controls live notification availability and layout. A standard timer notification is used as the fallback.",12,d.muted,false,20);
        line("Offline and personal. Accessibility reads app identity, not screen text. Stop, uninstalling, force-stop, or disabling Accessibility can bypass limits. Blocking returns to phone Home; it cannot force-stop other apps or stop background audio.",12,d.muted,false,16);
    }
    private void settingRow(String icon,String title,String subtitle,Runnable action) {
        LinearLayout card=d.card(d.surface,18),row=d.row(),words=d.column();row.addView(d.icon(icon,d.ink),new LinearLayout.LayoutParams(d.dp(24),d.dp(24)));
        words.setPadding(d.dp(16),0,d.dp(10),0);words.addView(text(title,16,d.ink,true));d.add(words,text(subtitle,13,d.muted,false),8);d.weighted(row,words);
        row.addView(d.icon("chevron",d.muted),new LinearLayout.LayoutParams(d.dp(18),d.dp(18)));card.addView(row);card.setMinimumHeight(d.dp(82));card.setOnClickListener(v->action.run());card.setFocusable(true);d.add(content,card,16);
    }
    private void accessibilityDialog() {
        new AlertDialog.Builder(this).setTitle("App monitoring permission")
                .setMessage("SocialPause uses Accessibility to identify the focused app, measure usage, and cover blocked apps with a brief limit message before returning to phone Home. It does not read or store screen text. Usage history stays on this phone.\n\nIf Android blocks this setting for a sideloaded app, open App info and look for Allow restricted settings, then return here.")
                .setNegativeButton("Cancel",null).setPositiveButton("Open settings",(dialog,which)->open(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))).show();
    }
    private void chooseApps() {
        if(e().running){message("Use Stop on Home before changing your app list.");return;}
        Set<String> original=new LinkedHashSet<>(e().selected);
        new Thread(()->{
            Set<String> packages=new HashSet<>(original);for(var info:getPackageManager().queryIntentActivities(new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER),0))packages.add(info.activityInfo.packageName);
            packages.removeAll(AppController.protectedPackages(this));List<String> sorted=new ArrayList<>(packages);sorted.sort(Comparator.comparing(p->AppController.label(this,p)));
            runOnUiThread(()->{
                if(isFinishing()||isDestroyed())return;
                String[] labels=sorted.stream().map(p->AppController.label(this,p)).toArray(String[]::new);boolean[] checked=new boolean[sorted.size()];Set<String> chosen=new LinkedHashSet<>(original);
                for(int i=0;i<checked.length;i++)checked[i]=chosen.contains(sorted.get(i));
                new AlertDialog.Builder(this).setTitle("Your social apps").setMultiChoiceItems(labels,checked,(dialog,index,enabled)->{if(enabled)chosen.add(sorted.get(index));else chosen.remove(sorted.get(index));})
                        .setNegativeButton("Cancel",null).setPositiveButton("Save",(dialog,which)->{try{e().select(chosen);controller.refresh();structure="";render();}catch(IllegalArgumentException|IllegalStateException ex){message(ex.getMessage());}}).show();
            });
        },"app-list").start();
    }
    private void lunchDialog() {
        new AlertDialog.Builder(this).setTitle("Lunch · changes apply tomorrow").setMessage("One unrestricted hour resets usage, followed by one blocked hour even if lunch was unused. Overnight windows finish before a pending change applies.")
                .setNegativeButton("Cancel",null).setNeutralButton("Disable tomorrow",(dialog,which)->{e().lunch(false,e().lunchMinute,AppController.wall(),AppController.elapsed());controller.refresh();render();})
                .setPositiveButton("Set start time",(dialog,which)->pickTime(e().lunchMinute,minute->{e().lunch(true,minute,AppController.wall(),AppController.elapsed());controller.refresh();render();})).show();
    }
    private void sleepDialog() {
        new AlertDialog.Builder(this).setTitle("Sleep Time").setMessage("Hide timer notifications while limits stay active. Choose a start, then an end time.")
                .setNegativeButton("Cancel",null).setNeutralButton("Disable",(dialog,which)->{e().sleep(false,e().sleepStart,e().sleepEnd);controller.refresh();render();})
                .setPositiveButton("Set times",(dialog,which)->pickTime(e().sleepStart,start->pickTime(e().sleepEnd,end->{try{e().sleep(true,start,end);controller.refresh();render();}catch(IllegalArgumentException ex){message(ex.getMessage());}}))).show();
    }
    private void pickTime(int minute,java.util.function.IntConsumer done){new TimePickerDialog(this,(view,h,m)->done.accept(h*60+m),minute/60,minute%60,true).show();}
    private void message(String value){new AlertDialog.Builder(this).setMessage(value).setPositiveButton("OK",null).show();}
    private void open(Intent intent){try{startActivity(intent);}catch(ActivityNotFoundException ex){message("This settings page is unavailable. Open this app's settings from your phone Settings app.");}}
    @Override protected void onResume(){super.onResume();structure="";handler.removeCallbacks(tick);handler.post(tick);}
    @Override protected void onPause(){handler.removeCallbacks(tick);super.onPause();}
}
