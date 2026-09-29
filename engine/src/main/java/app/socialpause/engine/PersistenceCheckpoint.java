package app.socialpause.engine;

/** Skip encoding before doing any serialization work. Never shared across threads. */
public final class PersistenceCheckpoint {
    private RulesEngine savedEngine;
    private long stateRevision, historyRevision;
    public boolean needsSave(RulesEngine engine) {
        return engine != savedEngine || stateRevision != engine.revision() || historyRevision != engine.historyRevision();
    }
    public void saved(RulesEngine engine) {
        savedEngine = engine; stateRevision = engine.revision(); historyRevision = engine.historyRevision();
    }
}
