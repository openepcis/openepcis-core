<p align="center">
  <img src="https://openepcis.io/img/openepcis-logo.svg" alt="OpenEPCIS" width="30%">
</p>

[![License](https://img.shields.io/badge/License-Apache_2.0-blue.svg)](LICENSE)
[![Build](https://github.com/openepcis/openepcis-core/actions/workflows/maven.yml/badge.svg)](https://github.com/openepcis/openepcis-core/actions/workflows/maven.yml)

<h1 align="center">OpenEPCIS Core</h1>

The core of the [OpenEPCIS](https://openepcis.io) EPCIS 2.0 repository: capture, query and
subscriptions over OpenSearch and Kafka, document storage, validation and the user extension
schemas. Together with [openepcis-rest-quarkus](https://github.com/openepcis/openepcis-rest-quarkus)
it is the **Community Edition** of the platform, and
[epcis-repository-ce](https://github.com/openepcis/epcis-repository-ce) runs it as one container.

## Editions

The Community Edition is for trying OpenEPCIS out, not for production. It has no
authentication: every caller is an administrator and can read, write and delete everything. It
is single-tenant, has no document-level security, and OpenSearch runs without its security
plugin. Checks beyond EPCIS and the configured rules are not part of it either: possession,
commissioning register and rules, settings and meters per tenant, the webhook destination guard
and the access tiers of the extension schemas.

All of that, and authentication, multi-tenancy, document-level security, Keycloak, onboarding,
Azure Blob storage, the GS1 Germany service platform and the legacy Elasticsearch backend, is
the Business edition, which builds on these artifacts through the interfaces they define. The
reverse dependency never exists, and the Maven enforcer in this reactor refuses it.

## Modules

| Module | What it does |
|---|---|
| `openepcis-capture-common`, `-capture-service`, `-capture-kafka-core`, `-capture-kafka-streams` | The capture pipeline: context, validation and the rule engine, Kafka messages and serdes |
| `openepcis-query`, `-query-service`, `-query-subscription` | Query parameters, named queries, the subscription service |
| `openepcis-subscription-core`, `-subscription-streaming`, `-subscription-job-runnr` | Streaming subscriptions over the percolator, scheduled ones on JobRunr |
| `openepcis-repository-api`, `-repository-common`, `-repository-opensearch` | Repository interfaces, document models, the OpenSearch backend with its index templates |
| `openepcis-repository-etcd`, `-keyvalue-repository` | Page tokens: etcd for a cluster, Caffeine by default |
| `openepcis-storage-service`, `-s3-service` | Document storage: local by default, S3 optional |
| `openepcis-validation-service`, `-custom-extension-service`, `-event-rule-api` | Event validation, user extension schemas, the rule SPI |
| `openepcis-service-api`, `-service-common`, `-service-util`, `-opentelemetry-logging` | Shared interfaces, configuration and logging |

## Build

```bash
mvn -B clean verify
```

Java 25. Nothing resolves from anywhere but Maven Central and its snapshot repository.

## License

Apache License 2.0, see [LICENSE](LICENSE).
