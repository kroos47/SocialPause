package app.socialpause.engine;

import static app.socialpause.engine.EngineTests.*;

/** Startup evidence must identify this process even when a historical PID has been reused. */
final class StartupRecoveryTests {
    static void run() {
        test("startup binding before Java initialization still identifies the current process",()->{
            eq(true,ProcessStartEvidence.isCurrent(1000,1020,1050,1005_000_000L,null));
            eq(true,ProcessStartEvidence.isCurrent(1000,1020,1050,null,1030_000_000L));
            eq(true,ProcessStartEvidence.isCurrent(1000,1020,1050,1000_000_000L,null));
            eq(true,ProcessStartEvidence.isCurrent(1000,1020,1050,null,1050_000_000L));
        });
        test("reused PID with old startup timestamps cannot identify a new process",()->{
            eq(false,ProcessStartEvidence.isCurrent(1000,1020,1050,100_000_000L,150_000_000L));
            eq(false,ProcessStartEvidence.isCurrent(1000,1020,1050,999_999_999L,1019_999_999L));
            eq(false,ProcessStartEvidence.isCurrent(1000,1020,1050,null,null));
        });
        test("future timestamps and invalid process clocks are not positive recovery evidence",()->{
            eq(false,ProcessStartEvidence.isCurrent(1000,1020,1050,1060_000_000L,1070_000_000L));
            eq(false,ProcessStartEvidence.isCurrent(0,1020,1050,1005_000_000L,1030_000_000L));
            eq(false,ProcessStartEvidence.isCurrent(1000,999,1050,1005_000_000L,null));
            eq(false,ProcessStartEvidence.isCurrent(1000,1020,1019,1005_000_000L,null));
            eq(false,ProcessStartEvidence.isCurrent(1000,1020,1050,0L,-1L));
        });
    }
    public static void main(String[] args) {run();System.out.println(tests+" startup evidence scenarios passed.");}
}
