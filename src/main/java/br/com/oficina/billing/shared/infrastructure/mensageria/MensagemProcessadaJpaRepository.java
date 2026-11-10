package br.com.oficina.billing.shared.infrastructure.mensageria;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

public interface MensagemProcessadaJpaRepository extends JpaRepository<MensagemProcessadaData, UUID> {

    /**
     * Marca a mensagem como processada. Devolve 1 na primeira vez e 0 se ela ja existia.
     * Deve rodar na transacao que processa a mensagem: se ela desfaz, a marca some junto.
     */
    @Modifying
    @Transactional(propagation = Propagation.MANDATORY)
    @Query(value = "insert into mensagens_processadas (message_id, processada_em) values (:id, :agora) "
            + "on conflict (message_id) do nothing", nativeQuery = true)
    int registrar(@Param("id") UUID id, @Param("agora") Instant agora);
}
