package dev.arcmem.simulator.ui.panels;
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

import dev.arcmem.simulator.engine.*;
import dev.arcmem.simulator.history.*;
import dev.arcmem.simulator.scenario.*;
import dev.arcmem.simulator.ui.controllers.*;
import dev.arcmem.simulator.ui.views.*;

import com.vaadin.flow.component.ClientCallable;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;

/**
 * Banner displayed after resuming from a paused simulation, showing
 * the number of interventions made and the unit count delta.
 * <p>
 * Styled with green for positive unit deltas, magenta for negative.
 * Automatically dismisses after 10 seconds or on the next turn, or
 * can be manually dismissed via a close button.
 */
public class InterventionImpactBanner extends HorizontalLayout {

    public InterventionImpactBanner() {
        addClassName("ar-banner");
        setWidthFull();
        setVisible(false);
        setPadding(true);
        setSpacing(true);
        setAlignItems(Alignment.CENTER);
    }

    /**
     * Show the banner with intervention impact information.
     *
     * @param interventionCount number of interventions during the pause session
     * @param unitDelta       change in active unit count (positive = added, negative = removed)
     */
    public void show(int interventionCount, int unitDelta) {
        removeAll();

        var deltaAttr = unitDelta > 0 ? "positive" : unitDelta < 0 ? "negative" : "neutral";
        getElement().setAttribute("data-delta", deltaAttr);

        var icon = new Span(unitDelta > 0 ? "\u2191" : unitDelta < 0 ? "\u2193" : "\u2194");
        icon.addClassName("ar-banner-icon");
        icon.getElement().setAttribute("data-delta", deltaAttr);

        var message = new Span("%d intervention%s | MemoryUnit delta: %s%d".formatted(
                interventionCount,
                interventionCount == 1 ? "" : "s",
                unitDelta >= 0 ? "+" : "",
                unitDelta));
        message.addClassName("ar-banner-message");

        var closeButton = new Button("\u2715");
        closeButton.addThemeVariants(ButtonVariant.LUMO_TERTIARY, ButtonVariant.LUMO_SMALL);
        closeButton.addClickListener(e -> dismiss());

        add(icon, message, closeButton);
        setVisible(true);

        // Auto-dismiss after 10 seconds
        var ui = UI.getCurrent();
        if (ui != null) {
            ui.access(() -> ui.getPage().executeJs(
                    "setTimeout(() => $0.$server.dismiss(), 10000)",
                    getElement()));
        }
    }

    /**
     * Dismiss the banner. Called on close button click, auto-timeout, or next turn.
     */
    @ClientCallable
    public void dismiss() {
        setVisible(false);
        removeAll();
    }
}
