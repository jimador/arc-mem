package dev.dunnam.diceanchors.chat;

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
import dev.dunnam.diceanchors.DiceAnchorsProperties;
import dev.dunnam.diceanchors.assembly.AnchorsLlmReference;
import dev.dunnam.diceanchors.assembly.PropositionsLlmReference;
import dev.dunnam.diceanchors.assembly.RelevanceScorer;
import dev.dunnam.diceanchors.assembly.TokenCounter;
import dev.dunnam.diceanchors.anchor.Anchor;
import dev.dunnam.diceanchors.anchor.AnchorEngine;
import dev.dunnam.diceanchors.anchor.Authority;
import dev.dunnam.diceanchors.anchor.AnchorMutationStrategy;
import dev.dunnam.diceanchors.anchor.CompliancePolicy;
import dev.dunnam.diceanchors.anchor.CompliancePolicyMode;
import dev.dunnam.diceanchors.anchor.MutationDecision;
import dev.dunnam.diceanchors.anchor.MutationRequest;
import dev.dunnam.diceanchors.anchor.MutationSource;
import dev.dunnam.diceanchors.anchor.event.ArchiveReason;
import dev.dunnam.diceanchors.persistence.AnchorRepository;
import dev.dunnam.diceanchors.persistence.PropositionNode;
import dev.dunnam.diceanchors.persistence.PropositionView;
import dev.dunnam.diceanchors.prompt.PromptTemplates;
import org.commonmark.parser.Parser;
import org.commonmark.renderer.html.HtmlRenderer;
import org.drivine.manager.CascadeType;
import org.drivine.manager.GraphObjectManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
 * Vaadin chat view for the dice-anchors demo.
 * <p>
 * Follows the Impromptu VaadinChatView pattern:
 * Thread + BlockingQueue + @Push + ui.access() for async LLM responses.
 * The Embabel {@link Chatbot} drives the agent process; {@link ChatActions}
 * assembles the anchor context on each turn.
 * <p>
 * Session state is stored in {@link VaadinSession} to survive navigation.
 */
@Route("chat")
@PageTitle("Dice Anchors — Bigby the DM")
public class ChatView extends VerticalLayout implements BeforeEnterObserver {

    private static final Logger logger = LoggerFactory.getLogger(ChatView.class);
    private static final int RESPONSE_TIMEOUT_SECONDS = 120;
    private static final int ASYNC_SIDEBAR_REFRESH_ATTEMPTS = 24;
    private static final long ASYNC_SIDEBAR_REFRESH_INTERVAL_MILLIS = 5_000L;
    private static final String SESSION_ATTR_CHAT = "diceAnchorsChatSession";
    private static final String SESSION_ATTR_CONVERSATION_ID = "diceAnchorsConversationId";

    private final Chatbot chatbot;
    private final AnchorEngine anchorEngine;
    private final AnchorRepository anchorRepository;
    private final GraphObjectManager graphObjectManager;
    private final DiceAnchorsProperties properties;
    private final CompliancePolicy compliancePolicy;
    private final TokenCounter tokenCounter;
    private final RelevanceScorer relevanceScorer;
    private final AnchorMutationStrategy mutationStrategy;
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

    private VerticalLayout anchorsTabContent;
    private VerticalLayout propositionsTabContent;
    private VerticalLayout knowledgeTabContent;
    private VerticalLayout sessionInfoTabContent;
    private VerticalLayout contextTabContent;
    private volatile Thread sidebarRefreshThread;
    private final Map<String, Div> anchorCards = new LinkedHashMap<>();
    private final Map<String, Anchor> renderedAnchors = new LinkedHashMap<>();
    private Span anchorsHeader;
    private Div createAnchorForm;
    private int turnCount = 0;
    private String activeConversationId;

    public ChatView(Chatbot chatbot, AnchorEngine anchorEngine, AnchorRepository anchorRepository,
                    GraphObjectManager graphObjectManager,
                    DiceAnchorsProperties properties,
                    CompliancePolicy compliancePolicy,
                    TokenCounter tokenCounter,
                    RelevanceScorer relevanceScorer,
                    AnchorMutationStrategy mutationStrategy,
                    ConversationService conversationService) {
        this.chatbot = chatbot;
        this.anchorEngine = anchorEngine;
        this.anchorRepository = anchorRepository;
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

        var benchmarkLink = new RouterLink("Benchmark", dev.dunnam.diceanchors.sim.views.BenchmarkView.class);
        benchmarkLink.addClassName("ar-nav-link");
        var simLink = new RouterLink("Simulator", dev.dunnam.diceanchors.sim.views.SimulationView.class);
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

        resumeIdField = new TextField();
        resumeIdField.setPlaceholder("Paste conversation ID...");
        resumeIdField.setWidth("280px");
        resumeIdField.setClearButtonVisible(true);

        var resumeButton = new Button("Resume", e -> resumeConversation());
        resumeButton.addThemeVariants(ButtonVariant.LUMO_SMALL, ButtonVariant.LUMO_SUCCESS);

        var bar = new HorizontalLayout(
                conversationIdField, copyButton, newButton, resumeIdField, resumeButton);
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

        anchorsTabContent = new VerticalLayout();
        anchorsTabContent.setPadding(true);
        anchorsTabContent.setSpacing(true);

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

        var anchorsScroller = new Scroller(anchorsTabContent);
        anchorsScroller.setSizeFull();
        anchorsScroller.setScrollDirection(Scroller.ScrollDirection.VERTICAL);

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
        tabSheet.add("Anchors", anchorsScroller);
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
        refreshAnchorsTab();
        refreshPropositionsTab();
        refreshKnowledgeTab();
        refreshSessionInfoTab();
        refreshContextTab();
    }

    private void refreshAnchorsTab() {
        try {
            var anchors = anchorEngine.inject(getContextId());
            var anchorsById = anchors.stream().collect(java.util.stream.Collectors.toMap(
                    Anchor::id, anchor -> anchor, (left, right) -> right, LinkedHashMap::new));

            anchorCards.entrySet().removeIf(entry -> {
                var stale = !anchorsById.containsKey(entry.getKey());
                if (stale) {
                    anchorsTabContent.remove(entry.getValue());
                    renderedAnchors.remove(entry.getKey());
                }
                return stale;
            });

            anchorsById.forEach((id, anchor) -> {
                var currentCard = anchorCards.get(id);
                if (currentCard == null) {
                    var newCard = anchorCard(anchor);
                    anchorCards.put(id, newCard);
                    if (createAnchorForm != null) {
                        anchorsTabContent.addComponentAtIndex(anchorsTabContent.indexOf(createAnchorForm), newCard);
                    } else {
                        anchorsTabContent.add(newCard);
                    }
                } else if (!Objects.equals(renderedAnchors.get(id), anchor)) {
                    updateAnchorCard(currentCard, anchor);
                }
                renderedAnchors.put(id, anchor);
            });

            if (anchorsHeader == null) {
                anchorsHeader = new Span();
                anchorsHeader.addClassName("ar-chat-section-header");
                anchorsHeader.addClassName("ar-chat-section-header--cyan");
                anchorsTabContent.addComponentAtIndex(0, anchorsHeader);
            }
            anchorsHeader.setText("Active Anchors (%d)".formatted(anchors.size()));
            anchorsHeader.setVisible(!anchors.isEmpty());

            if (createAnchorForm == null) {
                createAnchorForm = buildCreateAnchorForm();
                anchorsTabContent.add(createAnchorForm);
            }
        } catch (Exception e) {
            logger.warn("Failed to load anchors for sidebar: {}", e.getMessage());
        }
    }

    private void updateAnchorCard(Div card, Anchor anchor) {
        card.getChildren()
            .filter(component -> component instanceof Span
                    && component.getClassNames().contains("ar-chat-authority-badge"))
            .findFirst()
            .ifPresent(component -> {
                var badge = (Span) component;
                badge.setText(anchor.authority().name());
                badge.getElement().setAttribute("data-authority", anchor.authority().name().toLowerCase());
            });

        card.getChildren()
            .filter(component -> component instanceof Span
                    && component.getClassNames().contains("ar-chat-text"))
            .findFirst()
            .ifPresent(component -> ((Span) component).setText(truncateText(anchor.text(), 80)));

        card.getChildren()
            .filter(component -> component instanceof ProgressBar)
            .findFirst()
            .ifPresent(component -> ((ProgressBar) component).setValue(anchor.rank()));

        card.getChildren()
            .filter(component -> component instanceof Span
                    && component.getClassNames().contains("ar-chat-meta"))
            .findFirst()
            .ifPresent(component -> ((Span) component).setText(
                    "rank: %d | x%d".formatted(anchor.rank(), anchor.reinforcementCount())));

        var pinnedBadge = card.getChildren()
                              .filter(component -> component instanceof Span
                                      && component.getClassNames().contains("ar-pinned-icon"))
                              .findFirst()
                              .orElse(null);
        if (anchor.pinned() && pinnedBadge == null) {
            var badge = new Span("pinned");
            badge.addClassName("ar-pinned-icon");
            card.add(badge);
        }
        if (!anchor.pinned() && pinnedBadge != null) {
            card.remove(pinnedBadge);
        }
    }

    private void refreshPropositionsTab() {
        propositionsTabContent.removeAll();

        try {
            var propositions = anchorRepository.findByContextIdValue(getContextId());
            var anchorIds = anchorRepository.findActiveAnchors(getContextId()).stream()
                                            .map(n -> n.getId())
                                            .collect(java.util.stream.Collectors.toSet());
            var nonAnchors = propositions.stream()
                                         .filter(p -> !anchorIds.contains(p.getId()))
                                         .toList();

            if (!nonAnchors.isEmpty()) {
                var propsHeader = new Span("Propositions (%d)".formatted(nonAnchors.size()));
                propsHeader.addClassName("ar-chat-section-header");
                propsHeader.addClassName("ar-chat-section-header--amber");
                propositionsTabContent.add(propsHeader);

                for (var prop : nonAnchors) {
                    var card = new Div();
                    card.addClassName("ar-chat-card");

                    var text = new Span(truncateText(prop.getText(), 100));
                    text.addClassName("ar-chat-text");

                    var statusName = prop.getStatus() != null ? prop.getStatus().name() : "UNKNOWN";
                    var meta = new Span("conf: %.0f%% | %s".formatted(
                            prop.getConfidence() * 100, statusName));
                    meta.addClassName("ar-chat-meta");

                    var promoteButton = new Button("Promote", e -> {
                        anchorEngine.promote(prop.getId(), 500);
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
            var propositions = anchorRepository.findByContextIdValue(getContextId());
            var anchorIds = anchorRepository.findActiveAnchors(getContextId()).stream()
                                            .map(n -> n.getId())
                                            .collect(java.util.stream.Collectors.toSet());
            var knowledge = propositions.stream()
                                        .filter(p -> !anchorIds.contains(p.getId()))
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

                    var promoteButton = new Button("Promote to Anchor", e -> {
                        anchorEngine.promote(prop.getId(), 500);
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

            var node = new PropositionNode(knowledgeText.trim(), confidence);
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
            int anchorCount = anchorRepository.countActiveAnchors(getContextId());
            sessionInfoTabContent.add(infoRow("Active Anchors", String.valueOf(anchorCount)));
        } catch (Exception e) {
            sessionInfoTabContent.add(infoRow("Active Anchors", "N/A"));
        }

        try {
            var propositions = anchorRepository.findByContextIdValue(getContextId());
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


    private Div anchorCard(Anchor anchor) {
        var card = new Div();
        card.addClassName("ar-chat-anchor-card");

        var authorityBadge = new Span(anchor.authority().name());
        authorityBadge.addClassName("ar-chat-authority-badge");
        authorityBadge.getElement().setAttribute("data-authority", anchor.authority().name().toLowerCase());

        var text = new Span(truncateText(anchor.text(), 80));
        text.addClassName("ar-chat-text");

        var rankBar = new ProgressBar(0, 900, anchor.rank());
        rankBar.setWidthFull();
        rankBar.addClassName("ar-chat-rank-bar");

        var meta = new Span("rank: %d | x%d".formatted(anchor.rank(), anchor.reinforcementCount()));
        meta.addClassName("ar-chat-meta");

        card.add(authorityBadge, text, rankBar, meta);

        if (anchor.pinned()) {
            var pinnedBadge = new Span("pinned");
            pinnedBadge.addClassName("ar-pinned-icon");
            card.add(pinnedBadge);
        }

        // Inline rank slider
        var rankField = new IntegerField("Rank");
        rankField.setMin(100);
        rankField.setMax(900);
        rankField.setStep(50);
        rankField.setValue(anchor.rank());
        rankField.setWidthFull();
        rankField.setStepButtonsVisible(true);
        rankField.addClassName("ar-chat-form-field");
        rankField.addValueChangeListener(e -> {
            if (e.getValue() != null) {
                anchorRepository.updateRank(anchor.id(), e.getValue());
                refreshSidebar();
            }
        });

        // Authority dropdown (current level and higher, no CANON)
        var authorityCombo = new ComboBox<String>("Authority");
        authorityCombo.setWidthFull();
        authorityCombo.addClassName("ar-chat-form-field");
        var authorityOptions = new ArrayList<String>();
        int currentLevel = anchor.authority().level();
        for (var auth : Authority.values()) {
            if (auth != Authority.CANON && auth.level() >= currentLevel) {
                authorityOptions.add(auth.name());
            }
        }
        authorityCombo.setItems(authorityOptions);
        authorityCombo.setValue(anchor.authority().name());
        authorityCombo.addValueChangeListener(e -> {
            var latest = renderedAnchors.get(anchor.id());
            var currentAuthority = latest != null ? latest.authority().name() : anchor.authority().name();
            if (e.getValue() != null && !e.getValue().equals(currentAuthority)) {
                anchorRepository.setAuthority(anchor.id(), e.getValue());
                refreshSidebar();
            }
        });

        // Evict button
        var evictButton = new Button("Evict");
        evictButton.addThemeVariants(ButtonVariant.LUMO_SMALL, ButtonVariant.LUMO_ERROR, ButtonVariant.LUMO_TERTIARY);
        evictButton.addClickListener(e -> {
            anchorRepository.archiveAnchor(anchor.id());
            refreshSidebar();
        });

        var reviseField = new TextField("Revision");
        reviseField.setWidthFull();
        reviseField.setValue(anchor.text());
        reviseField.addClassName("ar-chat-form-field");

        var reviseButton = new Button("Revise");
        reviseButton.addThemeVariants(ButtonVariant.LUMO_SMALL, ButtonVariant.LUMO_PRIMARY, ButtonVariant.LUMO_TERTIARY);
        reviseButton.addClickListener(e -> {
            var revisedText = reviseField.getValue();
            if (revisedText == null) {
                return;
            }
            var latest = renderedAnchors.get(anchor.id());
            var currentText = latest != null ? latest.text() : anchor.text();
            var normalized = revisedText.trim();
            if (normalized.isEmpty() || normalized.equals(currentText)) {
                return;
            }
            var target = latest != null ? latest : anchor;
            var revised = reviseAnchor(target, normalized);
            if (!revised) {
                logger.warn("Anchor revision did not archive predecessor {}", target.id());
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

    private boolean reviseAnchor(Anchor anchor, String revisedText) {
        var request = new MutationRequest(anchor.id(), revisedText, MutationSource.UI, "chat-operator");
        var decision = mutationStrategy.evaluate(request);
        return switch (decision) {
            case MutationDecision.Allow _ -> executeRevision(anchor, revisedText);
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

    private boolean executeRevision(Anchor anchor, String revisedText) {
        var node = new PropositionNode(revisedText, 0.95);
        node.setContextId(getContextId());
        graphObjectManager.save(new PropositionView(node, List.of()), CascadeType.NONE);
        anchorRepository.promoteToAnchor(node.getId(), anchor.rank(), anchor.authority().name());
        if (anchor.pinned()) {
            anchorRepository.updatePinned(node.getId(), true);
        }
        anchorEngine.supersede(anchor.id(), node.getId(), ArchiveReason.REVISION);
        return anchorRepository.findPropositionNodeById(anchor.id())
                .map(predecessor -> predecessor.getRank() <= 0
                        || predecessor.getStatus() == PropositionStatus.SUPERSEDED)
                .orElse(true);
    }

    private void refreshContextTab() {
        contextTabContent.removeAll();
        try {
            var anchorRef = new AnchorsLlmReference(
                    anchorEngine,
                    getContextId(),
                    properties.anchor().budget(),
                    compliancePolicy,
                    properties.assembly().promptTokenBudget(),
                    tokenCounter,
                    null,
                    properties.retrieval(),
                    relevanceScorer);
            var propositionRef = new PropositionsLlmReference(
                    anchorRepository,
                    getContextId(),
                    properties.anchor().budget());

            var anchors = anchorRef.getAnchors();
            var anchorsBlock = anchorRef.getContent();
            var propositionBlock = propositionRef.getContent();
            var prompt = renderChatPrompt(anchors, propositionBlock);

            contextTabContent.add(infoRow("Anchors in Prompt", String.valueOf(anchors.size())));
            contextTabContent.add(readOnlyBlock("Anchors Block", anchorsBlock));
            contextTabContent.add(readOnlyBlock("Propositions Block", propositionBlock));
            contextTabContent.add(readOnlyBlock("Rendered System Prompt", prompt));
        } catch (Exception e) {
            logger.warn("Failed to build context preview: {}", e.getMessage());
            var placeholder = new Paragraph("Failed to render context preview.");
            placeholder.addClassName("ar-empty-message");
            contextTabContent.add(placeholder);
        }
    }

    private String renderChatPrompt(List<Anchor> anchors, String propositionBlock) {
        var anchorMaps = anchors.stream()
                .map(a -> java.util.Map.<String, Object>of(
                        "text", a.text(),
                        "rank", a.rank(),
                        "authority", a.authority().name()))
                .toList();

        var tiered = properties.anchor().compliancePolicy() == CompliancePolicyMode.TIERED;
        var templateVars = new java.util.HashMap<String, Object>();
        templateVars.put("properties", properties);
        templateVars.put("anchors", anchorMaps);
        templateVars.put("proposition_block", propositionBlock);
        templateVars.put("persona", properties.chat().persona());
        templateVars.put("tiered", tiered);

        if (tiered) {
            var grouped = anchors.stream()
                    .collect(java.util.stream.Collectors.groupingBy(Anchor::authority));
            for (var authority : Authority.values()) {
                var key = authority.name().toLowerCase() + "_anchors";
                var group = grouped.getOrDefault(authority, List.of()).stream()
                        .map(a -> java.util.Map.<String, Object>of("text", a.text(), "rank", a.rank()))
                        .toList();
                templateVars.put(key, group);
            }
        }

        return PromptTemplates.render("prompts/dice-anchors.jinja", templateVars);
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


    private Div buildCreateAnchorForm() {
        var form = new Div();
        form.addClassName("ar-chat-form");

        var formTitle = new Span("Create Anchor");
        formTitle.addClassName("ar-chat-form-title");

        var anchorTextField = new TextField("Anchor Text");
        anchorTextField.setWidthFull();
        anchorTextField.setPlaceholder("Enter anchor text...");

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
            var anchorText = anchorTextField.getValue();
            if (anchorText == null || anchorText.trim().isEmpty()) {
                return;
            }

            var rank = rankField.getValue() != null ? rankField.getValue() : 500;
            var authority = authorityCombo.getValue() != null ? authorityCombo.getValue() : "PROVISIONAL";

            var node = new PropositionNode(anchorText.trim(), 0.95);
            node.setContextId(getContextId());
            graphObjectManager.save(new PropositionView(node, List.of()), CascadeType.NONE);
            anchorRepository.promoteToAnchor(node.getId(), rank, authority);

            anchorTextField.clear();
            rankField.setValue(500);
            authorityCombo.setValue("PROVISIONAL");

            refreshSidebar();
        });

        var layout = new VerticalLayout(formTitle, anchorTextField, rankField, authorityCombo, createButton);
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
        }, "dice-anchors-chat-" + UUID.randomUUID().toString().substring(0, 8)).start();
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
        anchorCards.clear();
        renderedAnchors.clear();
        anchorsHeader = null;
        createAnchorForm = null;
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

        cancelAsyncSidebarRefresh();
        clearSession();

        activeConversationId = resumeId;
        updateConversationIdDisplay();
        storeConversationIdInSession();
        resumeIdField.clear();

        messagesLayout.removeAll();
        anchorCards.clear();
        renderedAnchors.clear();
        anchorsHeader = null;
        createAnchorForm = null;
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
        logger.info("Resumed conversation {} with {} messages", activeConversationId, messages.size());
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
        }, "dice-anchors-sidebar-refresh-" + UUID.randomUUID().toString().substring(0, 8));
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
