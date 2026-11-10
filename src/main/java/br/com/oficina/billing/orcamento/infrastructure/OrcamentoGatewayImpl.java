package br.com.oficina.billing.orcamento.infrastructure;

import br.com.oficina.billing.orcamento.entities.Orcamento;
import br.com.oficina.billing.orcamento.gateways.OrcamentoGateway;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Component
class OrcamentoGatewayImpl implements OrcamentoGateway {

    private final OrcamentoJpaRepository repository;
    private final OrcamentoMapper mapper;

    OrcamentoGatewayImpl(OrcamentoJpaRepository repository, OrcamentoMapper mapper) {
        this.repository = repository;
        this.mapper = mapper;
    }

    @Override
    @Transactional
    public Orcamento salvar(Orcamento orcamento) {
        boolean novo = !repository.existsById(orcamento.getId());
        var salvo = repository.saveAndFlush(mapper.paraData(orcamento, novo));
        return mapper.paraDominio(salvo);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Orcamento> buscarPorId(UUID id) {
        return repository.findById(id).map(mapper::paraDominio);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Orcamento> buscarPorOsId(UUID osId) {
        return repository.findByOsId(osId).map(mapper::paraDominio);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Orcamento> buscarPorPagamentoId(UUID pagamentoId) {
        return repository.buscarPorPagamentoId(pagamentoId).map(mapper::paraDominio);
    }

    @Override
    @Transactional(readOnly = true)
    public List<Orcamento> buscarComPagamentoVencido(Instant agora, int limite) {
        return repository.buscarComPagamentoVencido(agora, PageRequest.of(0, limite)).stream()
                .map(mapper::paraDominio).toList();
    }
}
