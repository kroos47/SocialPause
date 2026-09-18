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
    private LocalDate selectedDay;
    private Design.Chart weeklyChart;
    private LocalDate touchSelection;
    private float touchX,touchY;
    private boolean moved;
    @Override public boolean dispatchTouchEvent(MotionEvent event) {
        if(event.getActionMasked()==MotionEvent.ACTION_DOWN){touchSelection=selectedDay;touchX=event.getRawX();touchY=event.getRawY();moved=false;}
        if(event.getActionMasked()==MotionEvent.ACTION_MOVE && Math.hypot(event.getRawX()-touchX,event.getRawY()-touchY)>ViewConfiguration.get(this).getScaledTouchSlop())moved=true;
        boolean clear=event.getActionMasked()==MotionEvent.ACTION_UP&&!moved&&touchSelection!=null&&weeklyChart!=null&&!weeklyChart.containsDay(event.getRawX(),event.getRawY());
        boolean handled=super.dispatchTouchEvent(event);
        if(clear&&tab==1&&week&&touchSelection.equals(selectedDay)){selectedDay=null;structure="";render();}
        return handled;
    }
    private String structure = "";
    private final List<Runnable> bindings = new ArrayList<>();
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable tick = new Runnable() {
        @Override public void run() { controller.refresh(); render(); handler.postDelayed(this, 1000); }
    };
    @Override public void onCreate(Bundle saved) {
        setTheme(R.style.Theme_SocialPause);
        super.onCreate(saved); controller = AppController.get(this); d = new Design(this);
        if (saved != null) { tab = saved.getInt("tab"); week = saved.getBoolean("week", true); String day = saved.getString("selectedDay"); if (day != null) selectedDay = LocalDate.parse(day); }
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
    @Override protected void onSaveInstanceState(Bundle state) { super.onSaveInstanceState(state); state.putInt("tab",tab); state.putBoolean("week",week); if(selectedDay!=null)state.putString("selectedDay",selectedDay.toString()); }
    private RulesEngine e() { return controller.engine; }
    private RulesEngine.Mode mode() { return e().mode(AppController.wall(),AppController.elapsed()); }
    private TextView text(String value,int size,int color,boolean bold) { return d.text(value,size,color,bold); }
    private void line(String value,int size,int color,boolean bold,int top) { d.add(content,text(value,size,color,bold),top); }
    private void bind(Runnable runnable) { bindings.add(runnable); runnable.run(); }
    private void render() {
        LocalDate monday=LocalDate.now().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        if(selectedDay!=null && (selectedDay.isBefore(monday)||selectedDay.isAfter(monday.plusDays(6))))selectedDay=null;
        String key = tab+":"+week+":"+selectedDay+":"+mode()+":"+controller.connected+":"+e().selected+":"+e().pendingAt()+":"+e().quiet(AppController.wall())+":"+e().lunchEnabled+":"+e().lunchMinute+":"+e().sleepEnabled+":"+e().sleepStart+":"+e().sleepEnd+":"+LocalDate.now();
        if (!key.equals(structure)) { structure=key; buildPage(); }
        for(Runnable binding:bindings)binding.run();
    }
    private void buildPage() {
        int y=scroll.getScrollY(); bindings.clear(); weeklyChart=null; content.removeAllViews(); navigation.removeAllViews(); navigation.setBackgroundColor(d.surface);
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
    private void heading(String title,String subtitle) { line(title,28,d.ink,true,0);line(subtitle,14,d.muted,false,10); }
    private void banner(String icon,String title,String subtitle,int top,Runnable action) {
        LinearLayout card=d.card(d.mint,16), row=d.row(), words=d.column();
        row.addView(d.icon(icon,d.ink),new LinearLayout.LayoutParams(d.dp(22),d.dp(22)));
        words.setPadding(d.dp(14),0,0,0);words.addView(text(title,14,d.ink,true));d.add(words,text(subtitle,12,d.muted,false),6);d.weighted(row,words);card.addView(row);
        if(action!=null){card.setOnClickListener(v->action.run());card.setFocusable(true);card.setMinimumHeight(d.dp(64));}
        d.add(content,card,24);
    }
    private void home() {
        LinearLayout title=d.row();d.weighted(title,text("SocialPause",28,d.ink,true));
        Button toggle=d.button(e().running?"Stop":"Start",false,()->{if(e().running)controller.stop();else controller.start();render();});
        toggle.setMinWidth(d.dp(86));toggle.setEnabled(e().running || controller.connected);toggle.setAlpha(toggle.isEnabled()?1f:.5f);title.addView(toggle);content.addView(title);
        line("A little social. A little more life.",14,d.muted,false,8);
        LinearLayout summary=d.card(d.mint,20),row=d.row(),words=d.column();
        row.addView(d.icon("clock",d.ink),new LinearLayout.LayoutParams(d.dp(24),d.dp(24)));words.setPadding(d.dp(16),0,0,0);
        TextView status=text("",20,d.ink,true),note=text("",13,d.muted,false);words.addView(status);d.add(words,note,8);d.weighted(row,words);summary.addView(row);summary.setMinimumHeight(d.dp(98));d.add(content,summary,24);
        bind(()->{
            long wall=AppController.wall(),elapsed=AppController.elapsed();int available=e().availableCount(wall,elapsed);
            String heading,detail;
            if(!controller.connected){heading="Monitoring is off";detail="Enable App monitoring in Settings.";}
            else if(mode()==RulesEngine.Mode.STOPPED){heading="Tracking stopped";detail="Social apps are unrestricted until you Start.";}
            else if(mode()==RulesEngine.Mode.LUNCH){heading="Enjoy your lunch break";detail="Unrestricted until "+AppController.at(wall+e().countdown(wall,elapsed))+".";}
            else if(mode()==RulesEngine.Mode.LUNCH_COOLDOWN){heading="A little space after lunch";detail="All apps available at "+AppController.at(wall+e().countdown(wall,elapsed))+".";}
            else if(e().quiet(wall)){heading="Sleep Time is on";detail="Notifications hidden. App limits stay active.";}
            else if(available==0){heading="Time for a breather";detail="Each app will return after its own cooldown.";}
            else {heading=getResources().getQuantityString(R.plurals.apps_available,available,available);detail="Each app has its own timer and cooldown.";}
            status.setText(heading);note.setText(detail);
        });
        line("Your social apps",20,d.ink,true,24);for(String pkg:e().selected)appCard(pkg);
        line("Usage pauses when you leave an app or lock your phone. Cooldowns keep counting down.",12,d.muted,false,20);
        if(!controller.connected)banner("settings","Enable app monitoring","Tap to open setup.",20,()->{tab=2;structure="";render();});
    }
    private void appCard(String pkg) {
        LinearLayout card=d.card(d.surface,16),row=d.row(),words=d.column(),counter=d.column();
        row.addView(d.badge(pkg),new LinearLayout.LayoutParams(d.dp(44),d.dp(44)));words.setPadding(d.dp(12),0,d.dp(6),0);
        words.addView(text(AppController.label(this,pkg),16,d.ink,true));TextView limit=text(e().limit(pkg)/RulesEngine.MINUTE+" min per session",12,d.muted,false);d.add(words,limit,6);d.weighted(row,words);
        counter.setGravity(Gravity.END);TextView left=text("",32,d.ink,true),caption=text("",12,d.muted,false);counter.addView(left);d.add(counter,caption,3);caption.setGravity(Gravity.END);row.addView(counter);card.addView(row);
        LinearLayout state=d.row();TextView pill=d.pill("",false),detail=text("",12,d.muted,false);state.addView(pill);detail.setPadding(d.dp(10),0,0,0);d.weighted(state,detail);d.add(card,state,16);
        Design.UsageBar bar=d.new UsageBar();LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,d.dp(4));lp.topMargin=d.dp(12);card.addView(bar,lp);d.add(content,card,12);
        bind(()->{
            long wall=AppController.wall(),elapsed=AppController.elapsed(),cooldown=e().cooldownRemaining(pkg,wall,elapsed);
            boolean free=!e().running||mode()==RulesEngine.Mode.LUNCH,active=pkg.equals(e().focused()),cooling=cooldown>0;
            String remaining=free?"Free":AppController.duration(cooling?cooldown:e().remaining(pkg));left.setText(remaining);
            caption.setText(free?"Unrestricted":cooling?"cooldown left":"usage left");
            String stateLabel=cooling?"Cooldown":active?"In use":e().used(pkg)>0?"Paused":"Available";
            pill.setText(stateLabel);pill.setBackground(d.shape(cooling?d.warning:d.mint,16));pill.setTextColor(cooling?d.warningInk:d.ink);
            String description=cooling?"Ready at "+AppController.at(wall+cooldown):active?"Timer is running":e().used(pkg)>0?"Resumes when you return":"Ready when you are";
            detail.setText(description);state.setVisibility(free?View.GONE:View.VISIBLE);bar.setVisibility(free?View.GONE:View.VISIBLE);
            limit.setText(!e().running?"Tracking off":mode()==RulesEngine.Mode.LUNCH?"Lunch break":e().limit(pkg)/RulesEngine.MINUTE+" min per session");
            bar.setColor(cooling?d.muted:d.accent);bar.setFraction(free?0:cooling?1-cooldown/(float)RulesEngine.COOLDOWN:e().used(pkg)/(float)e().limit(pkg));
        });
    }
    private void insights() {
        heading("Your insights","A clearer picture of your social time.");
        LinearLayout tabs=d.row();tabs.setPadding(d.dp(4),d.dp(4),d.dp(4),d.dp(4));tabs.setBackground(d.shape(d.line,24));
        for(int i=0;i<2;i++){boolean value=i==1;TextView t=text(value?"This week":"Today",14,d.ink,week==value);t.setGravity(Gravity.CENTER);t.setMinHeight(d.dp(44));t.setBackground(d.shape(week==value?d.surface:d.line,24));t.setOnClickListener(v->{week=value;selectedDay=null;structure="";render();});t.setFocusable(true);t.setSelected(week==value);tabs.addView(t,new LinearLayout.LayoutParams(0,-2,1));}d.add(content,tabs,22);
        LocalDate today=LocalDate.now(),monday=today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        LocalDate start=week?(selectedDay==null?monday:selectedDay):today,end=week&&selectedDay==null?monday.plusDays(6):start;
        if(week){
            LinearLayout hero=d.card(d.accent,20);hero.addView(text(selectedDay==null?"THIS WEEK'S TRACKED TIME":"TRACKED TIME · SELECTED DAY",12,d.onAccent,true));
            LinearLayout totalRow=d.row();TextView total=text("",40,d.onAccent,false);d.weighted(totalRow,total);
            TextView period=text(selectedDay==null?monday.format(DateTimeFormatter.ofPattern("d MMM"))+" – "+end.format(DateTimeFormatter.ofPattern("d MMM")):selectedDay.format(DateTimeFormatter.ofPattern("EEE, d MMM")),12,d.onAccent,false);period.setGravity(Gravity.END);totalRow.addView(period);d.add(hero,totalRow,14);d.add(content,hero,20);
            if(selectedDay!=null){hero.setFocusable(true);hero.setContentDescription("Selected day summary. Tap to show weekly summary.");hero.setOnClickListener(v->{selectedDay=null;structure="";render();});}
            bind(()->total.setText(Design.usage(e().history().total(start,end))));
            LinearLayout chartHeading=d.row();d.weighted(chartHeading,text("Your week, at a glance",20,d.ink,true));chartHeading.addView(text("Minutes",12,d.muted,false));d.add(content,chartHeading,24);
            Design.Chart chart=d.new Chart(day->{selectedDay=day.equals(selectedDay)?null:day;structure="";render();});weeklyChart=chart;
            LinearLayout.LayoutParams chartLp=new LinearLayout.LayoutParams(-1,d.dp(184));chartLp.topMargin=d.dp(12);content.addView(chart,chartLp);
            bind(()->{java.util.List<Map<String,Long>> days=new ArrayList<>();for(int i=0;i<7;i++)days.add(e().history().byApp(monday.plusDays(i),monday.plusDays(i)));chart.data(days,monday,selectedDay);});
            Set<String> legendApps=new LinkedHashSet<>(e().selected);legendApps.addAll(e().history().byApp(monday,monday.plusDays(6)).keySet());
            HorizontalScrollView legendScroll=new HorizontalScrollView(this);legendScroll.setHorizontalScrollBarEnabled(false);LinearLayout legend=d.row();
            for(String pkg:legendApps){LinearLayout row=d.row();View swatch=new View(this);swatch.setBackground(d.shape(d.appColor(pkg),4));row.addView(swatch,new LinearLayout.LayoutParams(d.dp(8),d.dp(8)));TextView name=text(AppController.label(this,pkg),12,d.muted,false);name.setPadding(d.dp(6),d.dp(6),d.dp(14),d.dp(6));row.addView(name);legend.addView(row);}legendScroll.addView(legend);d.add(content,legendScroll,8);
            line("Tap a day for details. Tap it again or outside to see the week.",12,d.muted,false,8);
            line(selectedDay==null?"By app · this week":"By app · "+selectedDay.format(DateTimeFormatter.ofPattern("EEEE")),20,d.ink,true,24);
        }else{line(today.format(DateTimeFormatter.ofPattern("EEEE, d MMM")),16,d.ink,true,24);line("Time used today",13,d.muted,false,6);}
        LinearLayout appRows=d.column();d.add(content,appRows,12);TextView empty=text("",13,d.muted,false);d.add(content,empty,12);
        final String[] previous={""};final Map<String,TextView> counters=new HashMap<>();
        bind(()->{
            var apps=e().history().byApp(start,end);long ms=e().history().total(start,end);empty.setVisibility(ms==0?View.VISIBLE:View.GONE);empty.setText(R.string.no_usage);
            Set<String> packages=new LinkedHashSet<>(e().selected);packages.addAll(apps.keySet());
            if(!previous[0].equals(packages.toString())){
                previous[0]=packages.toString();appRows.removeAllViews();counters.clear();
                for(String pkg:packages){
                    LinearLayout card=d.card(d.surface,week?12:16),row=d.row();
                    row.addView(d.badge(pkg),new LinearLayout.LayoutParams(d.dp(week?30:44),d.dp(week?30:44)));
                    LinearLayout words=d.column();words.setPadding(d.dp(12),0,d.dp(6),0);words.addView(text(AppController.label(this,pkg),16,d.ink,true));if(!week)d.add(words,text("Tracked today",12,d.muted,false),6);d.weighted(row,words);
                    TextView val=text("",week?20:26,d.ink,true);row.addView(val);card.addView(row);card.setGravity(Gravity.CENTER_VERTICAL);if(!week)card.setMinimumHeight(d.dp(100));d.add(appRows,card,counters.isEmpty()?0:10);counters.put(pkg,val);
                }
            }
            for(String pkg:packages)counters.get(pkg).setText(Design.usage(apps.getOrDefault(pkg,0L)));
        });
        line("Only tracked allowance time is included. Lunch and Stop periods are excluded. History stays on this phone.",12,d.muted,false,16);
    }
    private void settings() {
        heading("Your rhythm","Set limits that fit your day.");
        line("APP ALLOWANCES",12,d.muted,true,30);
        LinearLayout allowances=d.card(d.surface,16);
        for(String pkg:e().selected){LinearLayout row=d.row();row.setPadding(0,d.dp(8),0,d.dp(8));row.addView(d.badge(pkg),new LinearLayout.LayoutParams(d.dp(36),d.dp(36)));TextView name=text(AppController.label(this,pkg),16,d.ink,true);name.setPadding(d.dp(12),0,d.dp(8),0);d.weighted(row,name);row.addView(text(e().limit(pkg)/RulesEngine.MINUTE+" min",16,d.ink,true));allowances.addView(row);}
        d.add(content,allowances,16);line("Each app has its own 60-minute cooldown. Other apps keep their remaining allowance.",13,d.muted,false,12);
        StringJoiner selected=new StringJoiner(", ");for(String pkg:e().selected)selected.add(AppController.label(this,pkg));settingRow("chart","Selected apps",selected.toString(),this::chooseApps);
        line("DAILY SCHEDULE",12,d.muted,true,30);
        settingRow("clock","Lunch break",e().lunchEnabled?AppController.time(e().lunchMinute)+"–"+AppController.time((e().lunchMinute+60)%1440)+" · unrestricted":"Off",this::lunchDialog);
        if(e().lunchEnabled){LinearLayout note=d.card(d.mint,16);note.addView(text("Then a little space.",14,d.ink,true));d.add(note,text("Social apps cool down from "+AppController.time((e().lunchMinute+60)%1440)+" to "+AppController.time((e().lunchMinute+120)%1440)+".",12,d.muted,false),6);d.add(content,note,8);}
        settingRow("moon","Sleep Time",e().sleepEnabled?AppController.time(e().sleepStart)+"–"+AppController.time(e().sleepEnd)+" · notifications hidden":"Off",this::sleepDialog);
        line("Limits stay active during Sleep Time.",13,d.muted,false,12);
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
        line("Samsung controls live notification availability and layout. A standard timer notification is restored after dismissal while monitoring runs. Android still controls notification visibility.",12,d.muted,false,20);
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
