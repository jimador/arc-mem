package dev.arcmem.core.memory.event;
import dev.arcmem.core.memory.budget.*;
import dev.arcmem.core.memory.canon.*;
import dev.arcmem.core.memory.conflict.*;
import dev.arcmem.core.memory.engine.*;
import dev.arcmem.core.memory.maintenance.*;
import dev.arcmem.core.memory.model.*;
import dev.arcmem.core.memory.mutation.*;
import dev.arcmem.core.memory.trust.*;
import dev.arcmem.core.assembly.budget.*;
import dev.arcmem.core.assembly.compaction.*;
import dev.arcmem.core.assembly.compliance.*;
import dev.arcmem.core.assembly.protection.*;
import dev.arcmem.core.assembly.retrieval.*;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("MemoryUnitLifecycleListener")
class MemoryUnitLifecycleListenerTest {

    private final MemoryUnitLifecycleListener listener = new MemoryUnitLifecycleListener();
    private final Logger logger = (Logger) LoggerFactory.getLogger(MemoryUnitLifecycleListener.class);
    private final ListAppender<ILoggingEvent> appender = new ListAppender<>();

    @BeforeEach
    void setUp() {
        appender.start();
        logger.addAppender(appender);
    }

    @AfterEach
    void tearDown() {
        logger.detachAppender(appender);
    }

    @Test
    @DisplayName("logs each lifecycle event with [LIFECYCLE] prefix at INFO")
    void logsLifecycleEventsAtInfo() {
        listener.onPromoted(MemoryUnitLifecycleEvent.promoted(this, "ctx", "p1", "a1", 500));
        listener.onReinforced(MemoryUnitLifecycleEvent.reinforced(this, "ctx", "a1", 500, 550, 2));
        listener.onArchived(MemoryUnitLifecycleEvent.archived(this, "ctx", "a1", ArchiveReason.MANUAL));
        listener.onConflictDetected(MemoryUnitLifecycleEvent.conflictDetected(this, "ctx", "incoming", 1, List.of("a1")));
        listener.onConflictResolved(MemoryUnitLifecycleEvent.conflictResolved(this, "ctx", "a1", ConflictResolver.Resolution.COEXIST));
        listener.onAuthorityChanged(MemoryUnitLifecycleEvent.authorityChanged(this, "ctx", "a1", Authority.PROVISIONAL, Authority.UNRELIABLE, AuthorityChangeDirection.PROMOTED, "reinforcement"));

        var events = appender.list;
        assertThat(events).hasSize(6);
        assertThat(events).allMatch(event -> event.getLevel() == Level.INFO);
        assertThat(events).allMatch(event -> event.getFormattedMessage().contains("[LIFECYCLE]"));
    }
}
