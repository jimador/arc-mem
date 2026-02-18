package dev.dunnam.diceanchors.anchor.event;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import dev.dunnam.diceanchors.anchor.Authority;
import dev.dunnam.diceanchors.anchor.ConflictResolver;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("AnchorLifecycleListener")
class AnchorLifecycleListenerTest {

    private final AnchorLifecycleListener listener = new AnchorLifecycleListener();
    private final Logger logger = (Logger) LoggerFactory.getLogger(AnchorLifecycleListener.class);
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
        listener.onPromoted(new AnchorPromotedEvent(this, "ctx", "p1", "a1", 500));
        listener.onReinforced(new AnchorReinforcedEvent(this, "ctx", "a1", 500, 550, 2));
        listener.onArchived(new AnchorArchivedEvent(this, "ctx", "a1", ArchiveReason.MANUAL));
        listener.onConflictDetected(new ConflictDetectedEvent(this, "ctx", "incoming", 1, List.of("a1")));
        listener.onConflictResolved(new ConflictResolvedEvent(this, "ctx", "a1", ConflictResolver.Resolution.COEXIST));
        listener.onAuthorityUpgraded(new AuthorityUpgradedEvent(this, "ctx", "a1", Authority.PROVISIONAL, Authority.UNRELIABLE, 2));

        var events = appender.list;
        assertThat(events).hasSize(6);
        assertThat(events).allMatch(event -> event.getLevel() == Level.INFO);
        assertThat(events).allMatch(event -> event.getFormattedMessage().contains("[LIFECYCLE]"));
    }
}
