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
    private boolean appPickerOpen;
    private View lunchSettingsRow, sharedSettingsRow;
    private Dialog editorDialog;
    private int appPickerGeneration;
    private AlertDialog appPickerDialog;
    private final Map<String,String> appLabels = new HashMap<>();
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
        Appearance.apply(this);
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
        if(saved!=null){int restoredY=saved.getInt("scrollY");scroll.post(()->scroll.scrollTo(0,restoredY));}
    }
    @Override protected void onSaveInstanceState(Bundle state) { super.onSaveInstanceState(state); state.putInt("tab",tab); state.putBoolean("week",week); state.putInt("scrollY",scroll.getScrollY()); if(selectedDay!=null)state.putString("selectedDay",selectedDay.toString()); }
    private RulesEngine e() { return controller.engine; }
    private String appLabel(String pkg) { return appLabels.computeIfAbsent(pkg,key -> AppController.label(this,key)); }
    private void change(Runnable action) {
        try { action.run(); controller.refresh(); structure=""; render(); }
        catch (IllegalArgumentException | IllegalStateException ex) { structure=""; render(); message(ex.getMessage()); }
    }
    private RulesEngine.Mode mode() { return e().mode(AppController.wall(),AppController.elapsed()); }
    private TextView text(String value,int size,int color,boolean bold) { return d.text(value,size,color,bold); }
    private void line(String value,int size,int color,boolean bold,int top) { d.add(content,text(value,size,color,bold),top); }
    private void bind(Runnable runnable) { bindings.add(runnable); runnable.run(); }
    private void render() {
        LocalDate monday=LocalDate.now().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        if(selectedDay!=null && (selectedDay.isBefore(monday)||selectedDay.isAfter(monday.plusDays(6))))selectedDay=null;
        String key = e().timerMode()+":"+(tab==2?"settings":e().sharedLimit())+":"+e().isManualLunch()+":"+e().manualLunchUsedToday(AppController.wall())+":"+tab+":"+week+":"+selectedDay+":"+mode()+":"+controller.connected+":"+e().selected+":"+e().pendingAt()+":"+e().pendingLunchMinute()+":"+e().pendingLunchEnabled()+":"+e().quiet(AppController.wall())+":"+e().lunchEnabled+":"+e().lunchMinute+":"+e().sleepEnabled+":"+e().sleepStart+":"+e().sleepEnd+":"+LocalDate.now();
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
        LinearLayout title=d.row(),identity=d.column(),controls=d.column();title.setGravity(Gravity.TOP);
        boolean largeHeader=getResources().getConfiguration().fontScale>1.3f;int controlWidth=d.dp(largeHeader?112:86);
        identity.addView(text("SocialPause",28,d.ink,true));d.add(identity,text("A little social. A little more life.",14,d.muted,false),8);
        if(largeHeader){title.setOrientation(LinearLayout.VERTICAL);title.addView(identity,new LinearLayout.LayoutParams(-1,-2));}else d.weighted(title,identity);
        Button toggle=d.button(e().running?"Stop":"Start",false,()->change(()->{if(e().running)controller.stop();else controller.start();}));
        toggle.setMinWidth(d.dp(86));
        controls.addView(toggle,new LinearLayout.LayoutParams(controlWidth,-2));
        Design.ThemeSwitch appearance=d.new ThemeSwitch();appearance.setOnCheckedChangeListener((button,dark)->Appearance.setDark(this,dark));
        LinearLayout.LayoutParams appearanceSize=new LinearLayout.LayoutParams(controlWidth,d.dp(48));appearanceSize.topMargin=d.dp(8);controls.addView(appearance,appearanceSize);
        if(largeHeader){LinearLayout.LayoutParams controlsSize=new LinearLayout.LayoutParams(controlWidth,-2);controlsSize.gravity=Gravity.END;controlsSize.topMargin=d.dp(12);title.addView(controls,controlsSize);}else title.addView(controls);content.addView(title);
        TextView stopHint=text("",13,d.muted,false);d.add(content,stopHint,12);
        bind(()->{
            long remaining=e().stopLockRemaining(AppController.elapsed());
            boolean locked=e().running&&remaining>0;
            toggle.setEnabled(e().running?e().canStop(AppController.elapsed()):controller.connected);
            toggle.setAlpha(toggle.isEnabled()?1f:.45f);
            toggle.setStateDescription(locked?"Locked for "+AppController.stopDuration(remaining):e().running?"Available":controller.connected?"Ready":"App monitoring required");
            stopHint.setText(!e().running?"Starting locks Stop for 6 hours.":locked?"Stop available in "+AppController.stopDuration(remaining):"Stop is available. Monitoring continues until you stop it.");
        });
        timerModeSelector();
        LinearLayout summary=d.card(d.mint,20),row=d.row(),words=d.column();
        row.addView(d.icon("clock",d.ink),new LinearLayout.LayoutParams(d.dp(24),d.dp(24)));words.setPadding(d.dp(16),0,0,0);
        TextView status=text("",20,d.ink,true),note=text("",13,d.muted,false);words.addView(status);d.add(words,note,8);d.weighted(row,words);summary.addView(row);summary.setMinimumHeight(d.dp(98));d.add(content,summary,24);
        bind(()->{
            long wall=AppController.wall(),elapsed=AppController.elapsed();int available=e().availableCount(wall,elapsed);
            String heading,detail;
            if(!controller.connected){heading="Monitoring is off";detail="Enable App monitoring in Settings.";}
            else if(mode()==RulesEngine.Mode.STOPPED){heading="Tracking stopped";detail="Social apps are unrestricted until you Start.";}
            else if(mode()==RulesEngine.Mode.LUNCH){heading="Enjoy your lunch break";detail="Unrestricted until "+AppController.at(wall+e().countdown(wall,elapsed))+".";}
            else if(mode()==RulesEngine.Mode.LUNCH_COOLDOWN){heading="A little space after lunch";detail=allAppsHaveNoAllowance()?"Your apps have no allowance after the lunch block.":returningApps()+" return at "+AppController.at(wall+e().countdown(wall,elapsed))+".";}
            else if(mode()==RulesEngine.Mode.SHARED_COOLDOWN){heading="Shared allowance complete";detail=returningApps()+" return at "+AppController.at(wall+e().sharedCooldownRemaining(elapsed))+".";}
            else if(e().quiet(wall)){heading="Sleep Time is on";detail="Notifications hidden. App limits stay active.";}
            else if(available==0){heading=allAppsHaveNoAllowance()?"No app allowance":"Time for a breather";detail=allAppsHaveNoAllowance()?"Stop monitoring to set app limits in Settings.":"Apps with an allowance return after their cooldowns.";}
            else {heading=getResources().getQuantityString(R.plurals.apps_available,available,available);detail=e().timerMode()==RulesEngine.TimerMode.SHARED?"App limits and your shared allowance apply.":"Each app has its own timer and cooldown.";}
            status.setText(heading);note.setText(detail);
        });
        if(e().timerMode()==RulesEngine.TimerMode.SHARED)sharedCard();
        line("Your social apps",20,d.ink,true,24);for(String pkg:e().selected)appCard(pkg);
        line("Usage pauses when you leave an app or lock your phone. Cooldowns keep counting down.",12,d.muted,false,20);
        lunchCard();
        if(!controller.connected)banner("settings","Enable app monitoring","Tap to open setup.",20,()->{tab=2;structure="";render();});
    }
    private boolean allAppsHaveNoAllowance(){for(String pkg:e().selected)if(!e().noAllowance(pkg))return false;return true;}
    private String returningApps(){for(String pkg:e().selected)if(e().noAllowance(pkg))return "Apps with an allowance";return "All selected apps";}
    private void timerModeSelector() {
        LinearLayout card=d.card(d.surface,16);card.addView(text("TIMER MODE",12,d.muted,true));
        RadioGroup modes=new RadioGroup(this);modes.setOrientation(LinearLayout.HORIZONTAL);modes.setBackground(d.shape(d.line,24));modes.setPadding(d.dp(4),d.dp(4),d.dp(4),d.dp(4));modes.setContentDescription(getString(R.string.timer_mode));
        boolean shared=e().timerMode()==RulesEngine.TimerMode.SHARED;
        for(int i=0;i<2;i++){
            boolean value=i==1;RadioButton button=new RadioButton(this);button.setId(View.generateViewId());button.setButtonDrawable((android.graphics.drawable.Drawable)null);
            button.setText(value?R.string.shared_mode:R.string.individual_mode);button.setTextSize(14);button.setGravity(Gravity.CENTER);button.setPadding(d.dp(6),d.dp(10),d.dp(6),d.dp(10));button.setMinimumHeight(d.dp(48));
            button.setChecked(value==shared);button.setTextColor(value==shared?d.onAccent:d.ink);button.setBackground(d.shape(value==shared?d.accent:d.line,22));
            button.setEnabled(!e().running);button.setOnClickListener(v->change(()->e().setTimerMode(value?RulesEngine.TimerMode.SHARED:RulesEngine.TimerMode.INDIVIDUAL)));
            modes.addView(button,new RadioGroup.LayoutParams(0,-2,1));
        }
        d.add(card,modes,14);
        d.add(card,text(shared?"One allowance across selected apps. Each app keeps its own limit and cooldown.":"Each app has its own usage allowance and 60-minute cooldown.",14,d.muted,false),14);
        d.add(card,text(e().running?"Stop monitoring to change timer settings.":"App and shared allowances can be changed in Settings.",12,d.muted,false),12);
        d.add(content,card,20);
    }
    private void sharedCard() {
        LinearLayout card=d.card(d.accent,20);
        TextView label=text("SHARED TIME LEFT",12,d.onAccent,true),remaining=text("",40,d.onAccent,false),note=text("",13,d.onAccent,false);
        card.addView(label);d.add(card,remaining,12);d.add(card,note,10);d.add(content,card,20);
        bind(()->{
            long wall=AppController.wall(),elapsed=AppController.elapsed();RulesEngine.Mode current=mode();
            boolean lunchCooldown=current==RulesEngine.Mode.LUNCH_COOLDOWN;
            long cooldown=lunchCooldown?e().countdown(wall,elapsed):e().running?e().sharedCooldownRemaining(elapsed):0;
            label.setText(cooldown>0?(lunchCooldown?"AFTER-LUNCH COOLDOWN":"SHARED COOLDOWN REMAINING"):"SHARED TIME LEFT");
            remaining.setText(AppController.duration(cooldown>0?cooldown:e().sharedRemaining()));
            note.setText(cooldown>0?(allAppsHaveNoAllowance()?"Your apps still have no allowance after this block.":returningApps()+" return at "+AppController.at(wall+cooldown)+"."):
                    allAppsHaveNoAllowance()?"Set an app allowance in Settings to use shared time.":"of "+e().sharedLimit()/RulesEngine.MINUTE+" combined minutes"+(!e().running?" · monitoring stopped":current==RulesEngine.Mode.LUNCH?" · paused for lunch":e().focused()==null?" · paused":""));
        });
    }
    private void lunchCard() {
        LinearLayout card=d.card(d.surface,18),row=d.row();row.addView(d.icon("clock",d.ink),new LinearLayout.LayoutParams(d.dp(24),d.dp(24)));
        TextView heading=text("Lunch",22,d.ink,true);heading.setPadding(d.dp(12),0,0,0);d.weighted(row,heading);card.addView(row);
        TextView schedule=text("",13,d.muted,false),phase=text("",18,d.ink,true),counter=text("",28,d.ink,true),detail=text("",13,d.muted,false),availability=text("",12,d.muted,true);
        d.add(card,schedule,16);d.add(card,phase,20);d.add(card,counter,10);d.add(card,detail,12);
        Button manual=d.button("Start lunch",true,()->change(controller::startManualLunch));
        Button stop=d.button("Stop lunch",false,()->change(controller::stopLunch));
        if(getResources().getConfiguration().fontScale>1.3f){d.add(card,manual,20);d.add(card,stop,10);}
        else{LinearLayout actions=d.row();actions.addView(manual,new LinearLayout.LayoutParams(0,-2,1));LinearLayout.LayoutParams stopSize=new LinearLayout.LayoutParams(0,-2,1);stopSize.leftMargin=d.dp(12);actions.addView(stop,stopSize);d.add(card,actions,20);}
        d.add(card,availability,16);d.add(card,text("Start a 60-minute lunch now. Once per day; replaces a later scheduled lunch today.",12,d.muted,false),10);d.add(content,card,24);
        bind(()->{
            long wall=AppController.wall(),elapsed=AppController.elapsed();RulesEngine.Mode current=mode();
            boolean lunch=current==RulesEngine.Mode.LUNCH,afterLunch=current==RulesEngine.Mode.LUNCH_COOLDOWN,used=e().manualLunchUsedToday(wall);
            phase.setText(lunch?(e().isManualLunch()?"Manual lunch":"Scheduled lunch"):afterLunch?"After-lunch cooldown":"Your lunch, your timing");
            counter.setVisibility(lunch||afterLunch?View.VISIBLE:View.GONE);counter.setText(AppController.duration(e().countdown(wall,elapsed)));
            detail.setText(lunch?"Unrestricted until "+AppController.at(wall+e().countdown(wall,elapsed))+". Stop lunch starts a full 60-minute cooldown.":
                    afterLunch?(allAppsHaveNoAllowance()?"The lunch block ends at "+AppController.at(wall+e().countdown(wall,elapsed))+". Your apps still have no allowance.":returningApps()+" return at "+AppController.at(wall+e().countdown(wall,elapsed))+"."):"A manual start can replace the current lunch or cooldown immediately.");
            long next=e().nextScheduledLunch(wall);
            schedule.setText(next>0?"Next scheduled lunch: "+scheduleTime(next,wall):"Automatic lunch is off. Set a start time in Settings.");
            availability.setText(used?"Manual lunch used today.":!e().running||!controller.connected?"Start monitoring with App monitoring connected to use lunch.":"1 manual start available today.");
            stop.setEnabled(lunch&&e().running&&controller.connected);stop.setAlpha(stop.isEnabled()?1f:.45f);
            manual.setEnabled(e().running&&controller.connected&&e().manualLunchAvailable(wall));manual.setAlpha(manual.isEnabled()?1f:.45f);
        });
    }
    private String scheduleTime(long when,long now) {
        LocalDate date=Instant.ofEpochMilli(when).atZone(ZoneId.systemDefault()).toLocalDate();
        LocalDate today=Instant.ofEpochMilli(now).atZone(ZoneId.systemDefault()).toLocalDate();
        String day=date.equals(today)?"today":date.equals(today.plusDays(1))?"tomorrow":date.format(DateTimeFormatter.ofPattern("EEE, d MMM"));
        return day+" at "+AppController.at(when);
    }
    private void appCard(String pkg) {
        LinearLayout card=d.card(d.surface,16),row=d.row(),words=d.column(),counter=d.column();
        row.addView(d.badge(pkg),new LinearLayout.LayoutParams(d.dp(44),d.dp(44)));words.setPadding(d.dp(12),0,d.dp(6),0);
        words.addView(text(appLabel(pkg),16,d.ink,true));TextView limit=text(e().limit(pkg)/RulesEngine.MINUTE+" min per session",12,d.muted,false);d.add(words,limit,6);d.weighted(row,words);
        counter.setGravity(Gravity.END);TextView left=text("",32,d.ink,true),caption=text("",12,d.muted,false);counter.addView(left);d.add(counter,caption,3);caption.setGravity(Gravity.END);
        boolean largeText=getResources().getConfiguration().fontScale>1.3f||getResources().getConfiguration().screenWidthDp<360;
        if(largeText){card.addView(row);d.add(card,counter,12);}else{row.addView(counter);card.addView(row);}
        LinearLayout state=largeText?d.column():d.row();TextView pill=d.pill("",false),detail=text("",12,d.muted,false);state.addView(pill);
        if(largeText)d.add(state,detail,8);else{detail.setPadding(d.dp(10),0,0,0);d.weighted(state,detail);}d.add(card,state,16);
        Design.UsageBar bar=d.new UsageBar();LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,d.dp(4));lp.topMargin=d.dp(12);card.addView(bar,lp);d.add(content,card,12);
        bind(()->{
            long wall=AppController.wall(),elapsed=AppController.elapsed(),cooldown=e().cooldownRemaining(pkg,wall,elapsed);
            boolean free=!e().running||mode()==RulesEngine.Mode.LUNCH,active=pkg.equals(e().focused()),zero=e().noAllowance(pkg),cooling=cooldown>0&&!zero;
            String remaining=free?"Free":AppController.duration(cooling?cooldown:e().remaining(pkg));left.setText(remaining);
            caption.setText(free?"Unrestricted":zero?getString(R.string.no_allowance):cooling?"cooldown left":"usage left");
            String stateLabel=zero?getString(R.string.no_allowance):cooling?"Cooldown":active?"In use":e().used(pkg)>0?"Paused":"Available";
            pill.setText(stateLabel);pill.setBackground(d.shape(cooling?d.warning:d.mint,16));pill.setTextColor(cooling?d.warningInk:d.ink);
            String reason=mode()==RulesEngine.Mode.SHARED_COOLDOWN?"Shared allowance used":mode()==RulesEngine.Mode.LUNCH_COOLDOWN?"After lunch":"App limit reached";
            String description=zero?"Stop monitoring to change this app's limit.":cooling?reason+" · Ready at "+AppController.at(wall+cooldown):active?"Timer is running":e().used(pkg)>0?"Resumes when you return":"Ready when you are";
            detail.setText(description);state.setVisibility(free?View.GONE:View.VISIBLE);bar.setVisibility(free?View.GONE:View.VISIBLE);
            limit.setText(!e().running?"Tracking off":mode()==RulesEngine.Mode.LUNCH?"Lunch break":e().limit(pkg)/RulesEngine.MINUTE+" min per session");
            bar.setColor(cooling||zero?d.muted:d.appColor(pkg));bar.setFraction(free?0:zero?1:cooling?1-cooldown/(float)RulesEngine.COOLDOWN:e().used(pkg)/(float)e().limit(pkg));
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
            for(String pkg:legendApps){LinearLayout row=d.row();View swatch=new View(this);swatch.setBackground(d.shape(d.appColor(pkg),4));row.addView(swatch,new LinearLayout.LayoutParams(d.dp(8),d.dp(8)));TextView name=text(appLabel(pkg),12,d.muted,false);name.setPadding(d.dp(6),d.dp(6),d.dp(14),d.dp(6));row.addView(name);legend.addView(row);}legendScroll.addView(legend);d.add(content,legendScroll,8);
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
                    LinearLayout words=d.column();words.setPadding(d.dp(12),0,d.dp(6),0);words.addView(text(appLabel(pkg),16,d.ink,true));if(!week)d.add(words,text("Tracked today",12,d.muted,false),6);d.weighted(row,words);
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
        line("Set a usage limit for each app.",14,d.muted,false,10);
        line(e().running?"Stop monitoring to change timer settings.":"Both modes · 0 minutes blocks app usage.",12,d.muted,false,8);
        for(String pkg:e().selected)appAllowanceSlider(pkg);
        line("Each app has its own 1-hour cooldown. Shared allowance exhaustion also starts a 1-hour block across selected apps.",13,d.muted,false,18);
        line("TIMERS & SCHEDULE",12,d.muted,true,28);
        sharedSettingsRow=settingRow("clock","Shared allowance",hoursMinutes(e().configuredSharedLimit())+" total · your app limits still apply",this::sharedAllowanceDialog);
        lunchSettingsRow=settingRow("clock","Lunch break",e().pendingAt()>0&&e().pendingLunchEnabled()?AppController.time(e().pendingLunchMinute())+" from "+Instant.ofEpochMilli(e().pendingAt()).atZone(ZoneId.systemDefault()).toLocalDate():e().lunchEnabled?AppController.time(e().lunchMinute)+"–"+AppController.time((e().lunchMinute+60)%1440)+" · then a 1-hour block":"Off",this::lunchDialog);
        if(e().pendingAt()>0&&!e().pendingLunchEnabled())line("Automatic lunch switches off on "+Instant.ofEpochMilli(e().pendingAt()).atZone(ZoneId.systemDefault()).toLocalDate()+".",12,d.muted,false,8);
        settingRow("moon","Sleep Time",e().sleepEnabled?AppController.time(e().sleepStart)+"–"+AppController.time(e().sleepEnd)+" · notifications hidden":"Off",this::sleepDialog);
        line("Manual lunch is available on Home once per day, even after scheduled lunch.",13,d.muted,false,18);
        line("Lunch edits apply today when today's old and new starts are still ahead and lunch has not started; otherwise tomorrow. Active lunch and cooldown keep their deadlines.",12,d.muted,false,10);
        line("Limits stay active during Sleep Time.",12,d.muted,false,8);
        line("YOUR APPS",12,d.muted,true,28);
        StringJoiner selected=new StringJoiner(", ");for(String pkg:e().selected)selected.add(appLabel(pkg));settingRow("chart","Selected apps",selected.toString(),this::chooseApps);
        TextView stopAvailability=text("",12,d.muted,false);d.add(content,stopAvailability,12);
        bind(()->stopAvailability.setText(e().running&&e().stopLockRemaining(AppController.elapsed())>0?
                "Stop available in "+AppController.stopDuration(e().stopLockRemaining(AppController.elapsed()))+" on Home.":
                e().running?"Use Stop on Home when you need free access.":"Starting locks Stop for 6 hours. Lunch controls remain available."));
        line("APP SETUP",12,d.muted,true,30);
        settingRow("settings","App monitoring",controller.connected?"Connected":"Required · tap to enable",this::accessibilityDialog);
        settingRow("clock","Timer notifications",getSystemService(NotificationManager.class).areNotificationsEnabled()?"Enabled · hidden during Sleep Time":"Off · tap to enable",()->{
            if(checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED)requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS},1);
            else open(new Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE,getPackageName()));
        });
        settingRow("clock","Precise schedule alarms",getSystemService(AlarmManager.class).canScheduleExactAlarms()?"Allowed":"Optional · improves idle transitions",()->open(new Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,Uri.parse("package:"+getPackageName()))));
        settingRow("settings","Battery & app settings","Review if Samsung delays monitoring",()->open(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,Uri.parse("package:"+getPackageName()))));
        line("Samsung controls live notification availability and layout. A standard timer notification is restored after dismissal while monitoring runs. Android still controls notification visibility.",12,d.muted,false,20);
        line("Offline and personal. Accessibility reads app identity, not screen text. Stop is locked for 6 hours after Start. Android force-stop, reboot, uninstalling, or disabling Accessibility can still bypass limits. Blocking returns to phone Home; it cannot force-stop other apps or stop background audio.",12,d.muted,false,16);
    }
    private View settingRow(String icon,String title,String subtitle,Runnable action) {
        LinearLayout card=d.card(d.surface,18),row=d.row(),words=d.column();row.addView(d.icon(icon,d.ink),new LinearLayout.LayoutParams(d.dp(24),d.dp(24)));
        words.setPadding(d.dp(16),0,d.dp(10),0);words.addView(text(title,16,d.ink,true));d.add(words,text(subtitle,13,d.muted,false),8);d.weighted(row,words);
        row.addView(d.icon("chevron",d.muted),new LinearLayout.LayoutParams(d.dp(18),d.dp(18)));card.addView(row);card.setMinimumHeight(d.dp(82));card.setOnClickListener(v->action.run());card.setFocusable(true);d.add(content,card,16);return card;
    }
    private void appAllowanceSlider(String pkg) {
        LinearLayout card=d.card(d.surface,16),row=d.row();row.addView(d.badge(pkg),new LinearLayout.LayoutParams(d.dp(30),d.dp(30)));
        TextView name=text(appLabel(pkg),16,d.ink,true);name.setPadding(d.dp(10),0,d.dp(6),0);d.weighted(row,name);
        TextView value=text(AppController.duration(e().limit(pkg)),16,d.ink,true);row.addView(value);card.addView(row);
        SeekBar slider=new SeekBar(this);slider.setMax((int)(e().maximumLimit(pkg)/RulesEngine.MINUTE));slider.setProgress((int)(e().limit(pkg)/RulesEngine.MINUTE));slider.setMinimumHeight(d.dp(48));
        slider.setProgressTintList(android.content.res.ColorStateList.valueOf(d.appColor(pkg)));slider.setThumbTintList(android.content.res.ColorStateList.valueOf(d.appColor(pkg)));slider.setProgressBackgroundTintList(android.content.res.ColorStateList.valueOf(d.line));
        slider.setEnabled(!e().running);slider.setContentDescription(appLabel(pkg)+" allowance, minutes");
        slider.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){
            @Override public void onProgressChanged(SeekBar bar,int progress,boolean fromUser){
                if(!fromUser)return;
                try{e().setAppLimit(pkg,progress*RulesEngine.MINUTE);controller.refresh();value.setText(AppController.duration(e().limit(pkg)));}
                catch(IllegalStateException|IllegalArgumentException ex){bar.setProgress((int)(e().limit(pkg)/RulesEngine.MINUTE));message(ex.getMessage());}
            }
            @Override public void onStartTrackingTouch(SeekBar bar){}
            @Override public void onStopTrackingTouch(SeekBar bar){}
        });d.add(card,slider,8);
        LinearLayout endpoints=d.row();d.weighted(endpoints,text("0 min",12,d.muted,false));endpoints.addView(text(e().maximumLimit(pkg)/RulesEngine.MINUTE+" min max",12,d.muted,false));card.addView(endpoints);d.add(content,card,12);
    }
    private void accessibilityDialog() {
        new AlertDialog.Builder(this).setTitle("App monitoring permission")
                .setMessage("SocialPause uses Accessibility to identify the focused app, measure usage, and cover blocked apps with a brief limit message before returning to phone Home. It does not read or store screen text. Usage history stays on this phone.\n\nIf Android blocks this setting for a sideloaded app, open App info and look for Allow restricted settings, then return here.")
                .setNegativeButton("Cancel",null).setPositiveButton("Open settings",(dialog,which)->open(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))).show();
    }
    private void chooseApps() {
        if(appPickerOpen)return;
        if(e().running){message("Use Stop on Home before changing your app list.");return;}
        appPickerOpen=true;int generation=++appPickerGeneration;
        Set<String> original=new LinkedHashSet<>(e().selected);
        AlertDialog loading=new AlertDialog.Builder(this).setTitle("Your social apps").setMessage("Loading installed apps…").setNegativeButton("Cancel",null).create();
        appPickerDialog=loading;
        loading.setOnDismissListener(dialog->closeAppPicker(generation));loading.show();
        Context context=getApplicationContext();
        new Thread(()->{
            try {
                Set<String> packages=new HashSet<>(original);
                for(var info:context.getPackageManager().queryIntentActivities(new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER),0))packages.add(info.activityInfo.packageName);
                packages.removeAll(AppController.protectedPackages(context));
                Map<String,String> labels=new HashMap<>();for(String pkg:packages)labels.put(pkg,AppController.label(context,pkg));
                List<String> sorted=new ArrayList<>(packages);sorted.sort(Comparator.comparing((String pkg)->labels.get(pkg),String.CASE_INSENSITIVE_ORDER).thenComparing(pkg->pkg));
                runOnUiThread(()->{
                    if(!isCurrentAppPicker(generation))return;
                    appLabels.putAll(labels);loading.setOnDismissListener(null);loading.dismiss();
                    showAppPicker(generation,sorted,labels,original);
                });
            } catch(RuntimeException ex) {
                runOnUiThread(()->{if(isCurrentAppPicker(generation)){loading.dismiss();message("The app list could not be loaded. Please try again.");}});
            }
        },"app-list").start();
    }
    private boolean isCurrentAppPicker(int generation) { return appPickerOpen&&generation==appPickerGeneration&&!isFinishing()&&!isDestroyed(); }
    private void closeAppPicker(int generation) {
        if(generation!=appPickerGeneration)return;
        appPickerOpen=false;appPickerDialog=null;appPickerGeneration++;
    }
    private void showAppPicker(int generation,List<String> sorted,Map<String,String> labels,Set<String> original) {
        String[] names=sorted.stream().map(labels::get).toArray(String[]::new);
        boolean[] checked=new boolean[sorted.size()];Set<String> chosen=new LinkedHashSet<>(original);
        for(int i=0;i<checked.length;i++)checked[i]=chosen.contains(sorted.get(i));
        LinearLayout heading=d.column();heading.setPadding(d.dp(24),d.dp(20),d.dp(24),d.dp(8));heading.addView(text("Your social apps",20,d.ink,true));
        TextView error=text("",13,d.warningInk,false);error.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);error.setVisibility(View.GONE);d.add(heading,error,8);
        AlertDialog dialog=new AlertDialog.Builder(this).setCustomTitle(heading).setMultiChoiceItems(names,checked,(picker,index,enabled)->{
            if(enabled)chosen.add(sorted.get(index));else chosen.remove(sorted.get(index));error.setVisibility(View.GONE);
        }).setNegativeButton("Cancel",null).setPositiveButton("Save",null).create();
        appPickerDialog=dialog;dialog.setOnDismissListener(picker->closeAppPicker(generation));
        dialog.setOnShowListener(ignored->dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v->{
            if(!isCurrentAppPicker(generation))return;
            if(chosen.isEmpty()){error.setText(R.string.select_at_least_one_app);error.setVisibility(View.VISIBLE);return;}
            Button save=dialog.getButton(AlertDialog.BUTTON_POSITIVE);save.setEnabled(false);
            try {
                e().select(new LinkedHashSet<>(chosen));controller.refresh();dialog.dismiss();structure="";render();
            } catch(IllegalArgumentException|IllegalStateException ex) {
                error.setText(ex.getMessage());error.setVisibility(View.VISIBLE);save.setEnabled(true);
            }
        }));dialog.setCanceledOnTouchOutside(true);dialog.show();
    }
    private void restoreSettingsFocus(int y,boolean lunch) {
        structure="";render();scroll.post(()->{View row=lunch?lunchSettingsRow:sharedSettingsRow;if(row!=null)row.requestFocus();scroll.scrollTo(0,y);});
    }
    private void lunchDialog() {
        final int previousY=scroll.getScrollY();
        int minute=e().pendingAt()>0?e().pendingLunchMinute():e().lunchMinute;
        Dialog dialog=new Dialog(this);editorDialog=dialog;dialog.setTitle("Lunch starts");
        FrameLayout backdrop=new FrameLayout(this);backdrop.setPadding(d.dp(12),d.dp(20),d.dp(12),d.dp(20));backdrop.setOnClickListener(v->dialog.dismiss());
        LinearLayout panel=d.card(d.background,20);panel.setClickable(true);backdrop.addView(panel,new FrameLayout.LayoutParams(-1,-1));
        ScrollView body=new ScrollView(this);body.setFillViewport(false);LinearLayout form=d.column();body.addView(form);panel.addView(body,new LinearLayout.LayoutParams(-1,0,1));
        form.addView(text("Lunch starts",28,d.ink,true));d.add(form,text("Choose your daily lunch time.",15,d.muted,false),12);
        LinearLayout wheelCard=d.card(d.surface,16);wheelCard.addView(text("EVERY DAY",12,d.muted,true));
        LinearLayout labels=d.row();for(int label:new int[]{R.string.hour,R.string.minute,R.string.am_pm}){TextView name=text(getString(label),13,d.muted,false);name.setGravity(Gravity.CENTER);d.weighted(labels,name);}d.add(wheelCard,labels,20);
        FrameLayout wheelArea=new FrameLayout(this);View selected=new View(this);selected.setBackground(d.shape(d.mint,16));
        wheelArea.addView(selected,new FrameLayout.LayoutParams(-1,d.dp(64),Gravity.CENTER_VERTICAL));
        LinearLayout wheels=d.row();wheelArea.addView(wheels,new FrameLayout.LayoutParams(-1,-1));
        NumberPicker hour=wheel(1,12,(minute/60)%12==0?12:(minute/60)%12,getString(R.string.hour),false);
        NumberPicker minutes=wheel(0,59,minute%60,getString(R.string.minute),true);
        // Four alternating entries let the native three-row wheel wrap AM/PM independently.
        NumberPicker half=wheel(0,3,minute>=720?1:0,getString(R.string.am_pm),false);half.setDisplayedValues(new String[]{"AM","PM","AM","PM"});
        wheels.addView(hour,new LinearLayout.LayoutParams(0,-1,1));TextView colon=text(":",30,d.ink,false);colon.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);wheels.addView(colon,new LinearLayout.LayoutParams(d.dp(12),-2));wheels.addView(minutes,new LinearLayout.LayoutParams(0,-1,1));wheels.addView(half,new LinearLayout.LayoutParams(0,-1,1));
        LinearLayout.LayoutParams wheelSize=new LinearLayout.LayoutParams(-1,d.dp(getResources().getConfiguration().fontScale>1.3f?300:260));wheelSize.topMargin=d.dp(8);wheelCard.addView(wheelArea,wheelSize);
        TextView instruction=text("Scroll, swipe, or tap a time.",13,d.muted,false);instruction.setGravity(Gravity.CENTER);d.add(wheelCard,instruction,14);d.add(form,wheelCard,26);
        d.add(form,text("60 minutes of unrestricted lunch",18,d.ink,true),24);d.add(form,text("Then a 1-hour block.",15,d.muted,false),12);
        d.add(form,text("Changes apply today if lunch has not started and both the current and new start times are still ahead. Otherwise, they apply tomorrow.",14,d.muted,false),22);
        d.add(form,text("Active lunch and cooldown keep their deadlines. Nothing changes until Save.",13,d.muted,false),12);
        LinearLayout actions=d.row();Button cancel=d.button(getString(R.string.cancel),false,dialog::dismiss);
        Button save=d.button(getString(R.string.save),true,()->{
            hour.clearFocus();minutes.clearFocus();half.clearFocus();
            int selectedMinute=(hour.getValue()%12)*60+minutes.getValue()+(half.getValue()%2)*720;
            try{e().lunch(true,selectedMinute,AppController.wall(),AppController.elapsed());controller.refresh();dialog.dismiss();}
            catch(IllegalArgumentException|IllegalStateException ex){message(ex.getMessage());}
        });
        actions.addView(cancel,new LinearLayout.LayoutParams(0,-2,1));LinearLayout.LayoutParams saveSize=new LinearLayout.LayoutParams(0,-2,1);saveSize.leftMargin=d.dp(12);actions.addView(save,saveSize);d.add(panel,actions,16);
        NumberPicker[] pickers={hour,minutes,half};boolean[] scrolling=new boolean[3];
        for(int i=0;i<pickers.length;i++){final int index=i;pickers[i].setOnScrollListener((picker,state)->{scrolling[index]=state!=NumberPicker.OnScrollListener.SCROLL_STATE_IDLE;save.setEnabled(!scrolling[0]&&!scrolling[1]&&!scrolling[2]);save.setAlpha(save.isEnabled()?1f:.5f);});}
        dialog.setContentView(backdrop);dialog.setCanceledOnTouchOutside(true);dialog.setOnDismissListener(ignored->{editorDialog=null;if(!isFinishing()&&!isDestroyed())restoreSettingsFocus(previousY,true);});
        dialog.setOnKeyListener((ignored,key,event)->{if(key==KeyEvent.KEYCODE_ESCAPE){if(event.getAction()==KeyEvent.ACTION_UP)dialog.dismiss();return true;}return false;});
        dialog.show();Window window=dialog.getWindow();if(window!=null){window.setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT));window.setLayout(-1,-1);}
    }
    private NumberPicker wheel(int min,int max,int value,String label,boolean padded) {
        NumberPicker picker=new NumberPicker(this);picker.setMinValue(min);picker.setMaxValue(max);picker.setWrapSelectorWheel(true);
        if(padded)picker.setFormatter(number->String.format(Locale.getDefault(),"%02d",number));
        picker.setValue(value);picker.setContentDescription(label);picker.setTextColor(d.ink);picker.setSelectionDividerHeight(0);
        picker.setTextSize(android.util.TypedValue.applyDimension(android.util.TypedValue.COMPLEX_UNIT_SP,label.equals(getString(R.string.am_pm))?24:32,getResources().getDisplayMetrics()));
        picker.setOnKeyListener((view,key,event)->wheelKey(picker,key,event));
        for(int i=0;i<picker.getChildCount();i++){View child=picker.getChildAt(i);child.setOnKeyListener((view,key,event)->wheelKey(picker,key,event));}
        return picker;
    }
    private boolean wheelKey(NumberPicker picker,int key,KeyEvent event) {
        if(key!=KeyEvent.KEYCODE_DPAD_UP&&key!=KeyEvent.KEYCODE_DPAD_DOWN)return false;
        if(event.getAction()==KeyEvent.ACTION_DOWN){
            picker.clearFocus();int value=picker.getValue()+(key==KeyEvent.KEYCODE_DPAD_UP?1:-1);
            if(value>picker.getMaxValue())value=picker.getWrapSelectorWheel()?picker.getMinValue():picker.getMaxValue();
            if(value<picker.getMinValue())value=picker.getWrapSelectorWheel()?picker.getMaxValue():picker.getMinValue();picker.setValue(value);picker.requestFocus();
        }return true;
    }
    private static String hoursMinutes(long millis) {
        long minutes=millis/RulesEngine.MINUTE;return String.format(Locale.getDefault(),"%02d:%02d",minutes/60,minutes%60);
    }
    private void sharedAllowanceDialog() {
        final int previousY=scroll.getScrollY();boolean editable=!e().running&&e().timerMode()==RulesEngine.TimerMode.SHARED;
        Dialog dialog=new Dialog(this);editorDialog=dialog;dialog.setTitle("Shared allowance");
        LinearLayout panel=d.card(d.surface,24);View handle=new View(this);handle.setBackground(d.shape(d.line,4));LinearLayout.LayoutParams handleSize=new LinearLayout.LayoutParams(d.dp(48),d.dp(4));handleSize.gravity=Gravity.CENTER;panel.addView(handle,handleSize);
        LinearLayout heading=d.row(),number=d.column();boolean largeText=getResources().getConfiguration().fontScale>1.3f;
        TextView title=text("Shared allowance",22,d.ink,true);if(largeText){heading.setOrientation(LinearLayout.VERTICAL);heading.addView(title,new LinearLayout.LayoutParams(-1,-2));}else d.weighted(heading,title);
        TextView value=text(hoursMinutes(e().configuredSharedLimit()),28,d.ink,true);number.setGravity(Gravity.END);number.addView(value);d.add(number,text("HH:MM",11,d.muted,false),6);
        if(largeText)d.add(heading,number,16);else heading.addView(number);d.add(panel,heading,24);
        d.add(panel,text("Total usage across selected apps per session. Each app keeps its configured usage limit.",14,d.muted,false),20);
        SeekBar slider=new SeekBar(this);slider.setMin(1);slider.setMax(30);slider.setProgress((int)(e().configuredSharedLimit()/RulesEngine.MINUTE));slider.setMinimumHeight(d.dp(56));
        slider.setProgressTintList(android.content.res.ColorStateList.valueOf(d.accent));slider.setThumbTintList(android.content.res.ColorStateList.valueOf(d.accent));slider.setProgressBackgroundTintList(android.content.res.ColorStateList.valueOf(d.line));
        slider.setEnabled(editable);slider.setAlpha(editable?1f:.45f);slider.setContentDescription("Shared allowance, minutes");d.add(panel,slider,24);
        slider.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){
            @Override public void onProgressChanged(SeekBar bar,int progress,boolean fromUser){if(!fromUser)return;
                try{e().setSharedLimit(progress*RulesEngine.MINUTE);controller.refresh();value.setText(hoursMinutes(e().configuredSharedLimit()));}
                catch(IllegalArgumentException|IllegalStateException ex){bar.setProgress((int)(e().configuredSharedLimit()/RulesEngine.MINUTE));message(ex.getMessage());}
            }
            @Override public void onStartTrackingTouch(SeekBar bar){}
            @Override public void onStopTrackingTouch(SeekBar bar){}
        });
        LinearLayout endpoints=d.row();d.weighted(endpoints,text("00:01",12,d.muted,false));endpoints.addView(text("00:30",12,d.muted,false));panel.addView(endpoints);
        d.add(panel,text(e().running?"Stop monitoring to change timer settings.":!editable?"Select Shared mode on Home to change this allowance.":"Saved immediately. Applies when you Start monitoring.",13,d.muted,false),22);
        d.add(panel,d.button(getString(R.string.done),true,dialog::dismiss),28);
        ScrollView body=new ScrollView(this);body.addView(panel);dialog.setContentView(body);dialog.setCanceledOnTouchOutside(true);dialog.setOnDismissListener(ignored->{editorDialog=null;if(!isFinishing()&&!isDestroyed())restoreSettingsFocus(previousY,false);});
        dialog.setOnKeyListener((ignored,key,event)->{if(key==KeyEvent.KEYCODE_ESCAPE){if(event.getAction()==KeyEvent.ACTION_UP)dialog.dismiss();return true;}return false;});
        dialog.show();Window window=dialog.getWindow();if(window!=null){window.setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT));window.setGravity(Gravity.BOTTOM);window.setLayout(-1,-2);}
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
    @Override protected void onDestroy(){
        handler.removeCallbacks(tick);appPickerGeneration++;appPickerOpen=false;
        if(appPickerDialog!=null){appPickerDialog.setOnDismissListener(null);appPickerDialog.dismiss();appPickerDialog=null;}
        if(editorDialog!=null){editorDialog.setOnDismissListener(null);editorDialog.dismiss();editorDialog=null;}
        super.onDestroy();
    }
}
