package br.com.oficina.billing.orcamento.infrastructure;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

interface EstornoAvulsoJpaRepository extends JpaRepository<EstornoAvulsoData, UUID> {
}
