package br.com.oficina.billing.shared.infrastructure.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/** Liga o agendador usado pelo relay da outbox. */
@Configuration
@EnableScheduling
public class AgendamentoConfig {
}
