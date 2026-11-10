package br.com.oficina.billing.shared.infrastructure.mensageria;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface OutboxJpaRepository extends JpaRepository<OutboxData, UUID> {

    /**
     * Linhas prontas para enviar, das mais antigas para as mais novas.
     * SKIP LOCKED deixa duas replicas trabalharem juntas.
     * A linha so entra se nenhuma linha mais antiga da mesma saga continua sem envio (inclusive as que esperam
     * o backoff ou estao travadas por outra replica). Isso mantem a ordem por saga e libera as outras sagas.
     * Por isso cada rodada envia no maximo uma linha por saga.
     */
    @Query(value = """
            select * from outbox o
            where o.enviado_em is null
              and (o.proxima_tentativa_em is null or o.proxima_tentativa_em <= now())
              and not exists (
                  select 1 from outbox o2
                  where o2.saga_id = o.saga_id and o2.enviado_em is null
                    and (o2.criado_em, o2.id) < (o.criado_em, o.id))
            order by o.criado_em, o.id
            limit :limite
            for update of o skip locked
            """, nativeQuery = true)
    List<OutboxData> travarPendentes(@Param("limite") int limite);
}
