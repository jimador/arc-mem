package dev.arcmem.simulator.chat;
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

import com.embabel.agent.api.channel.MessageOutputChannelEvent;
import com.embabel.agent.api.channel.OutputChannel;
import com.embabel.agent.api.channel.OutputChannelEvent;
import com.embabel.agent.api.channel.ProgressOutputChannelEvent;
import com.embabel.chat.AssistantMessage;
import com.embabel.chat.ChatSession;
import com.embabel.chat.Chatbot;
import com.embabel.chat.Message;
import com.embabel.chat.UserMessage;
import com.embabel.dice.proposition.PropositionStatus;
import com.vaadin.flow.component.AttachEvent;
import com.vaadin.flow.component.DetachEvent;
import com.vaadin.flow.component.Key;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.H4;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.Scroller;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.progressbar.ProgressBar;
import com.vaadin.flow.component.splitlayout.SplitLayout;
import com.vaadin.flow.component.tabs.TabSheet;
import com.vaadin.flow.component.textfield.TextArea;
import com.vaadin.flow.component.textfield.IntegerField;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.router.BeforeEnterEvent;
import com.vaadin.flow.router.BeforeEnterObserver;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.router.RouterLink;
import com.vaadin.flow.server.VaadinSession;
import dev.arcmem.core.config.ArcMemProperties;
import dev.arcmem.core.memory.event.ArchiveReason;
import dev.arcmem.core.persistence.MemoryUnitRepository;
import dev.arcmem.core.persistence.PropositionNode;
import dev.arcmem.core.persistence.PropositionView;
import dev.arcmem.core.prompt.PromptTemplates;
import org.commonmark.parser.Parser;
import org.commonmark.renderer.html.HtmlRenderer;
import org.drivine.manager.CascadeType;
import org.drivine.manager.GraphObjectManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;

/**
 * Vaadin chat view for the arc-mem demo.
 * <p>
 * Follows the Impromptu VaadinChatView pattern:
 * Thread + BlockingQueue + @Push + ui.access() for async LLM responses.
 * The Embabel {@link Chatbot} drives the agent process; {@link ChatActions}
 * assembles the unit context on each turn.
 * <p>
 * Session state is stored in {@link VaadinSession} to survive navigation.
 */
@Route("chat")
@PageTitle("Dice Units — Bigby the DM")
public class ChatView extends VerticalLayout implements BeforeEnterObserver {

    private static final Logger logger = LoggerFactory.getLogger(ChatView.class);
    private static final int RESPONSE_TIMEOUT_SECONDS = 120;
    private static final int ASYNC_SIDEBAR_REFRESH_ATTEMPTS = 24;
    private static final long ASYNC_SIDEBAR_REFRESH_INTERVAL_MILLIS = 5_000L;
    private static final String SESSION_ATTR_CHAT = "arcMemChatSession";
    private static final String SESSION_ATTR_CONVERSATION_ID = "arcMemConversationId";

    private final Chatbot chatbot;
    private final ArcMemEngine arcMemEngine;
    private final MemoryUnitRepository contextUnitRepository;
    private final GraphObjectManager graphObjectManager;
    private final ArcMemProperties properties;
    private final CompliancePolicy compliancePolicy;
    private final TokenCounter tokenCounter;
    private final RelevanceScorer relevanceScorer;
    private final MemoryUnitMutationStrategy mutationStrategy;
    private final ConversationService conversationService;
    private final Parser markdownParser = Parser.builder().build();
    private final HtmlRenderer htmlRenderer = HtmlRenderer.builder().build();

    private VerticalLayout messagesLayout;
    private Scroller messagesScroller;
    private TextField inputField;
    private Button sendButton;
    private Div thinkingIndicator;
    private TextField conversationIdField;
    private TextField resumeIdField;

    private VerticalLayout unitsTabContent;
    private VerticalLayout propositionsTabContent;
    private VerticalLayout knowledgeTabContent;
    private VerticalLayout sessionInfoTabContent;
    private VerticalLayout contextTabContent;
    private volatile Thread sidebarRefreshThread;
    private final Map<String, Div> unitCards = new LinkedHashMap<>();
    private final Map<String, MemoryUnit> renderedUnits = new LinkedHashMap<>();
    private Span unitsHeader;
    private Div createUnitForm;
    private int turnCount = 0;
    private String activeConversationId;

    public ChatView(Chatbot chatbot, ArcMemEngine arcMemEngine, MemoryUnitRepository contextUnitRepository,
                    GraphObjectManager graphObjectManager,
                    ArcMemProperties properties,
                    CompliancePolicy compliancePolicy,
                    TokenCounter tokenCounter,
                    RelevanceScorer relevanceScorer,
                    MemoryUnitMutationStrategy mutationStrategy,
                    ConversationService conversationService) {
        this.chatbot = chatbot;
        this.arcMemEngine = arcMemEngine;
        this.contextUnitRepository = contextUnitRepository;
        this.graphObjectManager = graphObjectManager;
        this.properties = properties;
        this.compliancePolicy = compliancePolicy;
        this.tokenCounter = tokenCounter;
        this.relevanceScorer = relevanceScorer;
        this.mutationStrategy = mutationStrategy;
        this.conversationService = conversationService;
        setSizeFull();
        setPadding(false);
        setSpacing(false);
        buildUI();
    }

    @Override
    protected void onAttach(AttachEvent attachEvent) {
        super.onAttach(attachEvent);
        restoreConversation();
    }

    @Override
    protected void onDetach(DetachEvent detachEvent) {
        cancelAsyncSidebarRefresh();
        super.onDetach(detachEvent);
    }

    @Override
    public void beforeEnter(BeforeEnterEvent event) {
        event.getLocation().getQueryParameters()
                .getParameters()
                .getOrDefault("conversationId", List.of())
                .stream()
                .findFirst()
                .ifPresent(id -> {
                    var existing = conversationService.findConversation(id);
                    if (existing.isPresent()) {
                        activeConversationId = id;
                        storeConversationIdInSession();
                    } else {
                        logger.warn("Conversation {} from query param not found", id);
                    }
                });
    }

    private void buildUI() {
        var header = new H2("Bigby — Your D&D Dungeon Master");
        header.addClassName("ar-chat-header");

        var benchmarkLink = new RouterLink("Benchmark", dev.arcmem.simulator.ui.views.BenchmarkView.class);
        benchmarkLink.addClassName("ar-nav-link");
        var simLink = new RouterLink("Simulator", dev.arcmem.simulator.ui.views.SimulationView.class);
        simLink.addClassName("ar-nav-link");

        var headerRow = new HorizontalLayout(header, simLink, benchmarkLink);
        headerRow.setWidthFull();
        headerRow.setAlignItems(HorizontalLayout.Alignment.CENTER);
        headerRow.addClassName("ar-header-row");

        var conversationBar = buildConversationBar();

        messagesLayout = new VerticalLayout();
        messagesLayout.setWidthFull();
        messagesLayout.setPadding(true);
        messagesLayout.setSpacing(true);

        messagesScroller = new Scroller(messagesLayout);
        messagesScroller.setSizeFull();
        messagesScroller.setScrollDirection(Scroller.ScrollDirection.VERTICAL);

        var inputSection = buildInputSection();

        var chatColumn = new VerticalLayout(messagesScroller, inputSection);
        chatColumn.setSizeFull();
        chatColumn.setPadding(false);
        chatColumn.setSpacing(false);
        chatColumn.setFlexGrow(1, messagesScroller);

        // Tabbed sidebar
        var sidebar = buildTabbedSidebar();

        var splitLayout = new SplitLayout(chatColumn, sidebar);
        splitLayout.setSizeFull();
        splitLayout.setSplitterPosition(70);

        add(headerRow, conversationBar, splitLayout);
        setFlexGrow(1, splitLayout);
        setPadding(false);
    }

    private HorizontalLayout buildConversationBar() {
        conversationIdField = new TextField();
        conversationIdField.setReadOnly(true);
        conversationIdField.setWidth("320px");
        conversationIdField.setPlaceholder("No conversation");
        conversationIdField.setClearButtonVisible(false);
        conversationIdField.addClassName("ar-conversation-id");

        var copyButton = new Button("Copy ID", e -> {
            if (activeConversationId != null) {
                getUI().ifPresent(ui -> ui.getPage().executeJs(
                        "navigator.clipboard.writeText($0)", activeConversationId));
            }
        });
        copyButton.addThemeVariants(ButtonVariant.LUMO_SMALL, ButtonVariant.LUMO_TERTIARY);

        var newButton = new Button("New", e -> startNewConversation());
        newButton.addThemeVariants(ButtonVariant.LUMO_SMALL, ButtonVariant.LUMO_PRIMARY);

        var cloneButton = new Button("Clone", e -> cloneCurrentConversation());
        cloneButton.addThemeVariants(ButtonVariant.LUMO_SMALL, ButtonVariant.LUMO_CONTRAST);

        resumeIdField = new TextField();
        resumeIdField.setPlaceholder("Paste conversation ID...");
        resumeIdField.setWidth("280px");
        resumeIdField.setClearButtonVisible(true);

        var resumeButton = new Button("Resume", e -> resumeConversation());
        resumeButton.addThemeVariants(ButtonVariant.LUMO_SMALL, ButtonVariant.LUMO_SUCCESS);

        var bar = new HorizontalLayout(
                conversationIdField, copyButton, newButton, cloneButton, resumeIdField, resumeButton);
        bar.setWidthFull();
        bar.setAlignItems(Alignment.CENTER);
        bar.setPadding(true);
        bar.setSpacing(true);
        bar.addClassName("ar-conversation-bar");

        return bar;
    }

    private HorizontalLayout buildInputSection() {
        var section = new HorizontalLayout();
        section.setWidthFull();
        section.setPadding(true);
        section.setAlignItems(Alignment.CENTER);

        inputField = new TextField();
        inputField.setPlaceholder("Tell Bigby what you do...");
        inputField.setWidthFull();
        inputField.setClearButtonVisible(true);
        inputField.getElement().setAttribute("autocomplete", "off");
        inputField.addKeyPressListener(Key.ENTER, e -> sendMessage());

        sendButton = new Button("Send");
        sendButton.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        sendButton.addClickListener(e -> sendMessage());

        section.add(inputField, sendButton);
        section.setFlexGrow(1, inputField);

        return section;
    }

    private VerticalLayout buildTabbedSidebar() {
        var sidebar = new VerticalLayout();
        sidebar.setSizeFull();
        sidebar.setPadding(false);
        sidebar.setSpacing(false);
        sidebar.addClassName("ar-chat-sidebar");

        unitsTabContent = new VerticalLayout();
        unitsTabContent.setPadding(true);
        unitsTabContent.setSpacing(true);

        propositionsTabContent = new VerticalLayout();
        propositionsTabContent.setPadding(true);
        propositionsTabContent.setSpacing(true);

        knowledgeTabContent = new VerticalLayout();
        knowledgeTabContent.setPadding(true);
        knowledgeTabContent.setSpacing(true);

        sessionInfoTabContent = new VerticalLayout();
        sessionInfoTabContent.setPadding(true);
        sessionInfoTabContent.setSpacing(true);

        contextTabContent = new VerticalLayout();
        contextTabContent.setPadding(true);
        contextTabContent.setSpacing(true);

        var unitsScroller = new Scroller(unitsTabContent);
        unitsScroller.setSizeFull();
        unitsScroller.setScrollDirection(Scroller.ScrollDirection.VERTICAL);

        var propositionsScroller = new Scroller(propositionsTabContent);
        propositionsScroller.setSizeFull();
        propositionsScroller.setScrollDirection(Scroller.ScrollDirection.VERTICAL);

        var knowledgeScroller = new Scroller(knowledgeTabContent);
        knowledgeScroller.setSizeFull();
        knowledgeScroller.setScrollDirection(Scroller.ScrollDirection.VERTICAL);

        var sessionInfoScroller = new Scroller(sessionInfoTabContent);
        sessionInfoScroller.setSizeFull();
        sessionInfoScroller.setScrollDirection(Scroller.ScrollDirection.VERTICAL);

        var contextScroller = new Scroller(contextTabContent);
        contextScroller.setSizeFull();
        contextScroller.setScrollDirection(Scroller.ScrollDirection.VERTICAL);

        var tabSheet = new TabSheet();
        tabSheet.setSizeFull();
        tabSheet.add("Memory Units", unitsScroller);
        tabSheet.add("Propositions", propositionsScroller);
        tabSheet.add("Knowledge", knowledgeScroller);
        tabSheet.add("Session Info", sessionInfoScroller);
        tabSheet.add("Context", contextScroller);

        sidebar.add(tabSheet);
        sidebar.setFlexGrow(1, tabSheet);

        refreshSidebar();

        return sidebar;
    }


    /**
     * Refresh all three sidebar tabs with current data from the repository.
     */
    private void refreshSidebar() {
        refreshUnitsTab();
        refreshPropositionsTab();
        refreshKnowledgeTab();
        refreshSessionInfoTab();
        refreshContextTab();
    }

    private void refreshUnitsTab() {
        try {
            var units = arcMemEngine.inject(getContextId());
            var unitsById = units.stream().collect(java.util.stream.Collectors.toMap(
                    MemoryUnit::id, unit -> unit, (left, right) -> right, LinkedHashMap::new));

            unitCards.entrySet().removeIf(entry -> {
                var stale = !unitsById.containsKey(entry.getKey());
                if (stale) {
                    unitsTabContent.remove(entry.getValue());
                    renderedUnits.remove(entry.getKey());
                }
                return stale;
            });

            unitsById.forEach((id, unit) -> {
                var currentCard = unitCards.get(id);
                if (currentCard == null) {
                    var newCard = unitCard(unit);
                    unitCards.put(id, newCard);
                    if (createUnitForm != null) {
                        unitsTabContent.addComponentAtIndex(unitsTabContent.indexOf(createUnitForm), newCard);
                    } else {
                        unitsTabContent.add(newCard);
                    }
                } else if (!Objects.equals(renderedUnits.get(id), unit)) {
                    updateUnitCard(currentCard, unit);
                }
                renderedUnits.put(id, unit);
            });

            if (unitsHeader == null) {
                unitsHeader = new Span();
                unitsHeader.addClassName("ar-chat-section-header");
                unitsHeader.addClassName("ar-chat-section-header--cyan");
                unitsTabContent.addComponentAtIndex(0, unitsHeader);
            }
            unitsHeader.setText("Active Units (%d)".formatted(units.size()));
            unitsHeader.setVisible(!units.isEmpty());

            if (createUnitForm == null) {
                createUnitForm = buildCreateUnitForm();
                unitsTabContent.add(createUnitForm);
            }
        } catch (Exception e) {
            logger.warn("Failed to load units for sidebar: {}", e.getMessage());
        }
    }

    private void updateUnitCard(Div card, MemoryUnit unit) {
        card.getChildren()
            .filter(component -> component instanceof Span
                    && component.getClassNames().contains("ar-chat-authority-badge"))
            .findFirst()
            .ifPresent(component -> {
                var badge = (Span) component;
                badge.setText(unit.authority().name());
                badge.getElement().setAttribute("data-authority", unit.authority().name().toLowerCase());
            });

        card.getChildren()
            .filter(component -> component instanceof Span
                    && component.getClassNames().contains("ar-chat-text"))
            .findFirst()
            .ifPresent(component -> ((Span) component).setText(truncateText(unit.text(), 80)));

        card.getChildren()
            .filter(component -> component instanceof ProgressBar)
            .findFirst()
            .ifPresent(component -> ((ProgressBar) component).setValue(unit.rank()));

        card.getChildren()
            .filter(component -> component instanceof Span
                    && component.getClassNames().contains("ar-chat-meta"))
            .findFirst()
            .ifPresent(component -> ((Span) component).setText(
                    "rank: %d | x%d".formatted(unit.rank(), unit.reinforcementCount())));

        var pinnedBadge = card.getChildren()
                              .filter(component -> component instanceof Span
                                      && component.getClassNames().contains("ar-pinned-icon"))
                              .findFirst()
                              .orElse(null);
        if (unit.pinned() && pinnedBadge == null) {
            var badge = new Span("pinned");
            badge.addClassName("ar-pinned-icon");
            card.add(badge);
        }
        if (!unit.pinned() && pinnedBadge != null) {
            card.remove(pinnedBadge);
        }
    }

    private void refreshPropositionsTab() {
        propositionsTabContent.removeAll();

        try {
            var propositions = contextUnitRepository.findByContextIdValue(getContextId());
            var unitIds = contextUnitRepository.findActiveUnits(getContextId()).stream()
                                            .map(n -> n.getId())
                                            .collect(java.util.stream.Collectors.toSet());
            var nonUnits = propositions.stream()
                                         .filter(p -> !unitIds.contains(p.getId()))
                                         .toList();

            if (!nonUnits.isEmpty()) {
                var propsHeader = new Span("Propositions (%d)".formatted(nonUnits.size()));
                propsHeader.addClassName("ar-chat-section-header");
                propsHeader.addClassName("ar-chat-section-header--amber");
                propositionsTabContent.add(propsHeader);

                for (var prop : nonUnits) {
                    var card = new Div();
                    card.addClassName("ar-chat-card");

                    var text = new Span(truncateText(prop.getText(), 100));
                    text.addClassName("ar-chat-text");

                    var statusName = prop.getStatus() != null ? prop.getStatus().name() : "UNKNOWN";
                    var meta = new Span("conf: %.0f%% | %s".formatted(
                            prop.getConfidence() * 100, statusName));
                    meta.addClassName("ar-chat-meta");

                    var promoteButton = new Button("Promote", e -> {
                        arcMemEngine.promote(prop.getId(), 500);
                        refreshSidebar();
                    });
                    promoteButton.addThemeVariants(ButtonVariant.LUMO_SMALL, ButtonVariant.LUMO_SUCCESS, ButtonVariant.LUMO_TERTIARY);

                    card.add(text, meta, promoteButton);
                    propositionsTabContent.add(card);
                }
            } else {
                var placeholder = new Paragraph("Extracted propositions will appear here after each conversation turn.");
                placeholder.addClassName("ar-empty-message");
                propositionsTabContent.add(placeholder);
            }
        } catch (Exception e) {
            logger.warn("Failed to load propositions for sidebar: {}", e.getMessage());
            var placeholder = new Paragraph("Failed to load propositions.");
            placeholder.addClassName("ar-empty-message");
            propositionsTabContent.add(placeholder);
        }
    }

    private void refreshKnowledgeTab() {
        knowledgeTabContent.removeAll();

        try {
            var propositions = contextUnitRepository.findByContextIdValue(getContextId());
            var unitIds = contextUnitRepository.findActiveUnits(getContextId()).stream()
                                            .map(n -> n.getId())
                                            .collect(java.util.stream.Collectors.toSet());
            var knowledge = propositions.stream()
                                        .filter(p -> !unitIds.contains(p.getId()))
                                        .toList();

            if (!knowledge.isEmpty()) {
                var header = new Span("Knowledge Entries (%d)".formatted(knowledge.size()));
                header.addClassName("ar-chat-section-header");
                header.addClassName("ar-chat-section-header--primary");
                knowledgeTabContent.add(header);

                for (var prop : knowledge) {
                    var card = new Div();
                    card.addClassName("ar-chat-card");

                    var text = new Span(truncateText(prop.getText(), 100));
                    text.addClassName("ar-chat-text");

                    var meta = new Span("conf: %.0f%%".formatted(prop.getConfidence() * 100));
                    meta.addClassName("ar-chat-meta");

                    var promoteButton = new Button("Promote to Memory Unit", e -> {
                        arcMemEngine.promote(prop.getId(), 500);
                        refreshSidebar();
                    });
                    promoteButton.addThemeVariants(ButtonVariant.LUMO_SMALL, ButtonVariant.LUMO_SUCCESS, ButtonVariant.LUMO_TERTIARY);

                    card.add(text, meta, promoteButton);
                    knowledgeTabContent.add(card);
                }
            } else {
                var placeholder = new Paragraph("No knowledge entries yet. Add one below or chat to generate propositions.");
                placeholder.addClassName("ar-empty-message");
                knowledgeTabContent.add(placeholder);
            }
        } catch (Exception e) {
            logger.warn("Failed to load knowledge for sidebar: {}", e.getMessage());
        }

        knowledgeTabContent.add(buildAddKnowledgeForm());
    }

    private Div buildAddKnowledgeForm() {
        var form = new Div();
        form.addClassName("ar-chat-form");

        var formTitle = new Span("Add Knowledge");
        formTitle.addClassName("ar-chat-form-title");

        var textField = new TextField("Knowledge Text");
        textField.setWidthFull();
        textField.setPlaceholder("Enter a fact or piece of knowledge...");

        var confidenceField = new com.vaadin.flow.component.textfield.NumberField("Confidence");
        confidenceField.setMin(0.0);
        confidenceField.setMax(1.0);
        confidenceField.setStep(0.1);
        confidenceField.setValue(0.8);
        confidenceField.setWidthFull();
        confidenceField.setStepButtonsVisible(true);

        var createButton = new Button("Create");
        createButton.addThemeVariants(ButtonVariant.LUMO_PRIMARY, ButtonVariant.LUMO_SMALL);
        createButton.setWidthFull();
        createButton.addClickListener(e -> {
            var knowledgeText = textField.getValue();
            if (knowledgeText == null || knowledgeText.trim().isEmpty()) {
                return;
            }

            var confidence = confidenceField.getValue() != null ? confidenceField.getValue() : 0.8;

            var node = new PropositionNode(UUID.randomUUID().toString(), "default", knowledgeText.trim(), confidence, 0.0, null, List.of(),
                    Instant.now(), Instant.now(), PropositionStatus.ACTIVE, null, List.of());
            node.setContextId(getContextId());
            graphObjectManager.save(new PropositionView(node, List.of()), CascadeType.NONE);

            textField.clear();
            confidenceField.setValue(0.8);

            refreshSidebar();
        });

        var layout = new VerticalLayout(formTitle, textField, confidenceField, createButton);
        layout.setPadding(false);
        layout.setSpacing(true);
        form.add(layout);

        return form;
    }

    private void refreshSessionInfoTab() {
        sessionInfoTabContent.removeAll();

        var title = new H4("Session Info");
        title.addClassName("ar-section-title");
        sessionInfoTabContent.add(title);

        sessionInfoTabContent.add(infoRow("Context ID", getContextId()));

        try {
            int unitCount = contextUnitRepository.countActiveUnits(getContextId());
            sessionInfoTabContent.add(infoRow("Active Units", String.valueOf(unitCount)));
        } catch (Exception e) {
            sessionInfoTabContent.add(infoRow("Active Units", "N/A"));
        }

        try {
            var propositions = contextUnitRepository.findByContextIdValue(getContextId());
            sessionInfoTabContent.add(infoRow("Propositions", String.valueOf(propositions.size())));
        } catch (Exception e) {
            sessionInfoTabContent.add(infoRow("Propositions", "N/A"));
        }

        sessionInfoTabContent.add(infoRow("Turn Count", String.valueOf(turnCount)));
    }

    private Div infoRow(String label, String value) {
        var row = new Div();
        row.addClassName("ar-chat-session-row");

        var labelSpan = new Span(label);
        labelSpan.addClassName("ar-chat-session-label");

        var valueSpan = new Span(value);

        row.add(labelSpan, valueSpan);
        return row;
    }


    private Div unitCard(MemoryUnit unit) {
        var card = new Div();
        card.addClassName("ar-chat-unit-card");

        var authorityBadge = new Span(unit.authority().name());
        authorityBadge.addClassName("ar-chat-authority-badge");
        authorityBadge.getElement().setAttribute("data-authority", unit.authority().name().toLowerCase());

        var text = new Span(truncateText(unit.text(), 80));
        text.addClassName("ar-chat-text");

        var rankBar = new ProgressBar(0, 900, unit.rank());
        rankBar.setWidthFull();
        rankBar.addClassName("ar-chat-rank-bar");

        var meta = new Span("rank: %d | x%d".formatted(unit.rank(), unit.reinforcementCount()));
        meta.addClassName("ar-chat-meta");

        card.add(authorityBadge, text, rankBar, meta);

        if (unit.pinned()) {
            var pinnedBadge = new Span("pinned");
            pinnedBadge.addClassName("ar-pinned-icon");
            card.add(pinnedBadge);
        }

        // Inline rank slider
        var rankField = new IntegerField("Rank");
        rankField.setMin(100);
        rankField.setMax(900);
        rankField.setStep(50);
        rankField.setValue(unit.rank());
        rankField.setWidthFull();
        rankField.setStepButtonsVisible(true);
        rankField.addClassName("ar-chat-form-field");
        rankField.addValueChangeListener(e -> {
            if (e.getValue() != null) {
                contextUnitRepository.updateRank(unit.id(), e.getValue());
                refreshSidebar();
            }
        });

        // Authority dropdown (current level and higher, no CANON)
        var authorityCombo = new ComboBox<String>("Authority");
        authorityCombo.setWidthFull();
        authorityCombo.addClassName("ar-chat-form-field");
        var authorityOptions = new ArrayList<String>();
        int currentLevel = unit.authority().level();
        for (var auth : Authority.values()) {
            if (auth != Authority.CANON && auth.level() >= currentLevel) {
                authorityOptions.add(auth.name());
            }
        }
        authorityCombo.setItems(authorityOptions);
        authorityCombo.setValue(unit.authority().name());
        authorityCombo.addValueChangeListener(e -> {
            var latest = renderedUnits.get(unit.id());
            var currentAuthority = latest != null ? latest.authority().name() : unit.authority().name();
            if (e.getValue() != null && !e.getValue().equals(currentAuthority)) {
                contextUnitRepository.setAuthority(unit.id(), e.getValue());
                refreshSidebar();
            }
        });

        // Evict button
        var evictButton = new Button("Evict");
        evictButton.addThemeVariants(ButtonVariant.LUMO_SMALL, ButtonVariant.LUMO_ERROR, ButtonVariant.LUMO_TERTIARY);
        evictButton.addClickListener(e -> {
            contextUnitRepository.archiveUnit(unit.id());
            refreshSidebar();
        });

        var reviseField = new TextField("Revision");
        reviseField.setWidthFull();
        reviseField.setValue(unit.text());
        reviseField.addClassName("ar-chat-form-field");

        var reviseButton = new Button("Revise");
        reviseButton.addThemeVariants(ButtonVariant.LUMO_SMALL, ButtonVariant.LUMO_PRIMARY, ButtonVariant.LUMO_TERTIARY);
        reviseButton.addClickListener(e -> {
            var revisedText = reviseField.getValue();
            if (revisedText == null) {
                return;
            }
            var latest = renderedUnits.get(unit.id());
            var currentText = latest != null ? latest.text() : unit.text();
            var normalized = revisedText.trim();
            if (normalized.isEmpty() || normalized.equals(currentText)) {
                return;
            }
            var target = latest != null ? latest : unit;
            var revised = reviseUnit(target, normalized);
            if (!revised) {
                logger.warn("MemoryUnit revision did not archive predecessor {}", target.id());
            }
            refreshSidebar();
        });

        var controlsRow = new HorizontalLayout(authorityCombo, evictButton);
        controlsRow.setWidthFull();
        controlsRow.setAlignItems(Alignment.END);
        controlsRow.setFlexGrow(1, authorityCombo);

        var reviseRow = new HorizontalLayout(reviseField, reviseButton);
        reviseRow.setWidthFull();
        reviseRow.setAlignItems(Alignment.END);
        reviseRow.setFlexGrow(1, reviseField);

        card.add(rankField, controlsRow, reviseRow);

        return card;
    }

    private boolean reviseUnit(MemoryUnit unit, String revisedText) {
        var request = new MutationRequest(unit.id(), revisedText, MutationSource.UI, "chat-operator");
        var decision = mutationStrategy.evaluate(request);
        return switch (decision) {
            case MutationDecision.Allow _ -> executeRevision(unit, revisedText);
            case MutationDecision.Deny deny -> {
                com.vaadin.flow.component.notification.Notification.show(
                        deny.reason(), 3000,
                        com.vaadin.flow.component.notification.Notification.Position.MIDDLE);
                yield false;
            }
            case MutationDecision.PendingApproval _ -> {
                com.vaadin.flow.component.notification.Notification.show(
                        "Revision queued for approval", 3000,
                        com.vaadin.flow.component.notification.Notification.Position.MIDDLE);
                yield false;
            }
        };
    }

    private boolean executeRevision(MemoryUnit unit, String revisedText) {
        var node = new PropositionNode(UUID.randomUUID().toString(), "default", revisedText, 0.95, 0.0, null, List.of(),
                Instant.now(), Instant.now(), PropositionStatus.ACTIVE, null, List.of());
        node.setContextId(getContextId());
        graphObjectManager.save(new PropositionView(node, List.of()), CascadeType.NONE);
        contextUnitRepository.promoteToUnit(node.getId(), unit.rank(), unit.authority().name());
        if (unit.pinned()) {
            contextUnitRepository.updatePinned(node.getId(), true);
        }
        arcMemEngine.supersede(unit.id(), node.getId(), ArchiveReason.REVISION);
        return contextUnitRepository.findPropositionNodeById(unit.id())
                .map(predecessor -> predecessor.getRank() <= 0
                        || predecessor.getStatus() == PropositionStatus.SUPERSEDED)
                .orElse(true);
    }

    private void refreshContextTab() {
        contextTabContent.removeAll();
        try {
            var unitRef = new ArcMemLlmReference(
                    arcMemEngine,
                    getContextId(),
                    properties.unit().budget(),
                    compliancePolicy,
                    properties.assembly().promptTokenBudget(),
                    tokenCounter,
                    null,
                    properties.retrieval(),
                    relevanceScorer);
            var propositionRef = new PropositionsLlmReference(
                    contextUnitRepository,
                    getContextId(),
                    properties.unit().budget());

            var units = unitRef.getUnits();
            var unitsBlock = unitRef.getContent();
            var propositionBlock = propositionRef.getContent();
            var prompt = renderChatPrompt(units, propositionBlock);

            contextTabContent.add(infoRow("Units in Prompt", String.valueOf(units.size())));
            contextTabContent.add(readOnlyBlock("ARC-Mem Context", unitsBlock));
            contextTabContent.add(readOnlyBlock("Propositions Block", propositionBlock));
            contextTabContent.add(readOnlyBlock("Rendered System Prompt", prompt));
        } catch (Exception e) {
            logger.warn("Failed to build context preview: {}", e.getMessage());
            var placeholder = new Paragraph("Failed to render context preview.");
            placeholder.addClassName("ar-empty-message");
            contextTabContent.add(placeholder);
        }
    }

    private String renderChatPrompt(List<MemoryUnit> units, String propositionBlock) {
        var unitMaps = units.stream()
                .map(a -> java.util.Map.<String, Object>of(
                        "text", a.text(),
                        "rank", a.rank(),
                        "authority", a.authority().name()))
                .toList();

        var tiered = properties.unit().compliancePolicy() == CompliancePolicyMode.TIERED;
        var templateVars = new java.util.HashMap<String, Object>();
        templateVars.put("properties", properties);
        templateVars.put("units", unitMaps);
        templateVars.put("proposition_block", propositionBlock);
        templateVars.put("persona", properties.chat().persona());
        templateVars.put("tiered", tiered);

        if (tiered) {
            var grouped = units.stream()
                    .collect(java.util.stream.Collectors.groupingBy(MemoryUnit::authority));
            for (var authority : Authority.values()) {
                var key = authority.name().toLowerCase() + "_units";
                var group = grouped.getOrDefault(authority, List.of()).stream()
                        .map(a -> java.util.Map.<String, Object>of("text", a.text(), "rank", a.rank()))
                        .toList();
                templateVars.put(key, group);
            }
        }

        return PromptTemplates.render("prompts/arc-mem.jinja", templateVars);
    }

    private VerticalLayout readOnlyBlock(String titleText, String content) {
        var title = new Span(titleText);
        title.addClassName("ar-chat-section-header");
        title.addClassName("ar-chat-section-header--primary");

        var textArea = new TextArea();
        textArea.setWidthFull();
        textArea.setReadOnly(true);
        textArea.setValue(content != null ? content : "");
        textArea.setMinHeight("180px");

        var block = new VerticalLayout(title, textArea);
        block.setPadding(false);
        block.setSpacing(true);
        return block;
    }


    private Div buildCreateUnitForm() {
        var form = new Div();
        form.addClassName("ar-chat-form");

        var formTitle = new Span("Create Memory Unit");
        formTitle.addClassName("ar-chat-form-title");

        var unitTextField = new TextField("Unit Text");
        unitTextField.setWidthFull();
        unitTextField.setPlaceholder("Enter unit text...");

        var rankField = new IntegerField("Rank");
        rankField.setMin(100);
        rankField.setMax(900);
        rankField.setValue(500);
        rankField.setStep(50);
        rankField.setWidthFull();
        rankField.setStepButtonsVisible(true);

        var authorityCombo = new ComboBox<String>("Authority");
        authorityCombo.setItems(List.of("PROVISIONAL", "UNRELIABLE", "RELIABLE"));
        authorityCombo.setValue("PROVISIONAL");
        authorityCombo.setWidthFull();

        var createButton = new Button("Create");
        createButton.addThemeVariants(ButtonVariant.LUMO_PRIMARY, ButtonVariant.LUMO_SMALL);
        createButton.setWidthFull();
        createButton.addClickListener(e -> {
            var unitText = unitTextField.getValue();
            if (unitText == null || unitText.trim().isEmpty()) {
                return;
            }

            var rank = rankField.getValue() != null ? rankField.getValue() : 500;
            var authority = authorityCombo.getValue() != null ? authorityCombo.getValue() : "PROVISIONAL";

            var node = new PropositionNode(UUID.randomUUID().toString(), "default", unitText.trim(), 0.95, 0.0, null, List.of(),
                    Instant.now(), Instant.now(), PropositionStatus.ACTIVE, null, List.of());
            node.setContextId(getContextId());
            graphObjectManager.save(new PropositionView(node, List.of()), CascadeType.NONE);
            contextUnitRepository.promoteToUnit(node.getId(), rank, authority);

            unitTextField.clear();
            rankField.setValue(500);
            authorityCombo.setValue("PROVISIONAL");

            refreshSidebar();
        });

        var layout = new VerticalLayout(formTitle, unitTextField, rankField, authorityCombo, createButton);
        layout.setPadding(false);
        layout.setSpacing(true);
        form.add(layout);

        return form;
    }

    private String truncateText(String text, int maxLength) {
        if (text == null) {
            return "";
        }
        if (text.length() <= maxLength) {
            return text;
        }
        return text.substring(0, maxLength) + "...";
    }


    private void sendMessage() {
        var text = inputField.getValue();
        if (text == null || text.trim().isEmpty()) {
            return;
        }

        var ui = getUI().orElse(null);
        if (ui == null) {
            return;
        }

        inputField.clear();
        setInputEnabled(false);

        addUserBubble(text);

        thinkingIndicator = new Div();
        thinkingIndicator.addClassName("ar-chat-thinking");
        thinkingIndicator.setText("Bigby is thinking...");
        messagesLayout.add(thinkingIndicator);
        scrollToBottom();

        var sessionData = getOrCreateSession(ui);

        if (activeConversationId != null) {
            conversationService.appendMessage(activeConversationId, "PLAYER", text);
        }

        new Thread(() -> {
            try {
                sessionData.chatSession().onUserMessage(new UserMessage(text));

                var response = sessionData.responseQueue()
                                          .poll(RESPONSE_TIMEOUT_SECONDS, TimeUnit.SECONDS);

                ui.access(() -> {
                    removeThinkingIndicator();
                    if (response != null) {
                        addBotBubble(response.getContent());
                        if (activeConversationId != null) {
                            conversationService.appendMessage(activeConversationId, "DM", response.getContent());
                        }
                        turnCount++;
                        refreshSidebar();
                        scheduleAsyncSidebarRefresh(ui);
                    } else {
                        addErrorBubble("Response timed out. Please try again.");
                    }
                    scrollToBottom();
                    setInputEnabled(true);
                    inputField.focus();
                });
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                logger.warn("Response wait interrupted for chat session");
                ui.access(() -> {
                    removeThinkingIndicator();
                    addErrorBubble("Request interrupted. Please try again.");
                    setInputEnabled(true);
                });
            } catch (Exception e) {
                logger.error("Error processing chat message", e);
                ui.access(() -> {
                    removeThinkingIndicator();
                    addErrorBubble("Error: " + e.getMessage());
                    scrollToBottom();
                    setInputEnabled(true);
                });
            }
        }, "arc-mem-chat-" + UUID.randomUUID().toString().substring(0, 8)).start();
    }


    private record SessionData(ChatSession chatSession, BlockingQueue<Message> responseQueue) {}

    private SessionData getOrCreateSession(UI ui) {
        var vaadinSession = VaadinSession.getCurrent();
        var existing = (SessionData) vaadinSession.getAttribute(SESSION_ATTR_CHAT);
        if (existing != null) {
            return existing;
        }

        if (activeConversationId == null) {
            activeConversationId = conversationService.createConversation();
            updateConversationIdDisplay();
            storeConversationIdInSession();
        }

        var responseQueue = new ArrayBlockingQueue<Message>(10);
        var outputChannel = new VaadinOutputChannel(responseQueue, ui, messagesLayout);

        var sessionId = UUID.randomUUID().toString();
        var user = new AnonymousDmUser(sessionId);

        var chatSession = chatbot.createSession(user, outputChannel, activeConversationId, activeConversationId);
        var sessionData = new SessionData(chatSession, responseQueue);
        vaadinSession.setAttribute(SESSION_ATTR_CHAT, sessionData);

        logger.info("Created chat session {} for conversation {}", sessionId, activeConversationId);
        return sessionData;
    }

    private void restoreConversation() {
        var vaadinSession = VaadinSession.getCurrent();
        if (vaadinSession == null) {
            return;
        }

        var storedConversationId = (String) vaadinSession.getAttribute(SESSION_ATTR_CONVERSATION_ID);
        if (storedConversationId != null && activeConversationId == null) {
            activeConversationId = storedConversationId;
            updateConversationIdDisplay();
        }

        var sessionData = (SessionData) vaadinSession.getAttribute(SESSION_ATTR_CHAT);
        if (sessionData != null) {
            var conversation = sessionData.chatSession().getConversation();
            for (var msg : conversation.getMessages()) {
                if (msg instanceof UserMessage) {
                    addUserBubble(msg.getContent());
                } else if (msg instanceof AssistantMessage) {
                    addBotBubble(msg.getContent());
                }
            }
        } else if (activeConversationId != null) {
            var messages = conversationService.loadConversation(activeConversationId);
            for (var msg : messages) {
                if ("PLAYER".equals(msg.role())) {
                    addUserBubble(msg.text());
                } else if ("DM".equals(msg.role())) {
                    addBotBubble(msg.text());
                }
            }
            turnCount = (int) messages.stream().filter(m -> "DM".equals(m.role())).count();
        }

        if (messagesLayout.getComponentCount() > 0) {
            scrollToBottom();
        }
    }


    private void hydrateEmbabelSession(UI ui, List<ChatMessageRecord> messages) {
        var sessionData = getOrCreateSession(ui);
        var conversation = sessionData.chatSession().getConversation();
        for (var msg : messages) {
            if ("PLAYER".equals(msg.role())) {
                conversation.addMessage(new UserMessage(msg.text()));
            } else if ("DM".equals(msg.role())) {
                conversation.addMessage(new AssistantMessage(msg.text()));
            }
        }
        logger.debug("Hydrated Embabel conversation with {} messages", messages.size());
    }

    private String getContextId() {
        return activeConversationId != null ? activeConversationId : "chat";
    }

    private void updateConversationIdDisplay() {
        if (conversationIdField != null && activeConversationId != null) {
            conversationIdField.setValue(activeConversationId);
        }
    }

    private void storeConversationIdInSession() {
        var vaadinSession = VaadinSession.getCurrent();
        if (vaadinSession != null && activeConversationId != null) {
            vaadinSession.setAttribute(SESSION_ATTR_CONVERSATION_ID, activeConversationId);
        }
    }

    private void clearSession() {
        var vaadinSession = VaadinSession.getCurrent();
        if (vaadinSession != null) {
            vaadinSession.setAttribute(SESSION_ATTR_CHAT, null);
            vaadinSession.setAttribute(SESSION_ATTR_CONVERSATION_ID, null);
        }
    }

    private void startNewConversation() {
        cancelAsyncSidebarRefresh();
        clearSession();

        activeConversationId = conversationService.createConversation();
        updateConversationIdDisplay();
        storeConversationIdInSession();

        messagesLayout.removeAll();
        unitCards.clear();
        renderedUnits.clear();
        unitsHeader = null;
        createUnitForm = null;
        turnCount = 0;

        refreshSidebar();
        inputField.focus();
        logger.info("Started new conversation {}", activeConversationId);
    }

    private void resumeConversation() {
        var resumeId = resumeIdField.getValue();
        if (resumeId == null || resumeId.trim().isEmpty()) {
            return;
        }
        resumeId = resumeId.trim();

        var existing = conversationService.findConversation(resumeId);
        if (existing.isEmpty()) {
            Notification.show("No conversation found with that ID", 3000,
                    Notification.Position.MIDDLE);
            return;
        }

        switchToConversation(resumeId);
        resumeIdField.clear();
        logger.info("Resumed conversation {} ", activeConversationId);
    }

    private void cloneCurrentConversation() {
        if (activeConversationId == null) {
            Notification.show("No active conversation to clone", 3000,
                    Notification.Position.MIDDLE);
            return;
        }

        var clonedId = conversationService.cloneConversation(activeConversationId);
        switchToConversation(clonedId);
        logger.info("Cloned to conversation {}", activeConversationId);
    }

    private void switchToConversation(String conversationId) {
        cancelAsyncSidebarRefresh();
        clearSession();

        activeConversationId = conversationId;
        updateConversationIdDisplay();
        storeConversationIdInSession();

        messagesLayout.removeAll();
        unitCards.clear();
        renderedUnits.clear();
        unitsHeader = null;
        createUnitForm = null;
        turnCount = 0;

        var messages = conversationService.loadConversation(activeConversationId);
        for (var msg : messages) {
            if ("PLAYER".equals(msg.role())) {
                addUserBubble(msg.text());
            } else if ("DM".equals(msg.role())) {
                addBotBubble(msg.text());
                turnCount++;
            }
        }

        getUI().ifPresent(ui -> hydrateEmbabelSession(ui, messages));
        refreshSidebar();
        scrollToBottom();
    }

    private void addUserBubble(String text) {
        var bubble = new Div(new Paragraph(text));
        bubble.addClassName("ar-chat-bubble");
        bubble.addClassName("ar-chat-bubble--user");
        messagesLayout.add(bubble);
        scrollToBottom();
    }

    private void addBotBubble(String markdown) {
        var html = htmlRenderer.render(markdownParser.parse(markdown));
        var bubble = new Div();
        bubble.addClassName("ar-chat-bubble");
        bubble.addClassName("ar-chat-bubble--bot");
        bubble.getElement().setProperty("innerHTML", html);
        messagesLayout.add(bubble);
        scrollToBottom();
    }

    private void addErrorBubble(String message) {
        var bubble = new Div(new Paragraph(message));
        bubble.addClassName("ar-chat-bubble");
        bubble.addClassName("ar-chat-bubble--error");
        messagesLayout.add(bubble);
        scrollToBottom();
    }

    private void removeThinkingIndicator() {
        if (thinkingIndicator != null) {
            messagesLayout.remove(thinkingIndicator);
            thinkingIndicator = null;
        }
    }

    private void setInputEnabled(boolean enabled) {
        inputField.setEnabled(enabled);
        sendButton.setEnabled(enabled);
    }

    private synchronized void cancelAsyncSidebarRefresh() {
        if (sidebarRefreshThread != null && sidebarRefreshThread.isAlive()) {
            sidebarRefreshThread.interrupt();
        }
        sidebarRefreshThread = null;
    }

    private synchronized void scheduleAsyncSidebarRefresh(UI ui) {
        cancelAsyncSidebarRefresh();
        var refresher = new Thread(() -> {
            for (int i = 0; i < ASYNC_SIDEBAR_REFRESH_ATTEMPTS; i++) {
                try {
                    Thread.sleep(ASYNC_SIDEBAR_REFRESH_INTERVAL_MILLIS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                }
                if (!ui.isAttached()) {
                    return;
                }
                ui.access(() -> {
                    if (ui.isAttached()) {
                        refreshSidebar();
                    }
                });
            }
        }, "arc-mem-sidebar-refresh-" + UUID.randomUUID().toString().substring(0, 8));
        refresher.setDaemon(true);
        sidebarRefreshThread = refresher;
        refresher.start();
    }

    private void scrollToBottom() {
        messagesScroller.getElement().executeJs("this.scrollTop = this.scrollHeight");
    }


    /**
     * Vaadin-specific OutputChannel that routes assistant messages to the UI queue
     * and shows tool-call progress indicators in real time.
     * <p>
     * Follows the Impromptu VaadinChatView.VaadinOutputChannel pattern.
     */
    private static class VaadinOutputChannel implements OutputChannel {

        private static final Logger log = LoggerFactory.getLogger(VaadinOutputChannel.class);

        private final BlockingQueue<Message> responseQueue;
        private final UI ui;
        private final VerticalLayout messagesLayout;
        private Div currentProgressIndicator;

        VaadinOutputChannel(BlockingQueue<Message> responseQueue, UI ui, VerticalLayout messagesLayout) {
            this.responseQueue = responseQueue;
            this.ui = ui;
            this.messagesLayout = messagesLayout;
        }

        @Override
        public void send(OutputChannelEvent event) {
            if (event instanceof MessageOutputChannelEvent msgEvent) {
                var msg = msgEvent.getMessage();
                if (msg instanceof AssistantMessage) {
                    ui.access(this::removeProgressIndicator);
                    responseQueue.offer(msg);
                }
            } else if (event instanceof ProgressOutputChannelEvent progressEvent) {
                var message = progressEvent.getMessage();
                ui.access(() -> {
                    removeProgressIndicator();
                    currentProgressIndicator = new Div();
                    currentProgressIndicator.addClassName("ar-chat-progress");
                    currentProgressIndicator.setText(message != null ? message : "Processing...");
                    messagesLayout.add(currentProgressIndicator);
                });
            }
        }

        private void removeProgressIndicator() {
            if (currentProgressIndicator != null) {
                messagesLayout.remove(currentProgressIndicator);
                currentProgressIndicator = null;
            }
        }
    }
}
