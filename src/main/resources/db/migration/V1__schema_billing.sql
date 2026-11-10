CREATE TABLE orcamentos (
    id            UUID           PRIMARY KEY,
    os_id         UUID           NOT NULL UNIQUE,
    saga_id       UUID           NOT NULL,
    cliente_id    UUID           NOT NULL,
    status        VARCHAR(20)    NOT NULL,
    valor_total   NUMERIC(12, 2) NOT NULL,
    versao        BIGINT         NOT NULL,
    criado_em     TIMESTAMPTZ    NOT NULL,
    atualizado_em TIMESTAMPTZ    NOT NULL
);

CREATE TABLE itens_orcamento (
    id             UUID           PRIMARY KEY,
    orcamento_id   UUID           NOT NULL REFERENCES orcamentos (id),
    tipo           VARCHAR(10)    NOT NULL,
    referencia_id  UUID           NOT NULL,
    descricao      VARCHAR(200)   NOT NULL,
    quantidade     INT            NOT NULL CHECK (quantidade > 0),
    valor_unitario NUMERIC(12, 2) NOT NULL CHECK (valor_unitario >= 0)
);

CREATE INDEX idx_itens_orcamento_orcamento ON itens_orcamento (orcamento_id);

CREATE TABLE pagamentos (
    id                UUID           PRIMARY KEY,
    orcamento_id      UUID           NOT NULL UNIQUE REFERENCES orcamentos (id),
    status            VARCHAR(20)    NOT NULL,
    valor             NUMERIC(12, 2) NOT NULL,
    mp_preference_id  VARCHAR(100),
    mp_payment_id     VARCHAR(100),
    link_pagamento    VARCHAR(500),
    expira_em         TIMESTAMPTZ    NOT NULL,
    tentativas_estorno INT           NOT NULL DEFAULT 0,
    criado_em         TIMESTAMPTZ    NOT NULL,
    atualizado_em     TIMESTAMPTZ    NOT NULL
);

CREATE INDEX idx_pagamentos_pendentes ON pagamentos (expira_em) WHERE status = 'PENDENTE';

CREATE TABLE outbox (
    id             UUID         PRIMARY KEY,
    tipo           VARCHAR(50)  NOT NULL,
    saga_id        UUID         NOT NULL,
    os_id          UUID         NOT NULL,
    correlation_id VARCHAR(100) NOT NULL,
    payload        JSONB        NOT NULL,
    criado_em      TIMESTAMPTZ  NOT NULL,
    enviado_em     TIMESTAMPTZ,
    tentativas     INT          NOT NULL DEFAULT 0,
    proxima_tentativa_em TIMESTAMPTZ
);

CREATE INDEX idx_outbox_pendentes ON outbox (criado_em) WHERE enviado_em IS NULL;

CREATE TABLE mensagens_processadas (
    message_id    UUID        PRIMARY KEY,
    processada_em TIMESTAMPTZ NOT NULL
);
