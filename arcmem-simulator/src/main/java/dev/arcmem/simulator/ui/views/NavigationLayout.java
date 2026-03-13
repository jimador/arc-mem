package dev.arcmem.simulator.ui.views;
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
import dev.arcmem.simulator.ui.panels.*;

import dev.arcmem.simulator.chat.ChatView;

import com.vaadin.flow.component.applayout.AppLayout;
import com.vaadin.flow.component.html.H1;
import com.vaadin.flow.component.html.Nav;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.router.RouterLink;

/**
 * Application-wide navigation layout with a horizontal nav bar.
 * <p>
 * Provides links to the three main views: Simulation ({@code /}),
 * Chat ({@code /chat}), and Run Inspector ({@code /run}).
 * Styled with the retro theme (dark background, cyan accent for links).
 * <p>
 * This layout is available for use by any view via
 * {@code @Route(value = "...", layout = NavigationLayout.class)},
 * but existing views are not modified to preserve their current route annotations.
 */
public class NavigationLayout extends AppLayout {

    public NavigationLayout() {
        var brand = new H1("arc-mem");
        brand.getStyle()
             .set("font-size", "var(--lumo-font-size-l)")
             .set("margin", "0")
             .set("color", "var(--unit-accent-cyan)")
             .set("white-space", "nowrap");

        var simLink = createNavLink("Simulation", "", true);
        var chatLink = createNavLink("Chat", "chat", false);
        var runLink = createNavLink("Run Inspector", "run", false);

        var nav = new Nav();
        var navLinks = new HorizontalLayout(simLink, chatLink, runLink);
        navLinks.setSpacing(true);
        navLinks.getStyle().set("margin-left", "24px");
        nav.add(navLinks);

        var header = new HorizontalLayout(brand, nav);
        header.setAlignItems(HorizontalLayout.Alignment.CENTER);
        header.setWidthFull();
        header.getStyle()
              .set("padding", "8px 16px")
              .set("background", "var(--unit-surface)")
              .set("border-bottom", "1px solid var(--unit-border)");

        addToNavbar(header);
    }

    private RouterLink createNavLink(String text, String route, boolean highlighted) {
        var link = new RouterLink(text, getRouteClass(route));
        link.getStyle()
            .set("color", "var(--unit-accent-cyan)")
            .set("text-decoration", "none")
            .set("font-size", "var(--lumo-font-size-s)")
            .set("font-weight", "600")
            .set("padding", "4px 8px")
            .set("border-radius", "var(--lumo-border-radius-s)");
        return link;
    }

    /**
     * Resolve route string to a Vaadin view class for RouterLink.
     * Falls back to creating a simple href-based link if the class is not found.
     */
    private Class<? extends com.vaadin.flow.component.Component> getRouteClass(String route) {
        return switch (route) {
            case "" -> SimulationView.class;
            case "chat" -> ChatView.class;
            case "run" -> RunInspectorView.class;
            default -> SimulationView.class;
        };
    }
}
