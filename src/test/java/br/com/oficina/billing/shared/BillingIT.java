package br.com.oficina.billing.shared;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.localstack.LocalStackContainer;
import org.testcontainers.utility.DockerImageName;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.sqs.SqsAsyncClient;
import software.amazon.awssdk.services.sqs.model.QueueAttributeName;

import java.util.Map;

/**
 * Base dos testes de integracao: Postgres e LocalStack (SQS) compartilhados na JVM.
 * Cria a fila de comandos (com DLQ, maxReceiveCount 3) e a fila de respostas.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
public abstract class BillingIT {

    protected static final String FILA_COMANDOS = "oficina-test-billing-commands";
    protected static final String FILA_RESPOSTAS = "oficina-test-os-saga-replies";

    static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("billing_test")
            .withUsername("billing")
            .withPassword("billing");

    static final LocalStackContainer localstack =
            new LocalStackContainer(DockerImageName.parse("localstack/localstack:3.8"))
                    .withServices(LocalStackContainer.Service.SQS);

    static {
        postgres.start();
        localstack.start();
        criarFilas();
    }

    @DynamicPropertySource
    static void configurar(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.cloud.aws.endpoint", () -> localstack.getEndpoint().toString());
        registry.add("spring.cloud.aws.region.static", localstack::getRegion);
        registry.add("spring.cloud.aws.credentials.access-key", localstack::getAccessKey);
        registry.add("spring.cloud.aws.credentials.secret-key", localstack::getSecretKey);
        registry.add("billing.filas.comandos", () -> FILA_COMANDOS);
        registry.add("billing.filas.respostas", () -> FILA_RESPOSTAS);
    }

    private static void criarFilas() {
        try (SqsAsyncClient sqs = SqsAsyncClient.builder()
                .endpointOverride(localstack.getEndpoint())
                .region(Region.of(localstack.getRegion()))
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create(localstack.getAccessKey(), localstack.getSecretKey())))
                .build()) {
            String dlqUrl = sqs.createQueue(b -> b.queueName(FILA_COMANDOS + "-dlq")).join().queueUrl();
            String dlqArn = sqs.getQueueAttributes(b -> b.queueUrl(dlqUrl)
                    .attributeNames(QueueAttributeName.QUEUE_ARN)).join()
                    .attributes().get(QueueAttributeName.QUEUE_ARN);
            String redrive = "{\"deadLetterTargetArn\":\"" + dlqArn + "\",\"maxReceiveCount\":\"3\"}";
            sqs.createQueue(b -> b.queueName(FILA_COMANDOS).attributes(Map.of(
                    QueueAttributeName.VISIBILITY_TIMEOUT, "2",
                    QueueAttributeName.REDRIVE_POLICY, redrive))).join();
            sqs.createQueue(b -> b.queueName(FILA_RESPOSTAS)).join();
        }
    }
}
