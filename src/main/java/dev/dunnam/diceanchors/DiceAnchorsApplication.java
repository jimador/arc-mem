package dev.dunnam.diceanchors;

import com.embabel.dice.web.rest.DiceRestConfiguration;
import com.vaadin.flow.component.page.AppShellConfigurator;
import com.vaadin.flow.component.page.Push;
import com.vaadin.flow.shared.communication.PushMode;
import com.vaadin.flow.shared.ui.Transport;
import com.vaadin.flow.theme.Theme;
import org.drivine.autoconfigure.EnableDrivine;
import org.drivine.autoconfigure.EnableDrivinePropertiesConfig;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Import;

@SpringBootApplication(exclude = {
        com.quantpulsar.opentelemetry.langfuse.LangfuseExporterAutoConfiguration.class
})
@EnableDrivine
@EnableDrivinePropertiesConfig
@Import(DiceRestConfiguration.class)
@Push(value = PushMode.AUTOMATIC, transport = Transport.WEBSOCKET_XHR)
@Theme("anchor-retro")
public class DiceAnchorsApplication implements AppShellConfigurator {

    public static void main(String[] args) {
        SpringApplication.run(DiceAnchorsApplication.class, args);
    }
}
