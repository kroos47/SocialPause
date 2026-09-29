import app.socialpause.engine.*;
import java.lang.management.ManagementFactory;
import java.io.*;
import java.time.*;
import java.util.*;
public final class SocialPausePerf {
 public static void main(String[] args)throws Exception {
  boolean optimized=Boolean.parseBoolean(args[0]);
  var bean=(com.sun.management.ThreadMXBean)ManagementFactory.getThreadMXBean();bean.setThreadAllocatedMemoryEnabled(true);
  System.out.println("days,scenario,operations,serializations,serialized_bytes,history_queries,cpu_ms,allocated_bytes");
  for(int days:new int[]{7,365,1095}){
   var e=new RulesEngine();e.lunchEnabled=false;e.sleepEnabled=false;
   var now=LocalDate.of(2026,9,29);var zone=ZoneId.of("UTC");
   for(int i=0;i<days;i++)for(String app:List.of("com.instagram.android","com.twitter.android","com.reddit.frontpage"))
    e.history().record(app,now.minusDays(i).atTime(12,0).atZone(zone).toInstant().toEpochMilli(),180_000,zone);
   byte[] checkpoint=serialize(e); // initialization, outside unchanged workload
   for(int i=0;i<30;i++){serialize(e);e.history().byApp(now.minusDays(6),now);}
   long id=Thread.currentThread().getId(),cpu=bean.getCurrentThreadCpuTime(),allocated=bean.getThreadAllocatedBytes(id),bytes=0;int serializations=0;
   Object gate=null;java.lang.reflect.Method needs=null,saved=null;
   if(optimized){var cls=Class.forName("app.socialpause.engine.PersistenceCheckpoint");gate=cls.getConstructor().newInstance();needs=cls.getMethod("needsSave",RulesEngine.class);saved=cls.getMethod("saved",RulesEngine.class);saved.invoke(gate,e);}
   for(int i=0;i<1000;i++)if(!optimized||(Boolean)needs.invoke(gate,e)){bytes+=serialize(e).length;serializations++;if(optimized)saved.invoke(gate,e);}
   output(days,"idle-save",1000,serializations,bytes,0,bean.getCurrentThreadCpuTime()-cpu,bean.getThreadAllocatedBytes(id)-allocated);
   cpu=bean.getCurrentThreadCpuTime();allocated=bean.getThreadAllocatedBytes(id);long checksum=0;
   for(int i=0;i<200;i++){for(int d=0;d<7;d++)checksum+=e.history().byApp(now.minusDays(d),now.minusDays(d)).size();checksum+=e.history().total(now.minusDays(6),now);}
   if(checksum==0)throw new AssertionError();
   output(days,"insights-query",200,0,0,1600,bean.getCurrentThreadCpuTime()-cpu,bean.getThreadAllocatedBytes(id)-allocated);
   // Warm this retained workload equally: the optimized idle path no longer warms serialization.
   for(int warm=0;warm<1000;warm++)serialize(e);
   cpu=bean.getCurrentThreadCpuTime();allocated=bean.getThreadAllocatedBytes(id);bytes=0;
   long wall=now.atStartOfDay(zone).toInstant().toEpochMilli();e.start(wall,10_000);e.focus("com.twitter.android",true,wall,10_000);
   for(int i=1;i<=40;i++){e.advance(wall+i*500,10_000+i*500);bytes+=serialize(e).length;}
   output(days,"active-save",40,40,bytes,0,bean.getCurrentThreadCpuTime()-cpu,bean.getThreadAllocatedBytes(id)-allocated);
  }
 }
 static byte[] serialize(Object e)throws Exception{var bytes=new ByteArrayOutputStream();try(var out=new ObjectOutputStream(bytes)){out.writeObject(e);}return bytes.toByteArray();}
 static void output(int d,String s,int op,int saves,long bytes,int queries,long cpu,long alloc){System.out.printf(Locale.ROOT,"%d,%s,%d,%d,%d,%d,%.3f,%d%n",d,s,op,saves,bytes,queries,cpu/1e6,alloc);}
}
