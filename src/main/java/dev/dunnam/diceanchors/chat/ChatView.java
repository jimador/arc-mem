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
import com.vaadin.flow.component.AttachEvent;
import com.vaadin.flow.component.Key;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.html.Div;
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
import com.vaadin.flow.component.textfield.IntegerField;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.router.RouterLink;
import com.vaadin.flow.server.VaadinSession;
import dev.dunnam.diceanchors.anchor.Anchor;
import dev.dunnam.diceanchors.anchor.AnchorEngine;
import dev.dunnam.diceanchors.anchor.Authority;
import dev.dunnam.diceanchors.persistence.AnchorRepository;
import dev.dunnam.diceanchors.persistence.PropositionNode;
import dev.dunnam.diceanchors.persistence.PropositionView;
import org.commonmark.parser.Parser;
import org.commonmark.renderer.html.HtmlRenderer;
import org.drivine.manager.CascadeType;
import org.drivine.manager.GraphObjectManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
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
public class ChatView extends VerticalLayout {

    private static final Logger logger = LoggerFactory.getLogger(ChatView.class);
    private static final int RESPONSE_TIMEOUT_SECONDS = 120;
    private static final String DEFAULT_CONTEXT = "chat";

    private final Chatbot chatbot;
    private final AnchorEngine anchorEngine;
    private final AnchorRepository anchorRepository;
    private final GraphObjectManager graphObjectManager;
    private final Parser markdownParser = Parser.builder().build();
    private final HtmlRenderer htmlRenderer = HtmlRenderer.builder().build();

    private VerticalLayout messagesLayout;
    private Scroller messagesScroller;
    private TextField inputField;
    private Button sendButton;
    private Div thinkingIndicator;

    private VerticalLayout anchorsTabContent;
    private VerticalLayout propositionsTabContent;
    private VerticalLayout knowledgeTabContent;
    private VerticalLayout sessionInfoTabContent;
    private int turnCount = 0;

    public ChatView(Chatbot chatbot, AnchorEngine anchorEngine, AnchorRepository anchorRepository,
                    GraphObjectManager graphObjectManager) {
        this.chatbot = chatbot;
        this.anchorEngine = anchorEngine;
        this.anchorRepository = anchorRepository;
        this.graphObjectManager = graphObjectManager;
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

    // ========================================================================
    // UI Construction
    // ========================================================================

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

        add(headerRow, splitLayout);
        setFlexGrow(1, splitLayout);
        setPadding(false);
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

        var tabSheet = new TabSheet();
        tabSheet.setSizeFull();
        tabSheet.add("Anchors", anchorsScroller);
        tabSheet.add("Propositions", propositionsScroller);
        tabSheet.add("Knowledge", knowledgeScroller);
        tabSheet.add("Session Info", sessionInfoScroller);

        sidebar.add(tabSheet);
        sidebar.setFlexGrow(1, tabSheet);

        refreshSidebar();

        return sidebar;
    }

    // ========================================================================
    // Sidebar refresh
    // ========================================================================

    /**
     * Refresh all three sidebar tabs with current data from the repository.
     */
    private void refreshSidebar() {
        refreshAnchorsTab();
        refreshPropositionsTab();
        refreshKnowledgeTab();
        refreshSessionInfoTab();
    }

    private void refreshAnchorsTab() {
        anchorsTabContent.removeAll();

        // Active anchors list
        try {
            var anchors = anchorEngine.inject(DEFAULT_CONTEXT);
            if (!anchors.isEmpty()) {
                var anchorsHeader = new Span("Active Anchors (%d)".formatted(anchors.size()));
                anchorsHeader.addClassName("ar-chat-section-header");
                anchorsHeader.addClassName("ar-chat-section-header--cyan");
                anchorsTabContent.add(anchorsHeader);

                for (var anchor : anchors) {
                    anchorsTabContent.add(anchorCard(anchor));
                }
            } else {
                var placeholder = new Paragraph("No active anchors yet. Create one below or chat to generate propositions.");
                placeholder.addClassName("ar-empty-message");
                anchorsTabContent.add(placeholder);
            }
        } catch (Exception e) {
            logger.warn("Failed to load anchors for sidebar: {}", e.getMessage());
        }

        // Create Anchor form
        anchorsTabContent.add(buildCreateAnchorForm());
    }

    private void refreshPropositionsTab() {
        propositionsTabContent.removeAll();

        try {
            var propositions = anchorRepository.findByContextIdValue(DEFAULT_CONTEXT);
            var anchorIds = anchorRepository.findActiveAnchors(DEFAULT_CONTEXT).stream()
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

                    var meta = new Span("conf: %.0f%% | %s".formatted(
                            prop.getConfidence() * 100, prop.getStatus().name()));
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
        }
    }

    private void refreshKnowledgeTab() {
        knowledgeTabContent.removeAll();

        try {
            var propositions = anchorRepository.findByContextIdValue(DEFAULT_CONTEXT);
            var anchorIds = anchorRepository.findActiveAnchors(DEFAULT_CONTEXT).stream()
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
            node.setContextId(DEFAULT_CONTEXT);
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

        sessionInfoTabContent.add(infoRow("Context ID", DEFAULT_CONTEXT));

        try {
            int anchorCount = anchorRepository.countActiveAnchors(DEFAULT_CONTEXT);
            sessionInfoTabContent.add(infoRow("Active Anchors", String.valueOf(anchorCount)));
        } catch (Exception e) {
            sessionInfoTabContent.add(infoRow("Active Anchors", "N/A"));
        }

        try {
            var propositions = anchorRepository.findByContextIdValue(DEFAULT_CONTEXT);
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

    // ========================================================================
    // Anchor card with management controls
    // ========================================================================

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
            if (e.getValue() != null && !e.getValue().equals(anchor.authority().name())) {
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

        var controlsRow = new HorizontalLayout(authorityCombo, evictButton);
        controlsRow.setWidthFull();
        controlsRow.setAlignItems(Alignment.END);
        controlsRow.setFlexGrow(1, authorityCombo);

        card.add(rankField, controlsRow);

        return card;
    }

    // ========================================================================
    // Create Anchor form
    // ========================================================================

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
            node.setContextId(DEFAULT_CONTEXT);
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

    // ========================================================================
    // Message handling
    // ========================================================================

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

        new Thread(() -> {
            try {
                sessionData.chatSession().onUserMessage(new UserMessage(text));

                var response = sessionData.responseQueue()
                                          .poll(RESPONSE_TIMEOUT_SECONDS, TimeUnit.SECONDS);

                ui.access(() -> {
                    removeThinkingIndicator();
                    if (response != null) {
                        addBotBubble(response.getContent());
                        turnCount++;
                        refreshSidebar();
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

    // ========================================================================
    // Session management (stored in VaadinSession to survive navigation)
    // ========================================================================

    private record SessionData(ChatSession chatSession, BlockingQueue<Message> responseQueue) {}

    private SessionData getOrCreateSession(UI ui) {
        var vaadinSession = VaadinSession.getCurrent();
        var existing = (SessionData) vaadinSession.getAttribute("diceAnchorsChatSession");
        if (existing != null) {
            return existing;
        }

        var responseQueue = new ArrayBlockingQueue<Message>(10);
        var outputChannel = new VaadinOutputChannel(responseQueue, ui, messagesLayout);

        // Simple anonymous user — dice-anchors has no user model
        var sessionId = UUID.randomUUID().toString();
        var user = new AnonymousDmUser(sessionId);

        var chatSession = chatbot.createSession(user, outputChannel, sessionId, "Dice Anchors Chat");
        var sessionData = new SessionData(chatSession, responseQueue);
        vaadinSession.setAttribute("diceAnchorsChatSession", sessionData);

        logger.info("Created dice-anchors chat session {}", sessionId);
        return sessionData;
    }

    private void restoreConversation() {
        var vaadinSession = VaadinSession.getCurrent();
        if (vaadinSession == null) {
            return;
        }

        var sessionData = (SessionData) vaadinSession.getAttribute("diceAnchorsChatSession");
        if (sessionData == null) {
            return;
        }

        var conversation = sessionData.chatSession().getConversation();
        for (var msg : conversation.getMessages()) {
            if (msg instanceof UserMessage) {
                addUserBubble(msg.getContent());
            } else if (msg instanceof AssistantMessage) {
                addBotBubble(msg.getContent());
            }
        }
        if (!conversation.getMessages().isEmpty()) {
            scrollToBottom();
        }
    }

    // ========================================================================
    // Bubble helpers
    // ========================================================================

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

    private void scrollToBottom() {
        messagesScroller.getElement().executeJs("this.scrollTop = this.scrollHeight");
    }

    // ========================================================================
    // Inner types
    // ========================================================================

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
