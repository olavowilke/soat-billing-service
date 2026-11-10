package br.com.oficina.billing.orcamento.infrastructure;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

interface OrcamentoJpaRepository extends JpaRepository<OrcamentoData, UUID> {

    Optional<OrcamentoData> findByOsId(UUID osId);

    @Query("select o from OrcamentoData o join o.pagamento p where p.id = :pagamentoId")
    Optional<OrcamentoData> buscarPorPagamentoId(@Param("pagamentoId") UUID pagamentoId);

    @Query("""
            select o from OrcamentoData o join o.pagamento p
            where o.status = br.com.oficina.billing.orcamento.entities.StatusOrcamento.APROVADO
              and p.status = br.com.oficina.billing.orcamento.entities.StatusPagamento.PENDENTE
              and p.expiraEm <= :agora
            order by p.expiraEm asc
            """)
    List<OrcamentoData> buscarComPagamentoVencido(@Param("agora") Instant agora, Pageable limite);
}
